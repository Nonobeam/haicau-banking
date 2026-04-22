package per.nonobeam.internal;

import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.ResponseErrorHandler;
import per.nonobeam.internal.factory.HttpServiceFactory;
import per.nonobeam.internal.provider.InternalClientDomainConfigurer;
import per.nonobeam.internal.provider.InternalClientDomainProvider;
import per.nonobeam.internal.setting.InternalHttpClientSetting;

@Configuration
@ComponentScan(basePackageClasses = PackageMarker.class)
public class InternalRestClientConfig {

  @Bean("internalConnectionConfig")
  @DependsOn("internalHttpClientSetting")
  ConnectionConfig connectionConfig(InternalHttpClientSetting setting) {
    return ConnectionConfig.custom()
        .setConnectTimeout(setting.getConnectTimeout(), TimeUnit.SECONDS)
        .setSocketTimeout(setting.getSocketTimeout(), TimeUnit.SECONDS)
        .build();
  }

  @Bean("internalPoolingHttpClientConnectionManager")
  @DependsOn("internalConnectionConfig")
  PoolingHttpClientConnectionManager poolingHttpClientConnectionManager(
      @Qualifier("internalConnectionConfig") ConnectionConfig connConfig,
      InternalHttpClientSetting setting) {
    return PoolingHttpClientConnectionManagerBuilder.create()
        .setDefaultConnectionConfig(connConfig)
        .setMaxConnTotal(setting.getMaxTotal())
        .setMaxConnPerRoute(setting.getMaxPerRoute())
        .build();
  }

  @Bean("internalCloseableHttpClient")
  @DependsOn("internalPoolingHttpClientConnectionManager")
  CloseableHttpClient closeableHttpClient(
      @Qualifier("internalPoolingHttpClientConnectionManager")
          PoolingHttpClientConnectionManager cm,
      InternalHttpClientSetting setting) {
    return HttpClientBuilder.create()
        .setConnectionManager(cm)
        .setKeepAliveStrategy(
            (response, context) -> TimeValue.of(setting.getKeepAliveTimeout(), TimeUnit.SECONDS))
        .build();
  }

  @Bean("internalHttpClientRequestFactory")
  @DependsOn("internalCloseableHttpClient")
  HttpComponentsClientHttpRequestFactory clientHttpRequestFactory(
      @Qualifier("internalCloseableHttpClient") CloseableHttpClient closeableHttpClient) {
    HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
    factory.setHttpClient(closeableHttpClient);
    return factory;
  }

  @Bean("internalResponseErrorHandler")
  ResponseErrorHandler internalResponseErrorHandler() {
    return new DefaultResponseErrorHandler();
  }

  @Bean
  @DependsOn("internalHttpClientRequestFactory")
  HttpServiceFactory<?> internalHttpServiceFactory(
      @Qualifier("internalHttpClientRequestFactory")
          HttpComponentsClientHttpRequestFactory requestFactory,
      @Qualifier("internalResponseErrorHandler") ResponseErrorHandler errorHandler) {
    return new HttpServiceFactory<>(requestFactory, errorHandler);
  }

  @Bean
  @ConditionalOnMissingBean
  InternalClientDomainConfigurer defaultInternalClientDomainConfigurer() {
    return spec -> {};
  }

  @Bean
  InternalClientDomainProvider internalClientDomainProvider(
      InternalClientDomainConfigurer domainConfigurer) {
    return new InternalClientDomainProvider(domainConfigurer);
  }
}
