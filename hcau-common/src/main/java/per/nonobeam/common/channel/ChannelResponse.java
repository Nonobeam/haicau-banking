package per.nonobeam.common.channel;

import java.time.Instant;

public record ChannelResponse<T>(
    String correlationId, String traceId, String sourceService, Instant timestamp, T body) {}
