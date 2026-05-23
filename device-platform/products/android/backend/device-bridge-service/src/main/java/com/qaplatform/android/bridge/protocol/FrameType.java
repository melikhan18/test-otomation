package com.qaplatform.android.bridge.protocol;

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

    // ── Faz 2: APK install + app launch + device reset ─────────────────────
    /** hub → agent (JSON: {requestId, packageName}) */
    public static final byte APP_INFO_REQUEST     = 0x0B;
    /** agent → hub (JSON: {requestId, installed, versionCode?, versionName?}) */
    public static final byte APP_INFO_RESPONSE    = 0x0C;
    /** hub → agent (JSON: {requestId, downloadUrl, sha256, expectedVersionCode, packageName}) */
    public static final byte INSTALL_APK_REQUEST  = 0x0D;
    /** agent → hub (JSON: {requestId, status: ok|failed, installedVersionCode?, errorCode?, errorMessage?}) */
    public static final byte INSTALL_APK_RESPONSE = 0x0E;
    /** hub → agent (JSON: {requestId, packageName}) */
    public static final byte LAUNCH_APP_REQUEST   = 0x0F;
    /** agent → hub (JSON: {requestId, status: ok|failed, errorMessage?}) */
    public static final byte LAUNCH_APP_RESPONSE  = 0x10;
    /** hub → agent (JSON: {requestId, packageName?, killProcess?}) */
    public static final byte RESET_HOME_REQUEST   = 0x11;
    /** agent → hub (JSON: {requestId, status: ok|failed, errorMessage?}) */
    public static final byte RESET_HOME_RESPONSE  = 0x12;

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
            case APP_INFO_REQUEST     -> "APP_INFO_REQUEST";
            case APP_INFO_RESPONSE    -> "APP_INFO_RESPONSE";
            case INSTALL_APK_REQUEST  -> "INSTALL_APK_REQUEST";
            case INSTALL_APK_RESPONSE -> "INSTALL_APK_RESPONSE";
            case LAUNCH_APP_REQUEST   -> "LAUNCH_APP_REQUEST";
            case LAUNCH_APP_RESPONSE  -> "LAUNCH_APP_RESPONSE";
            case RESET_HOME_REQUEST   -> "RESET_HOME_REQUEST";
            case RESET_HOME_RESPONSE  -> "RESET_HOME_RESPONSE";
            default -> "UNKNOWN(" + (t & 0xff) + ")";
        };
    }

    public static boolean isVideo(byte t) { return t == VIDEO_KEYFRAME || t == VIDEO_DELTA; }
    public static boolean isInspectResponse(byte t) { return t == INSPECT_RESPONSE; }
    public static boolean isScreenshotResponse(byte t) { return t == SCREENSHOT_RESPONSE; }
}
