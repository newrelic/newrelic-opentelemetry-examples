package com.example.demo;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.runtimemetrics.GarbageCollector;
import io.opentelemetry.instrumentation.runtimemetrics.MemoryPools;
import io.opentelemetry.instrumentation.spring.webmvc.SpringWebMvcTracing;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import javax.servlet.Filter;

@SpringBootApplication
public class DemoApplication {

  public static void main(String[] args) {
    // Step 3: Initialize OpenTelemetry using autoconfigure
    // https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure
    AutoConfiguredOpenTelemetrySdk.initialize();

    // Step 4: Register runtime metrics instrumentation
    // https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/runtime-metrics/library
    MemoryPools.registerObservers();
    GarbageCollector.registerObservers();

    SpringApplication.run(DemoApplication.class, args);
  }

  // Step 5: Register Spring WebMVC instrumentation
  // https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/main/instrumentation/spring/spring-webmvc-3.1/library
  @Bean
  public Filter webMvcTracingFilter() {
    return SpringWebMvcTracing.create(GlobalOpenTelemetry.get()).newServletFilter();
  }

}
