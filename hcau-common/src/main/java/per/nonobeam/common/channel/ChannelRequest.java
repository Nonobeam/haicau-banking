package per.nonobeam.common.channel;

import java.time.Instant;
import java.util.UUID;

public record ChannelRequest<T>(
    String correlationId, String traceId, String sourceService, Instant timestamp, T body) {

  public ChannelRequest {
    if (sourceService != null) {
      ServiceId.validate(sourceService);
    }
  }

  public static <T> ChannelRequest<T> of(T body) {
    return new ChannelRequest<>(
        UUID.randomUUID().toString(), null, ServiceContext.current(), Instant.now(), body);
  }

  public static <T> ChannelRequest<T> of(T body, String correlationId) {
    return new ChannelRequest<>(correlationId, null, ServiceContext.current(), Instant.now(), body);
  }

  public static <T> ChannelRequest<T> of(T body, String correlationId, String traceId) {
    return new ChannelRequest<>(
        correlationId, traceId, ServiceContext.current(), Instant.now(), body);
  }
}
