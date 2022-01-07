package com.example.demo;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.spring.webmvc.SpringWebMvcTracing;
import io.opentelemetry.sdk.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import javax.servlet.Filter;

@SpringBootApplication
public class Application {

  public static void main(String[] args) {
    OpenTelemetrySdkAutoConfiguration.initialize();

    SpringApplication.run(Application.class, args);
  }

  @Bean
  public Filter webMvcTracingFilter() {
    return SpringWebMvcTracing.create(GlobalOpenTelemetry.get()).newServletFilter();
  }

}
