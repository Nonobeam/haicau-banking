package per.nonobeam.internal.channel;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import per.nonobeam.common.channel.ServiceContext;
import per.nonobeam.common.channel.ServiceId;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "hcau.service")
public class HcauServiceProperties {

  @NotBlank(message = "hcau.service.id is required — declare it in application.yml")
  @Pattern(
      regexp = ServiceId.FORMAT,
      message = "hcau.service.id must be exactly 3 digits (e.g. 001, 042, 100)")
  private String id;

  @PostConstruct
  void registerServiceId() {
    ServiceContext.register(id);
  }
}
