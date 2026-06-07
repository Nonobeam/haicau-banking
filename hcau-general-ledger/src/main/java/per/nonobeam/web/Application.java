package per.nonobeam.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(
    basePackages = {
      "per.nonobeam.web",
      "per.nonobeam.internal",
      "per.nonobeam.config",
      "per.nonobeam.batch",
      "per.nonobeam.intake",
      "per.nonobeam.gate",
      "per.nonobeam.ordering",
      "per.nonobeam.saga",
      "per.nonobeam.health",
      "per.nonobeam.scaling",
      "per.nonobeam.deadletter",
      "per.nonobeam.validation",
      "per.nonobeam.repository"
    })
@EnableJpaRepositories(
    basePackages = {"per.nonobeam.web.repository", "per.nonobeam.saga", "per.nonobeam.deadletter"})
@EntityScan(
    basePackages = {
      "per.nonobeam.web.common",
      "per.nonobeam.common.config",
      "per.nonobeam.saga",
      "per.nonobeam.deadletter",
      "per.nonobeam.web.repository"
    })
public class Application {
  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
