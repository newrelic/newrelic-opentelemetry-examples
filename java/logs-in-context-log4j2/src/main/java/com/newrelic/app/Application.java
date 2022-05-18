package com.newrelic.app;

import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import java.util.UUID;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {

  public static void main(String[] args) {
    AutoConfiguredOpenTelemetrySdk.builder()
        .addResourceCustomizer(
            (resource, configProperties) ->
                resource.merge(
                    Resource.builder()
                        .put("service.instance.id", UUID.randomUUID().toString())
                        .build()))
        .build();

    SpringApplication.run(Application.class, args);
  }
}
