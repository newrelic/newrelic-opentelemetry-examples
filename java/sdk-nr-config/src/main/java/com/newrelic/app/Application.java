package com.newrelic.app;

import com.newrelic.shared.OpenTelemetryConfig;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.runtimemetrics.GarbageCollector;
import io.opentelemetry.instrumentation.runtimemetrics.MemoryPools;
import io.opentelemetry.instrumentation.spring.webmvc.WebMvcTracingFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application {

  public static void main(String[] args) {
    // Configure OpenTelemetry as early as possible
    OpenTelemetryConfig.configureGlobal("sdk-nr-config-");

    // Register runtime metrics instrumentation
    MemoryPools.registerObservers();
    GarbageCollector.registerObservers();

    SpringApplication.run(Application.class, args);
  }

  /** Add Spring WebMVC instrumentation by registering tracing filter. */
  @Bean
  public WebMvcTracingFilter webMvcTracingFilter() {
    return new WebMvcTracingFilter(GlobalOpenTelemetry.get());
  }
}
