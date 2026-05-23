/**
 * Thin wrapper around the WebCodecs {@link VideoDecoder} for H.264 baseline streams.
 *
 * The agent emits Annex-B framed bitstreams (start codes 0x00 00 00 01 separating NAL units).
 * WebCodecs accepts Annex-B when {@code description} is omitted in the decoder config.
 */
export class H264AnnexBDecoder {
  private decoder: VideoDecoder | null = null;
  private configured = false;
  private timestampUs = 0;
  private fps: number;

  constructor(
    private readonly onFrame: (frame: VideoFrame) => void,
    private readonly onError: (e: Error) => void,
    opts?: { fps?: number },
  ) {
    this.fps = opts?.fps ?? 30;
  }

  /** Throws synchronously if WebCodecs is unavailable in this browser. */
  ensureSupported() {
    if (typeof window.VideoDecoder === "undefined") {
      throw new Error("WebCodecs not supported — please use a recent Chrome, Edge, or Opera.");
    }
  }

  /** Configures with H.264 baseline level 3.1 (good default for mobile screen capture). */
  configure(codec: string = "avc1.42E01E") {
    if (this.configured) return;
    this.ensureSupported();
    this.decoder = new VideoDecoder({
      output: (frame) => this.onFrame(frame),
      error:  (e) => this.onError(e instanceof Error ? e : new Error(String(e))),
    });
    this.decoder.configure({
      codec,
      optimizeForLatency: true,
      hardwareAcceleration: "prefer-hardware",
    });
    this.configured = true;
  }

  feed(type: "key" | "delta", payload: Uint8Array) {
    if (!this.configured) this.configure();
    if (!this.decoder) return;
    const chunk = new EncodedVideoChunk({
      type,
      timestamp: this.timestampUs,
      data: payload,
    });
    this.timestampUs += Math.round(1_000_000 / this.fps);
    try {
      this.decoder.decode(chunk);
    } catch (e) {
      this.onError(e instanceof Error ? e : new Error(String(e)));
    }
  }

  reset() {
    this.timestampUs = 0;
    try { this.decoder?.reset(); } catch { /* ignore */ }
  }

  close() {
    try { this.decoder?.close(); } catch { /* ignore */ }
    this.decoder = null;
    this.configured = false;
  }
}
