package com.devicefarm.agent.capture

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import com.devicefarm.agent.net.AgentSocket
import com.devicefarm.agent.net.Frame
import com.devicefarm.agent.net.FrameType
import android.content.Intent
import java.nio.ByteBuffer

/**
 * Owns the {@link MediaProjection} session + {@link MediaCodec} H.264 encoder.
 * Pushes encoded NAL units into the supplied {@link AgentSocket}.
 *
 * The encoder emits its first OUTPUT_FORMAT_CHANGED with the SPS+PPS in the format. We cache
 * that and prepend it to every keyframe so a fresh web subscriber can prime its decoder.
 */
class ScreenCaptureEngine(
    private val context: Context,
    private val socket: AgentSocket,
) {

    data class Config(
        val width: Int,
        val height: Int,
        val realWidth: Int,
        val realHeight: Int,
        val densityDpi: Int = DisplayMetrics.DENSITY_DEFAULT,
        /**
         * Target frame rate. 30fps is the sweet spot for screen mirroring:
         *   - encoder + on-device GPU keep up reliably (60fps caused drops on
         *     mid-range phones, which surfaced as stutter + lower quality);
         *   - halves the bit budget so each frame gets ~2× more bits, sharper
         *     output;
         *   - aligns with typical Android UI animation tick (~16ms frame).
         */
        val fps: Int = 30,
        /**
         * Bitrate budget. 8 Mbps at 720p@30 ≈ 0.30 bits/pixel — comfortably in
         * the "broadcast quality" band. Was 4 Mbps which was visibly soft on
         * text-heavy screens. Bump again only if the link saturates.
         */
        val bitrate: Int = 8_000_000,
        /** H.264 level. AVCLevel4 supports 60fps at our resolutions; bump if you push higher. */
        val avcLevel: Int = MediaCodecInfo.CodecProfileLevel.AVCLevel4,
        /**
         * If > 0, encoder will resend the last frame after this many microseconds of input
         * idleness. Pro: smoother perceived framerate when the device is idle.
         * Con: wastes bandwidth re-encoding identical frames. Default disabled — for screen
         * mirroring we'd rather the canvas just hold the last frame.
         */
        val repeatPreviousFrameAfterUs: Long = 0L,
    )

    /** Whether the codec + virtual display are currently capturing. */
    val isRunning: Boolean get() = running

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var codec: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var spsPps: ByteArray? = null
    private var running = false
    private var cfg: Config = Config(720, 1280, 720, 1280)

    fun start(resultCode: Int, resultData: Intent, config: Config = defaultConfig(context)) {
        if (running) { Log.w(TAG, "already running"); return }
        cfg = config

        val pm = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = pm.getMediaProjection(resultCode, resultData) ?: run {
            Log.e(TAG, "no MediaProjection"); return
        }
        projection!!.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stop() }
        }, null)

        val format = MediaFormat.createVideoFormat("video/avc", cfg.width, cfg.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, cfg.bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, cfg.fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
            setInteger(MediaFormat.KEY_LEVEL, cfg.avcLevel)
            // VBR lets the encoder allocate more bits to complex frames (lots of
            // motion, fine text) and fewer to static screens — visibly sharper for
            // the same average bandwidth. Falls back to CBR on encoders that don't
            // declare VBR support.
            val bitrateMode = if (encoderSupportsVbr()) {
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
            } else {
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
            }
            setInteger(MediaFormat.KEY_BITRATE_MODE, bitrateMode)

            // Low-latency hints
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) setInteger(MediaFormat.KEY_LATENCY, 0)
            // Realtime priority — tells the codec scheduler not to throttle.
            setInteger(MediaFormat.KEY_PRIORITY, 0)
            // NOTE: KEY_OPERATING_RATE was previously set to Short.MAX_VALUE; some encoders
            // (e.g. Samsung Exynos H.264) interpret that as "encode at 32767 fps" and fail with
            // NO_MEMORY at start(). Setting it to the actual target fps is safer.
            setInteger(MediaFormat.KEY_OPERATING_RATE, cfg.fps)

            if (cfg.repeatPreviousFrameAfterUs > 0) {
                setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, cfg.repeatPreviousFrameAfterUs)
            }
        }

        codec = MediaCodec.createEncoderByType("video/avc").apply {
            setCallback(encoderCallback)
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = createInputSurface()
            start()
        }

        virtualDisplay = projection!!.createVirtualDisplay(
            "dp-agent-screen",
            cfg.width, cfg.height, cfg.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            inputSurface, null, null,
        )

        running = true
        announceMetadata()
        Log.i(TAG, "capture started stream=${cfg.width}x${cfg.height} real=${cfg.realWidth}x${cfg.realHeight} @ ${cfg.fps}fps")
    }

    fun stop() {
        running = false
        runCatching { virtualDisplay?.release() }; virtualDisplay = null
        runCatching { codec?.stop(); codec?.release() }; codec = null
        runCatching { inputSurface?.release() }; inputSurface = null
        runCatching { projection?.stop() }; projection = null
        spsPps = null
        Log.i(TAG, "capture stopped")
    }

    fun requestKeyframe() {
        codec?.setParameters(Bundle().apply {
            putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
        })
    }

    /**
     * Re-prime a freshly-connected bridge channel. After a bridge restart the new DeviceChannel
     * has no cached STREAM_METADATA or keyframe in its primer, so a web subscriber's decoder is
     * never configured (no `decoder.configure(...)` call without metadata) and the video stays
     * black even though the agent is happily sending DELTAs. We re-announce the stream descriptor
     * and force the encoder to emit a new I-frame so the next subscription can decode from scratch.
     *
     * Cheap and idempotent: metadata is ~150 bytes; requestKeyframe just sets a codec parameter.
     * No-op if capture isn't running (consent not granted yet).
     */
    fun reprimeForBridge() {
        if (!running) return
        announceMetadata()
        requestKeyframe()
    }

    private fun announceMetadata() {
        // realWidth/realHeight = actual physical screen pixels, used by the web client for
        // tap coordinates. Stream dims preserve the same aspect ratio as the real screen so
        // canvas display and TouchOverlay map 1:1 without letterboxing.
        val payload = """{"type":"streamMetadata","codec":"h264","width":${cfg.width},"height":${cfg.height},"fps":${cfg.fps},"realWidth":${cfg.realWidth},"realHeight":${cfg.realHeight}}"""
        socket.send(Frame.ofJson(FrameType.STREAM_METADATA, payload))
    }

    private val encoderCallback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) { /* surface input */ }

        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            try {
                val buf: ByteBuffer = codec.getOutputBuffer(index) ?: return
                buf.position(info.offset)
                buf.limit(info.offset + info.size)
                val data = ByteArray(info.size)
                buf.get(data)

                val isConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                val isKey    = (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME)    != 0

                if (isConfig) {
                    spsPps = data
                } else if (isKey) {
                    val sps = spsPps
                    val payload = if (sps != null) sps + data else data
                    socket.send(Frame(FrameType.VIDEO_KEYFRAME, payload))
                } else {
                    socket.send(Frame(FrameType.VIDEO_DELTA, data))
                }
            } finally {
                runCatching { codec.releaseOutputBuffer(index, false) }
            }
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            Log.e(TAG, "encoder error", e); stop()
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            val sps = format.getByteBuffer("csd-0")?.toBytes()
            val pps = format.getByteBuffer("csd-1")?.toBytes()
            if (sps != null && pps != null) spsPps = sps + pps
        }
    }

    private fun ByteBuffer.toBytes(): ByteArray {
        val out = ByteArray(remaining())
        duplicate().get(out)
        return out
    }

    /**
     * Probes whether the default H.264 encoder advertises VBR support. The check
     * has to happen against an encoder that's already created — we use a throwaway
     * lookup before configure() to avoid a second `createEncoderByType` allocation.
     */
    private fun encoderSupportsVbr(): Boolean {
        return try {
            val list = android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS)
            list.codecInfos.asSequence()
                .filter { it.isEncoder }
                .flatMap { info -> info.supportedTypes.asSequence().map { type -> info to type } }
                .firstOrNull { (_, t) -> t.equals("video/avc", ignoreCase = true) }
                ?.let { (info, type) ->
                    val caps = info.getCapabilitiesForType(type).encoderCapabilities
                    caps.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
                } ?: false
        } catch (_: Throwable) { false }
    }

    companion object {
        private const val TAG = "ScreenCapture"

        fun defaultConfig(context: Context): Config {
            // Read the actual physical screen size via WindowManager. Resources.getSystem()'s
            // displayMetrics is unreliable in multi-window / split-screen / some emulators.
            val (realW, realH, density) = readRealScreen(context)
            val (sw, sh) = scaleToMaxPreserveAspect(realW, realH, max = 1280)
            return Config(width = sw, height = sh, realWidth = realW, realHeight = realH, densityDpi = density)
        }

        @Suppress("DEPRECATION")
        private fun readRealScreen(context: Context): Triple<Int, Int, Int> {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm.maximumWindowMetrics.bounds
                val density = context.resources.displayMetrics.densityDpi
                Triple(bounds.width(), bounds.height(), density)
            } else {
                val dm = DisplayMetrics()
                val display: Display = wm.defaultDisplay
                display.getRealMetrics(dm)
                Triple(dm.widthPixels, dm.heightPixels, dm.densityDpi)
            }
        }

        /**
         * Scale so the long side equals {@code max}, then derive the short side from the
         * long side to keep the aspect ratio exact. Rounds to even (H.264 requirement).
         */
        private fun scaleToMaxPreserveAspect(w: Int, h: Int, max: Int): Pair<Int, Int> {
            val longSide = maxOf(w, h)
            if (longSide <= max) return alignEven(w) to alignEven(h)
            return if (h >= w) {
                val sh = max
                val sw = alignEven((w.toLong() * sh / h).toInt())
                sw to sh
            } else {
                val sw = max
                val sh = alignEven((h.toLong() * sw / w).toInt())
                sw to sh
            }
        }

        private fun alignEven(v: Int): Int = (v / 2) * 2
    }
}
