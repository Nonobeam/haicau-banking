package per.nonobeam.platform.config.setting;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "internal.channels.general-ledger")
public class GeneralLedger {

  private String url;
  private Endpoints endpoints = new Endpoints();

  @Data
  public static class Endpoints {
    private String createAccount;
    private String createCurrencyAccount;
  }
}
