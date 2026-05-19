package com.devicefarm.bridge.hub;

import com.devicefarm.bridge.protocol.Frame;
import com.devicefarm.bridge.protocol.FrameType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One per connected agent. Multiplexes traffic in two directions:
 * <ul>
 *   <li><b>agentToWeb</b> — multicast video + inspect-response frames to any number of web subscribers</li>
 *   <li><b>webToAgent</b> — control commands, inspect requests, force-keyframe</li>
 * </ul>
 *
 * The agent → web stream caches the most recent codec config (so a late subscriber can prime its decoder).
 */
public class DeviceChannel {

    private static final Logger log = LoggerFactory.getLogger(DeviceChannel.class);

    private final long deviceId;
    private final long productId;
    private final Sinks.Many<Frame> agentToWeb;
    private final Sinks.Many<Frame> webToAgent;
    private final AtomicReference<Frame> lastStreamMetadata = new AtomicReference<>();
    private final AtomicReference<Frame> lastKeyframe = new AtomicReference<>();
    private final Counter framesIn;
    private final Counter framesOut;
    private final Counter framesDropped;

    public DeviceChannel(long deviceId, long productId, int webBufferSize, MeterRegistry meters) {
        this.deviceId = deviceId;
        this.productId = productId;
        this.agentToWeb = Sinks.many().multicast().onBackpressureBuffer(webBufferSize, false);
        this.webToAgent = Sinks.many().unicast().onBackpressureBuffer();
        this.framesIn      = Counter.builder("bridge.frames.in").tag("deviceId", String.valueOf(deviceId)).register(meters);
        this.framesOut     = Counter.builder("bridge.frames.out").tag("deviceId", String.valueOf(deviceId)).register(meters);
        this.framesDropped = Counter.builder("bridge.frames.dropped").tag("deviceId", String.valueOf(deviceId)).register(meters);
    }

    public long deviceId()  { return deviceId; }
    public long productId() { return productId; }

    /** Push a frame received from the agent. */
    public void publishFromAgent(Frame f) {
        framesIn.increment();
        if (f.type() == FrameType.STREAM_METADATA) {
            lastStreamMetadata.set(f);
        } else if (f.type() == FrameType.VIDEO_KEYFRAME) {
            lastKeyframe.set(f);
        }
        Sinks.EmitResult r = agentToWeb.tryEmitNext(f);
        if (r.isFailure()) {
            framesDropped.increment();
            if (log.isTraceEnabled()) log.trace("dropped {} for device {} ({})", FrameType.name(f.type()), deviceId, r);
        } else {
            framesOut.increment();
        }
    }

    /** Subscribe a web client. Includes a primer of last metadata + last keyframe so late joiners can decode. */
    public Flux<Frame> subscribeWeb(boolean videoOnly) {
        Frame metadata = lastStreamMetadata.get();
        Frame keyframe = lastKeyframe.get();
        Flux<Frame> primer = Flux.empty();
        if (metadata != null) primer = primer.concatWith(Flux.just(metadata));
        if (keyframe != null) primer = primer.concatWith(Flux.just(keyframe));

        Flux<Frame> live = agentToWeb.asFlux();
        if (videoOnly) live = live.filter(f -> FrameType.isVideo(f.type()) || f.type() == FrameType.STREAM_METADATA);
        return primer.concatWith(live);
    }

    /** Send a control/inspect/forceKeyframe frame to the agent. */
    public void sendToAgent(Frame f) {
        Sinks.EmitResult r = webToAgent.tryEmitNext(f);
        if (r.isFailure() && log.isDebugEnabled()) {
            log.debug("could not enqueue {} for device {}: {}", FrameType.name(f.type()), deviceId, r);
        }
    }

    /** Flux of frames that the agent's WebSocket sink should write out. */
    public Flux<Frame> agentOutbound() { return webToAgent.asFlux(); }

    public void requestKeyframe() {
        sendToAgent(new Frame(FrameType.FORCE_KEYFRAME, ByteBuffer.allocate(0)));
    }

    public void close() {
        agentToWeb.tryEmitComplete();
        webToAgent.tryEmitComplete();
    }
}
