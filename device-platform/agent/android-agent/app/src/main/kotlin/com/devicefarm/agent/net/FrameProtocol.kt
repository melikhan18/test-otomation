package com.devicefarm.agent.net

import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.nio.charset.StandardCharsets

/**
 * Wire protocol: each WebSocket binary message is exactly one frame.
 *   [1 byte type][payload bytes]
 */
object FrameType {
    const val VIDEO_KEYFRAME: Byte      = 0x01
    const val VIDEO_DELTA:    Byte      = 0x02
    const val CONTROL_COMMAND: Byte     = 0x03
    const val INSPECT_REQUEST: Byte     = 0x04
    const val INSPECT_RESPONSE: Byte    = 0x05
    const val HEARTBEAT: Byte           = 0x06
    const val STREAM_METADATA: Byte     = 0x07
    const val FORCE_KEYFRAME: Byte      = 0x08
    /** hub → agent (JSON: {requestId}) */
    const val SCREENSHOT_REQUEST: Byte  = 0x09
    /** agent → hub: [4 bytes BE metaLen][JSON meta][PNG bytes] */
    const val SCREENSHOT_RESPONSE: Byte = 0x0A
}

data class Frame(val type: Byte, val payload: ByteArray) {

    fun encode(): ByteString {
        val buf = Buffer().apply {
            writeByte(type.toInt())
            if (payload.isNotEmpty()) write(payload)
        }
        return buf.readByteString()
    }

    fun payloadAsString(): String = String(payload, StandardCharsets.UTF_8)

    companion object {
        fun decode(bytes: ByteString): Frame {
            val arr = bytes.toByteArray()
            if (arr.isEmpty()) throw IllegalArgumentException("empty frame")
            val type = arr[0]
            val payload = if (arr.size == 1) ByteArray(0) else arr.copyOfRange(1, arr.size)
            return Frame(type, payload)
        }

        fun ofJson(type: Byte, json: String): Frame =
            Frame(type, json.toByteArray(StandardCharsets.UTF_8))

        fun empty(type: Byte): Frame = Frame(type, ByteArray(0))
    }
}

// helper for raw byte arrays
fun ByteArray.toBs(): ByteString = this.toByteString(0, this.size)
