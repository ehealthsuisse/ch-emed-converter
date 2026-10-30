package ch.emed.converter.rest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Entry point of the stateless REST conversion service (feature 002). */
@SpringBootApplication
@ConfigurationPropertiesScan
public class RestApplication {

  public static void main(String[] args) {
    SpringApplication.run(RestApplication.class, args);
  }
}
