package com.newrelic.app;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.common.Labels;
import io.opentelemetry.api.trace.Tracer;
import java.util.Random;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {

  private static final Tracer TRACER =
      GlobalOpenTelemetry.getTracerProvider().get(Application.class.getName());
  private static final Meter METER = GlobalMeterProvider.get().get(Application.class.getName());
  private final LongCounter MY_COUNTER =
      METER
          .longCounterBuilder("my-custom-counter")
          .setDescription("A counter to count things")
          .build();

  @GetMapping("/ping")
  public String ping() throws InterruptedException {
    var span = TRACER.spanBuilder("ping").setNoParent().startSpan();
    var sleepTime = new Random().nextInt(200);
    Thread.sleep(sleepTime);
    MY_COUNTER.add(sleepTime, Labels.of("path", "ping"));
    span.end();
    return "pong";
  }
}
