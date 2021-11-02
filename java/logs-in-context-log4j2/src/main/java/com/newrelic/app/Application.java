package com.newrelic.app;

import com.newrelic.shared.OpenTelemetryConfig;
import io.opentelemetry.instrumentation.log4j.v2_13_2.OpenTelemetryLog4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {

  public static void main(String[] args) {
    var defaultServiceName = "logs-in-context-log4j2";
    OpenTelemetryConfig.configureGlobal(defaultServiceName);
    OpenTelemetryLog4j.initialize(OpenTelemetryConfig.sdkLogEmitterProvider(defaultServiceName));

    SpringApplication.run(Application.class, args);
  }
}
