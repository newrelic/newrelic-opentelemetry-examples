package com.example.demo;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.logs.GlobalLoggerProvider;
import io.opentelemetry.instrumentation.runtimemetrics.*;
import io.opentelemetry.instrumentation.spring.webmvc.v5_3.SpringWebMvcTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import javax.servlet.Filter;

@SpringBootApplication
public class Application {

    private static volatile OpenTelemetry openTelemetry = OpenTelemetry.noop();

    public static void main(String[] args) {
        // Build the SDK auto-configuration extension module
        OpenTelemetrySdk openTelemetrySdk = AutoConfiguredOpenTelemetrySdk.builder()
                .setResultAsGlobal(false)
                .build()
                .getOpenTelemetrySdk();
        Application.openTelemetry = openTelemetrySdk;

        // Set GlobalLoggerProvider, which is used by Log4j2 appender
        GlobalLoggerProvider.set(openTelemetrySdk.getSdkLoggerProvider());

        // Register runtime metrics instrumentation
        BufferPools.registerObservers(openTelemetrySdk);
        Classes.registerObservers(openTelemetrySdk);
        Cpu.registerObservers(openTelemetrySdk);
        GarbageCollector.registerObservers(openTelemetrySdk);
        MemoryPools.registerObservers(openTelemetrySdk);
        Threads.registerObservers(openTelemetrySdk);

        SpringApplication.run(Application.class, args);
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