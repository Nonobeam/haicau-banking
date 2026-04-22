package per.nonobeam.internal.channel;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import per.nonobeam.common.channel.ChannelRequest;
import per.nonobeam.common.channel.ChannelResponse;
import per.nonobeam.common.channel.CommunicationChannel;

@Component
public class HttpCommunicationChannel extends CommunicationChannel {

  private static final Logger log = LoggerFactory.getLogger(HttpCommunicationChannel.class);

  static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
  static final String HEADER_TRACE_ID = "X-Trace-Id";
  static final String HEADER_SOURCE_SERVICE = "X-Source-Service";

  private final RestClient restClient;
  private final InternalChannelProperties properties;

  public HttpCommunicationChannel(
      @Qualifier("internalHttpClientRequestFactory")
          HttpComponentsClientHttpRequestFactory requestFactory,
      @Qualifier("internalResponseErrorHandler") ResponseErrorHandler errorHandler,
      InternalChannelProperties properties) {
    this.properties = properties;
    this.restClient =
        RestClient.builder()
            .requestFactory(requestFactory)
            .defaultStatusHandler(errorHandler)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build();
  }

  @Override
  public <ReqT, ResT> ChannelResponse<ResT> pull(
      String destination, ChannelRequest<ReqT> request, Class<ResT> responseType) {
    return send(destination, request, responseType);
  }

  @Override
  public <ReqT, ResT> ChannelResponse<ResT> push(
      String destination, ChannelRequest<ReqT> request, Class<ResT> responseType) {
    return send(destination, request, responseType);
  }

  private <ReqT, ResT> ChannelResponse<ResT> send(
      String destination, ChannelRequest<ReqT> request, Class<ResT> responseType) {
    logRequest(destination, request);
    try {
      String url = resolveUrl(destination);
      RestClient.RequestBodySpec spec =
          restClient
              .post()
              .uri(url)
              .header(HEADER_CORRELATION_ID, request.correlationId())
              .header(HEADER_SOURCE_SERVICE, request.sourceService());
      if (request.traceId() != null) {
        spec = spec.header(HEADER_TRACE_ID, request.traceId());
      }
      ResponseEntity<ResT> entity = spec.body(request.body()).retrieve().toEntity(responseType);

      HttpHeaders responseHeaders = entity.getHeaders();
      ChannelResponse<ResT> response =
          new ChannelResponse<>(
              firstHeader(responseHeaders, HEADER_CORRELATION_ID, request.correlationId()),
              firstHeader(responseHeaders, HEADER_TRACE_ID, request.traceId()),
              firstHeader(responseHeaders, HEADER_SOURCE_SERVICE, null),
              Instant.now(),
              entity.getBody());
      logResponse(destination, response);
      return response;
    } catch (Exception e) {
      onError(destination, request, e);
      throw e;
    }
  }

  private static String firstHeader(HttpHeaders headers, String name, String fallback) {
    String value = headers.getFirst(name);
    return value != null ? value : fallback;
  }

  private String resolveUrl(String destination) {
    int slash = destination.indexOf('/');
    if (slash < 0) {
      throw new IllegalArgumentException(
          "Invalid channel destination (missing path): " + destination);
    }
    String serviceName = destination.substring(0, slash);
    String path = destination.substring(slash);
    InternalChannelProperties.ChannelConfig config = properties.getChannels().get(serviceName);
    if (config == null) {
      throw new IllegalStateException("No channel configured for service: " + serviceName);
    }
    return config.getUrl() + path;
  }

  @Override
  protected void logRequest(String destination, ChannelRequest<?> request) {
    log.debug(
        "channel -> {} from={} correlationId={} traceId={}",
        destination,
        request.sourceService(),
        request.correlationId(),
        request.traceId());
  }

  @Override
  protected void logResponse(String destination, ChannelResponse<?> response) {
    log.debug(
        "channel <- {} from={} correlationId={}",
        destination,
        response.sourceService(),
        response.correlationId());
  }

  @Override
  protected void onError(String destination, ChannelRequest<?> request, Throwable e) {
    log.error(
        "channel error -> {} from={} correlationId={}: {}",
        destination,
        request.sourceService(),
        request.correlationId(),
        e.getMessage());
  }
}
