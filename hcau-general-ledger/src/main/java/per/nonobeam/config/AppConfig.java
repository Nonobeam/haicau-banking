package per.nonobeam.config;

import java.nio.charset.StandardCharsets;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {

  @Bean
  public RestClient restClient() {
    return RestClient.builder().build();
  }

  @Bean
  public MessageSource messageSource() {
    ResourceBundleMessageSource source = new ResourceBundleMessageSource();
    source.setBasename("messages");
    source.setDefaultEncoding(StandardCharsets.UTF_8.name());
    source.setUseCodeAsDefaultMessage(true);
    source.setFallbackToSystemLocale(false);
    return source;
  }
}
