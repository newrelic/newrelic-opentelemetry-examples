package com.example.demo;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;

@SpringBootApplication
public class Application {

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }

  @Bean
  public OpenTelemetry openTelemetry() {
    // OpenTelemetry javaagent will install the OpenTelemetrySdk and make the instance available via
    // GlobalOpenTelemetry.get(). Set this instance as a spring bean so we can add additional manual
    // instrumentation.
    return GlobalOpenTelemetry.get();
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    // Emit a custom event once the application is ready to serve requests
    EventEmitter.create(openTelemetry(), Application.class.getName()).emit("application.started");
  }
}
