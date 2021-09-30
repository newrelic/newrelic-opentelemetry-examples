package com.newrelic.app;

import com.newrelic.shared.OpenTelemetryConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {

  public static void main(String[] args) {
    OpenTelemetryConfig.configureGlobal(System.getenv("SERVICE_NAME"));
    SpringApplication.run(Application.class, args);
  }
}
