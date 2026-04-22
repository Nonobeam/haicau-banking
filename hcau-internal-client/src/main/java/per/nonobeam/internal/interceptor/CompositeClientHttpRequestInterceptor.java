package per.nonobeam.internal.interceptor;

import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

public class CompositeClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

  private final List<ClientHttpRequestInterceptor> delegates;

  public CompositeClientHttpRequestInterceptor(List<ClientHttpRequestInterceptor> delegates) {
    this.delegates = delegates;
  }

  @Override
  public ClientHttpResponse intercept(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    ClientHttpRequestExecution chain = execution;
    for (int i = delegates.size() - 1; i >= 0; i--) {
      ClientHttpRequestInterceptor current = delegates.get(i);
      ClientHttpRequestExecution finalChain = chain;
      chain = (req, b) -> current.intercept(req, b, finalChain);
    }
    return chain.execute(request, body);
  }
}
