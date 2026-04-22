package per.nonobeam.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {"per.nonobeam.web", "per.nonobeam.internal", "per.nonobeam.config"})
@EnableJpaRepositories(basePackages = {"per.nonobeam.web.repository"})
@EntityScan(basePackages = {"per.nonobeam.web.common", "per.nonobeam.common.config"})
public class Application {
  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
