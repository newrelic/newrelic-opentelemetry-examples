package com.newrelic.app;

import com.newrelic.shared.OpenTelemetryConfig;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.log4j.appender.v2_16.OpenTelemetryAppender;
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
    // Configure OpenTelemetry as early as possible
    var defaultServiceName = "sdk-nr-config";
    var openTelemetrySdk = OpenTelemetryConfig.configureGlobal(defaultServiceName);

    // Initialize Log4j2 appender
    OpenTelemetryAppender.setSdkLogEmitterProvider(openTelemetrySdk.getSdkLogEmitterProvider());

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
