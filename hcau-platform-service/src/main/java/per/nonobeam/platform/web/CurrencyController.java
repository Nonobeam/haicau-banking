package per.nonobeam.platform.web;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import per.nonobeam.internal.channel.InternalChannelProperties;

@RestController
@RequestMapping("/api/v1/currencies")
@RequiredArgsConstructor
public class CurrencyController {

  private static final String SERVICE_NAME = "general-ledger";
  private static final String PATH = "/internal/v1/currencies";

  private final InternalChannelProperties properties;
  private final RestClient.Builder restClientBuilder = RestClient.builder();

  @GetMapping
  public Object[] listCurrencies() {
    InternalChannelProperties.ChannelConfig config = properties.getChannels().get(SERVICE_NAME);
    if (config == null) {
      throw new IllegalStateException("No channel configured for service: " + SERVICE_NAME);
    }
    return restClientBuilder
        .build()
        .get()
        .uri(config.getUrl() + PATH)
        .retrieve()
        .body(Object[].class);
  }
}
