package com.newrelic.app;

import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import java.util.Random;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class Application {

  private static final Meter METER = GlobalMeterProvider.getMeter(Application.class.getName());
  private static final LongCounter COUNTER = METER.longCounterBuilder("my-custom-counter").build();

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }

  @RestController
  public static class Controller {
    @GetMapping("/ping")
    public String ping() {
      COUNTER.add(new Random().nextInt(1000));
      return "pong";
    }
  }
}
