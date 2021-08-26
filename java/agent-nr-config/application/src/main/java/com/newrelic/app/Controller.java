package com.newrelic.app;

import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import java.util.Random;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {

  private static final Meter METER = GlobalMeterProvider.get().get(Application.class.getName());
  private static final LongCounter MY_COUNTER = METER.counterBuilder("my-custom-counter").build();

  @GetMapping("/ping")
  public String ping() {
    // Demonstrate adding a custom attribute to the current span.
    Span.current().setAttribute("my-key", "my-value");

    MY_COUNTER.add(new Random().nextInt(1000));
    return "pong";
  }
}
