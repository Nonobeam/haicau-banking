package per.nonobeam.internal.channel;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "internal")
public class InternalChannelProperties {

  private Map<String, ChannelConfig> channels = new HashMap<>();

  @Data
  public static class ChannelConfig {
    private String url;
  }
}
