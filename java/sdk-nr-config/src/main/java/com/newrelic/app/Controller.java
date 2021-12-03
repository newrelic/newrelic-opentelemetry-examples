package com.newrelic.app;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import java.util.Random;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {

  private static final Tracer TRACER =
      GlobalOpenTelemetry.getTracerProvider().get(Application.class.getName());
  private static final Meter METER = GlobalMeterProvider.get().get(Application.class.getName());
  private final LongCounter MY_COUNTER =
      METER.counterBuilder("my-custom-counter").setDescription("A counter to count things").build();
  private static final Logger LOGGER = LogManager.getLogger(Controller.class);

  @GetMapping("/ping")
  public String ping() throws InterruptedException {
    var span = TRACER.spanBuilder("ping").startSpan();
    try (var scope = span.makeCurrent()) {
      var sleepTime = new Random().nextInt(200);
      Thread.sleep(sleepTime);
      MY_COUNTER.add(sleepTime, Attributes.of(AttributeKey.stringKey("method"), "ping"));
      LOGGER.info("A sample log message!");
      return "pong";
    } finally {
      span.end();
    }
  }
}
