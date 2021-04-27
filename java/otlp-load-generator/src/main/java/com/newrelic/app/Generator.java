package com.newrelic.app;

import com.newrelic.shared.OpenTelemetryConfig;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.api.metrics.common.Labels;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import java.util.Random;

public class Generator {

  private static final Random RANDOM = new Random();
  private static final List<String> TARGETS = List.of("/foo", "/bar", "/baz");
  private static final List<String> METHODS = List.of("GET", "POST", "PUT", "DELETE");
  private static final List<Long> STATUS_CODES = List.of(200L, 201L, 202L, 400L, 404L, 500L);

  public static void main(String[] args) {
    OpenTelemetryConfig.configureGlobal("otlp-load-generator");

    generateHttpServer();
  }

  private static void generateHttpServer() {
    var tracer = GlobalOpenTelemetry.getTracer(Generator.class.getName());
    var meter = GlobalMeterProvider.getMeter(Generator.class.getName());
    var durationRecorder = meter.longValueRecorderBuilder("http.server.duration").build();

    var spanCount = 0L;
    while (true) {
      var target = randomFromList(TARGETS);
      var method = randomFromList(METHODS);
      var statusCode = randomFromList(STATUS_CODES);
      var duration = RANDOM.nextInt(1000);

      var span =
          tracer
              .spanBuilder(target)
              .setAttribute(SemanticAttributes.HTTP_METHOD, method)
              .setAttribute(SemanticAttributes.HTTP_STATUS_CODE, statusCode)
              .setSpanKind(SpanKind.SERVER)
              .setNoParent()
              .startSpan();

      durationRecorder.record(
          duration,
          Labels.builder()
              .put("http.target", target)
              .put("http.method", method)
              .put("http.status_code", String.valueOf(statusCode))
              .build());

      try {
        Thread.sleep(duration);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Generator interrupted.", e);
      }

      span.end();
      spanCount++;
      if (spanCount % 10 == 0) {
        System.out.printf("%s spans have been produced.%n", spanCount);
      }
    }
  }

  private static <T> T randomFromList(List<T> list) {
    return list.get(RANDOM.nextInt(list.size()));
  }
}
