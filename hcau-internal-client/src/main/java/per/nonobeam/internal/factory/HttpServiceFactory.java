package per.nonobeam.internal.factory;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import per.nonobeam.internal.exchange.BaseExchange;
import per.nonobeam.internal.interceptor.CompositeClientHttpRequestInterceptor;
import per.nonobeam.internal.provider.InternalClientDomainProvider;
import per.nonobeam.internal.provider.ServiceConfig;

@Slf4j
public class HttpServiceFactory<T extends BaseExchange> {

  private final HttpComponentsClientHttpRequestFactory requestFactory;
  private final ResponseErrorHandler errorHandler;

  public HttpServiceFactory(
      HttpComponentsClientHttpRequestFactory requestFactory, ResponseErrorHandler errorHandler) {
    log.info("Internal HTTP Service Factory initialized");
    this.requestFactory = requestFactory;
    this.errorHandler = errorHandler;
  }

  public T create(Class<T> clazz, InternalClientDomainProvider domainProvider) {
    ServiceConfig serviceConfig = domainProvider.getService(clazz);

    RestClient.Builder builder =
        RestClient.builder()
            .baseUrl(serviceConfig.getUrl())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .requestFactory(requestFactory)
            .defaultStatusHandler(errorHandler);

    List<ClientHttpRequestInterceptor> interceptors = serviceConfig.getInterceptors();
    if (!interceptors.isEmpty()) {
      builder.requestInterceptor(new CompositeClientHttpRequestInterceptor(interceptors));
    }

    RestClient restClient = builder.build();

    return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
        .build()
        .createClient(clazz);
  }
}
