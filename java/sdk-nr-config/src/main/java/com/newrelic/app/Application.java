package com.newrelic.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {

  public static void main(String[] args) {
    // Configure OpenTelemetry as early as possible
    OpenTelemetryConfig.configure();
    SpringApplication.run(Application.class, args);
  }
}
