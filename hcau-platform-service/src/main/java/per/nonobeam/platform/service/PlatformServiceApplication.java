package per.nonobeam.platform.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "per.nonobeam")
@EnableJpaRepositories(basePackages = "per.nonobeam.platform.repository")
@EntityScan(basePackages = "per.nonobeam.platform.account")
public class PlatformServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(PlatformServiceApplication.class, args);
  }
}
