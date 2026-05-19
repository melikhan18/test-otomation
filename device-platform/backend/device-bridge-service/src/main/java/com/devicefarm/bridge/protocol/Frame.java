package com.devicefarm.bridge.protocol;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Immutable in-memory representation of a wire frame.
 * Use {@link #encode(WebSocketSession)} to build a binary {@link WebSocketMessage}.
 */
public record Frame(byte type, ByteBuffer payload) {

    public static Frame of(byte type, byte[] payload) {
        return new Frame(type, ByteBuffer.wrap(payload));
    }

    public static Frame ofJson(byte type, String json) {
        return new Frame(type, ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8)));
    }

    public static Frame empty(byte type) {
        return new Frame(type, ByteBuffer.allocate(0));
    }

    public int payloadSize() { return payload.remaining(); }

    public String payloadAsString() {
        ByteBuffer dup = payload.duplicate();
        byte[] arr = new byte[dup.remaining()];
        dup.get(arr);
        return new String(arr, StandardCharsets.UTF_8);
    }

    /**
     * Parse a single inbound binary WebSocket message into a Frame.
     *
     * Drains the underlying {@link DataBuffer} into a heap byte[] so the returned Frame
     * doesn't keep a reference to the pooled Netty buffer. We must NOT call
     * {@code DataBufferUtils.release(buf)} here — Reactor Netty auto-releases the buffer
     * after this {@code doOnNext} returns; releasing manually causes an
     * {@code IllegalReferenceCountException} on the next inbound frame.
     */
    public static Frame decode(WebSocketMessage msg) {
        DataBuffer buf = msg.getPayload();
        int n = buf.readableByteCount();
        if (n < 1) throw new IllegalArgumentException("empty frame");
        byte[] data = new byte[n];
        buf.read(data);
        byte t = data[0];
        ByteBuffer payload = (n == 1) ? ByteBuffer.allocate(0) : ByteBuffer.wrap(data, 1, n - 1);
        return new Frame(t, payload);
    }

    public WebSocketMessage encode(WebSocketSession session) {
        DataBufferFactory factory = session.bufferFactory();
        int size = payload.remaining();
        DataBuffer buf = factory.allocateBuffer(1 + size);
        buf.write(type);
        if (size > 0) {
            ByteBuffer dup = payload.duplicate();
            buf.write(dup);
        }
        return session.binaryMessage(f -> buf);
    }
}
