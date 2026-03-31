package per.nonobeam.platform.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "per.nonobeam")
public class PlatformServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(PlatformServiceApplication.class, args);
  }
}
