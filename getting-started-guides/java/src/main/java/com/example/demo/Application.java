package com.example.demo;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.log4j.appender.v2_17.OpenTelemetryAppender;
import io.opentelemetry.instrumentation.runtimemetrics.java8.Classes;
import io.opentelemetry.instrumentation.runtimemetrics.java8.Cpu;
import io.opentelemetry.instrumentation.runtimemetrics.java8.GarbageCollector;
import io.opentelemetry.instrumentation.runtimemetrics.java8.MemoryPools;
import io.opentelemetry.instrumentation.runtimemetrics.java8.Threads;
import io.opentelemetry.instrumentation.runtimemetrics.java8.internal.ExperimentalBufferPools;
import io.opentelemetry.instrumentation.runtimemetrics.java8.internal.ExperimentalCpu;
import io.opentelemetry.instrumentation.runtimemetrics.java8.internal.ExperimentalMemoryPools;
import io.opentelemetry.instrumentation.spring.webmvc.v6_0.SpringWebMvcTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import jakarta.servlet.Filter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application {

  private static volatile OpenTelemetry openTelemetry = OpenTelemetry.noop();

  public static void main(String[] args) {
    // Build the SDK auto-configuration extension module
    OpenTelemetrySdk openTelemetrySdk =
        AutoConfiguredOpenTelemetrySdk.builder().build().getOpenTelemetrySdk();
    Application.openTelemetry = openTelemetrySdk;

    // Register runtime metrics instrumentation
    Classes.registerObservers(openTelemetrySdk);
    Cpu.registerObservers(openTelemetrySdk);
    GarbageCollector.registerObservers(openTelemetrySdk);
    MemoryPools.registerObservers(openTelemetrySdk);
    Threads.registerObservers(openTelemetrySdk);

    ExperimentalCpu.registerObservers(openTelemetrySdk);
    ExperimentalBufferPools.registerObservers(openTelemetrySdk);
    ExperimentalMemoryPools.registerObservers(openTelemetrySdk);

    SpringApplication.run(Application.class, args);

    // Setup log4j OpenTelemetryAppender
    // Normally this is done before the framework (Spring) is initialized. However, spring boot
    // erases any programmatic log configuration so we must initialize after Spring. Unfortunately,
    // this means that Spring startup logs do not make it to the OpenTelemetry.
    // See this issue for tracking: https://github.com/spring-projects/spring-boot/issues/25847
    OpenTelemetryAppender.install(openTelemetrySdk);
  }

  @Bean
  public OpenTelemetry openTelemetry() {
    return openTelemetry;
  }

  // Add Spring WebMVC instrumentation by registering a tracing filter
  @Bean
  public Filter webMvcTracingFilter(OpenTelemetry openTelemetry) {
    return SpringWebMvcTelemetry.create(openTelemetry).createServletFilter();
  }
}
