package per.nonobeam.internal.provider;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.client.ClientHttpRequestInterceptor;

@Getter
@RequiredArgsConstructor
public class ServiceConfig {
  private final String url;
  private final List<ClientHttpRequestInterceptor> interceptors;
}
