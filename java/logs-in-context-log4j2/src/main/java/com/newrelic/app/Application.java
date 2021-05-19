package com.newrelic.app;

import com.newrelic.shared.OpenTelemetryConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {

  public static void main(String[] args) {
    // Configure OpenTelemetry as early as possible
    OpenTelemetryConfig.configureGlobal("logs-in-context");
    SpringApplication.run(Application.class, args);
  }
}
