package com.newrelic.app;

import com.newrelic.shared.OpenTelemetryConfig;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.log4j.v2_13_2.OpenTelemetryLog4j;
import io.opentelemetry.instrumentation.runtimemetrics.GarbageCollector;
import io.opentelemetry.instrumentation.runtimemetrics.MemoryPools;
import io.opentelemetry.instrumentation.spring.webmvc.SpringWebMvcTracing;
import javax.servlet.Filter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application {

  public static void main(String[] args) {
    var defaultServiceName = "sdk-nr-config";

    // Configure OpenTelemetry as early as possible
    OpenTelemetryConfig.configureGlobal(defaultServiceName);

    // Initialize Log4j2 appender
    var logEmitterProvider = OpenTelemetryConfig.configureLogSdk(defaultServiceName);
    OpenTelemetryLog4j.initialize(logEmitterProvider);

    // Register runtime metrics instrumentation
    MemoryPools.registerObservers();
    GarbageCollector.registerObservers();

    SpringApplication.run(Application.class, args);
  }

  /** Add Spring WebMVC instrumentation by registering tracing filter. */
  @Bean
  public Filter webMvcTracingFilter() {
    return SpringWebMvcTracing.create(GlobalOpenTelemetry.get()).newServletFilter();
  }
}
