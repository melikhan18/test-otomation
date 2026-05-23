package com.qaplatform.android.bridge.hub;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DeviceChannelRegistry {

    private final Map<Long, DeviceChannel> channels = new ConcurrentHashMap<>();
    private final MeterRegistry meters;
    private final int webBufferSize;

    public DeviceChannelRegistry(MeterRegistry meters,
                                 @Value("${app.bridge.web-buffer-size:60}") int webBufferSize) {
        this.meters = meters;
        this.webBufferSize = webBufferSize;
    }

    /** Register a fresh channel, replacing any existing one (a reconnecting agent). */
    public DeviceChannel attach(long deviceId, long productId) {
        DeviceChannel previous = channels.remove(deviceId);
        if (previous != null) previous.close();
        DeviceChannel fresh = new DeviceChannel(deviceId, productId, webBufferSize, meters);
        channels.put(deviceId, fresh);
        return fresh;
    }

    public void detach(long deviceId, DeviceChannel channel) {
        channels.remove(deviceId, channel);
        channel.close();
    }

    public Optional<DeviceChannel> get(long deviceId) {
        return Optional.ofNullable(channels.get(deviceId));
    }

    public int size() { return channels.size(); }
}
