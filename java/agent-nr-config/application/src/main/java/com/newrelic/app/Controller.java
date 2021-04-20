package com.newrelic.app;

import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import java.util.Random;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {

  private static final Meter METER = GlobalMeterProvider.getMeter(Application.class.getName());
  private static final LongCounter MY_COUNTER =
      METER.longCounterBuilder("my-custom-counter").build();

  @GetMapping("/ping")
  public String ping() {
    MY_COUNTER.add(new Random().nextInt(1000));
    return "pong";
  }
}
