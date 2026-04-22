package per.nonobeam.internal.setting;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component("internalHttpClientSetting")
@ConfigurationProperties(prefix = "internal.http-client")
public class InternalHttpClientSetting {
  private long connectTimeout = 30;
  private int socketTimeout = 30;
  private long keepAliveTimeout = 60;
  private int maxTotal = 100;
  private int maxPerRoute = 20;
}
