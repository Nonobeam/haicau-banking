package per.nonobeam.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(
    basePackages = {"per.nonobeam.web", "per.nonobeam.common", "per.nonobeam.repository"})
@EnableJpaRepositories(basePackages = {"per.nonobeam.web.repository", "per.nonobeam.repository"})
@EntityScan(basePackages = {"per.nonobeam.web.entity", "per.nonobeam.entity"})
public class Application {
  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
