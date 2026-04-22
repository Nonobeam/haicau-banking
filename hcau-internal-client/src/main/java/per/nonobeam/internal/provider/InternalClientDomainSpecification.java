package per.nonobeam.internal.provider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.client.ClientHttpRequestInterceptor;

public class InternalClientDomainSpecification {

  private final Map<Class<?>, String> serviceUrls = new HashMap<>();
  private final Map<Class<?>, List<ClientHttpRequestInterceptor>> serviceInterceptors =
      new HashMap<>();

  public ServiceBuilder service(Class<?> type, String url) {
    serviceUrls.put(type, url);
    return new ServiceBuilder(type);
  }

  public ServiceConfig getService(Class<?> serviceClass) {
    String url = serviceUrls.get(serviceClass);
    List<ClientHttpRequestInterceptor> interceptors =
        serviceInterceptors.getOrDefault(serviceClass, List.of());
    return new ServiceConfig(url, interceptors);
  }

  public class ServiceBuilder {

    private final Class<?> serviceClass;

    ServiceBuilder(Class<?> serviceClass) {
      this.serviceClass = serviceClass;
    }

    public ServiceBuilder withInterceptor(ClientHttpRequestInterceptor interceptor) {
      serviceInterceptors.computeIfAbsent(serviceClass, k -> new ArrayList<>()).add(interceptor);
      return this;
    }
  }
}
