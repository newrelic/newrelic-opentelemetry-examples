package com.newrelic.app;

//note that this dependency is just "a place where you would do your OTel setup"; not an actual dependency on New Relic SDKs.
import com.newrelic.shared.OpenTelemetryConfig;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {

  public static void main(String[] args) {
    // Configure OpenTelemetry as early as possible
    OpenTelemetryConfig.configureGlobal("otel-app");
    SpringApplication.run(Application.class, args);
  }
}
