package com.example.demo;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Random;

@RestController
public class Controller {

  private static final Tracer TRACER = GlobalOpenTelemetry.getTracer(Controller.class.getName());
  private static final Meter METER = GlobalMeterProvider.get().get(Controller.class.getName());
  private static final LongHistogram histogram = METER.histogramBuilder("my-histogram").ofLongs().build();
  private static final Random RANDOM = new Random();

  // Step 1: Add a controller method that we can invoke to exercise the application
  @GetMapping("/ping")
  public String ping() {
    doWork();
    return "pong";
  }

  private void doWork() {
    var span = TRACER.spanBuilder("doWork").startSpan();
    try (var scope = span.makeCurrent()) {
      var sleepMillis = RANDOM.nextInt(50);
      Thread.sleep(sleepMillis);
      histogram.record(sleepMillis, Attributes.builder().put("key", "value").build());
    } catch (InterruptedException e) {
      throw new RuntimeException("Thread interrupted.", e);
    } finally {
      span.end();
    }
  }
}
