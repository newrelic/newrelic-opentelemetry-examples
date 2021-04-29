package com.newrelic.app;

import static com.newrelic.app.Utils.randomFromList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.api.metrics.LongValueRecorder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.common.Labels;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

public class HttpGenerator implements Runnable {

  private static final Random RANDOM = new Random();
  private static final List<String> SPAN_NAMES = List.of("/user", "/role", "/permission");
  private static final List<String> METHODS = List.of("GET", "POST", "PUT", "DELETE");
  private static final List<Long> STATUS_CODES = List.of(200L, 201L, 202L, 400L, 404L, 500L);

  private final Tracer tracer;
  private final Meter meter;
  private final LongValueRecorder durationRecorder;
  private final AtomicLong runCount = new AtomicLong();

  public HttpGenerator() {
    this.tracer = GlobalOpenTelemetry.getTracer(HttpGenerator.class.getName());
    this.meter = GlobalMeterProvider.getMeter(HttpGenerator.class.getName());
    this.durationRecorder = meter.longValueRecorderBuilder("http.server.duration").build();
  }

  @Override
  public void run() {
    var spanName = randomFromList(SPAN_NAMES);
    var method = randomFromList(METHODS);
    var statusCode = randomFromList(STATUS_CODES);
    var duration = RANDOM.nextInt(1000);
    var requestContentLength = method.equals("GET") ? 0L : RANDOM.nextInt(1000);
    var responseContentLength =
        method.equals("POST") || method.equals("DELETE") ? 0L : RANDOM.nextInt(1000);

    var span =
        tracer
            .spanBuilder(spanName)
            .setAttribute(SemanticAttributes.HTTP_METHOD, method)
            .setAttribute(SemanticAttributes.HTTP_STATUS_CODE, statusCode)
            .setAttribute(SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH, requestContentLength)
            .setAttribute(SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH, responseContentLength)
            .setSpanKind(SpanKind.SERVER)
            .setNoParent()
            .startSpan();

    durationRecorder.record(
        duration,
        Labels.builder()
            .put("http.target", spanName)
            .put("http.method", method)
            .put("http.status_code", String.valueOf(statusCode))
            .build());

    try {
      Thread.sleep(duration);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(HttpGenerator.class.getSimpleName() + " interrupted.", e);
    }

    span.end();
    long count = runCount.incrementAndGet();
    if (count % 10 == 0) {
      System.out.printf("%s http spans have been produced.%n", count);
    }
  }
}
