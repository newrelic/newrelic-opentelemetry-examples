package com.newrelic.app;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Random;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {

  private static final Tracer TRACER =
      GlobalOpenTelemetry.getTracerProvider().get(Application.class.getName());
  private static final Logger logger = LogManager.getLogger(Controller.class);

  @GetMapping("/ping")
  public String ping() throws InterruptedException {
    var span =
        TRACER
            .spanBuilder("/ping")
            .setAttribute(SemanticAttributes.HTTP_METHOD, "GET")
            .setAttribute(SemanticAttributes.HTTP_SCHEME, "http")
            .setAttribute(SemanticAttributes.NET_HOST_NAME, "localhost:8080")
            .setAttribute(SemanticAttributes.HTTP_TARGET, "/ping")
            .setSpanKind(SpanKind.SERVER)
            .startSpan();
    try (var scope = span.makeCurrent()) {
      var sleepTime = new Random().nextInt(200);
      Thread.sleep(sleepTime);
      logger.info("A sample log message!");
      return "pong";
    } finally {
      span.end();
    }
  }
}
