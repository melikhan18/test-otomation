package com.devicefarm.bridge.protocol;

/**
 * Wire protocol frame types. Each WebSocket binary message is exactly one frame:
 * <pre>[1 byte type][payload bytes]</pre>
 * WebSocket framing already provides length, so no extra length prefix is needed.
 */
public final class FrameType {
    private FrameType() {}

    public static final byte VIDEO_KEYFRAME       = 0x01; // agent → hub (H.264 SPS+PPS+IDR)
    public static final byte VIDEO_DELTA          = 0x02; // agent → hub (H.264 P-frame)
    public static final byte CONTROL_COMMAND      = 0x03; // hub → agent (JSON: tap/swipe/key/text)
    public static final byte INSPECT_REQUEST      = 0x04; // hub → agent (JSON: {requestId})
    public static final byte INSPECT_RESPONSE     = 0x05; // agent → hub (JSON: node tree + requestId)
    public static final byte HEARTBEAT            = 0x06; // both directions
    public static final byte STREAM_METADATA      = 0x07; // agent → hub (JSON: width,height,fps,codec)
    public static final byte FORCE_KEYFRAME       = 0x08; // hub → agent (no payload)
    public static final byte SCREENSHOT_REQUEST   = 0x09; // hub → agent (JSON: {requestId})
    /** agent → hub: [4-byte BE metaLen][JSON meta][PNG bytes] */
    public static final byte SCREENSHOT_RESPONSE  = 0x0A;

    public static String name(byte t) {
        return switch (t) {
            case VIDEO_KEYFRAME       -> "VIDEO_KEYFRAME";
            case VIDEO_DELTA          -> "VIDEO_DELTA";
            case CONTROL_COMMAND      -> "CONTROL_COMMAND";
            case INSPECT_REQUEST      -> "INSPECT_REQUEST";
            case INSPECT_RESPONSE     -> "INSPECT_RESPONSE";
            case HEARTBEAT            -> "HEARTBEAT";
            case STREAM_METADATA      -> "STREAM_METADATA";
            case FORCE_KEYFRAME       -> "FORCE_KEYFRAME";
            case SCREENSHOT_REQUEST   -> "SCREENSHOT_REQUEST";
            case SCREENSHOT_RESPONSE  -> "SCREENSHOT_RESPONSE";
            default -> "UNKNOWN(" + (t & 0xff) + ")";
        };
    }

    public static boolean isVideo(byte t) { return t == VIDEO_KEYFRAME || t == VIDEO_DELTA; }
    public static boolean isInspectResponse(byte t) { return t == INSPECT_RESPONSE; }
    public static boolean isScreenshotResponse(byte t) { return t == SCREENSHOT_RESPONSE; }
}
