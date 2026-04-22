package per.nonobeam.internal.factory;

import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.ResponseErrorHandler;

public abstract class BaseServiceManager {

  protected final HttpComponentsClientHttpRequestFactory requestFactory;
  protected final ResponseErrorHandler errorHandler;

  protected BaseServiceManager(
      HttpComponentsClientHttpRequestFactory requestFactory, ResponseErrorHandler errorHandler) {
    this.requestFactory = requestFactory;
    this.errorHandler = errorHandler;
  }
}
