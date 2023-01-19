package com.example.demo;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;

@RestController
public class Controller {

  private static final Logger LOGGER = LogManager.getLogger(Controller.class);

  private static final AttributeKey<Long> ATTR_N = AttributeKey.longKey("fibonacci.n");
  private static final AttributeKey<Long> ATTR_RESULT = AttributeKey.longKey("fibonacci.result");
  private static final AttributeKey<Boolean> ATTR_VALID_N =
      AttributeKey.booleanKey("fibonacci.valid.n");

  private final Tracer tracer;
  private final LongCounter fibonacciInvocations;

  @Autowired
  Controller(OpenTelemetry openTelemetry) {
    tracer = openTelemetry.getTracer(Controller.class.getName());
    Meter meter = openTelemetry.getMeter(Controller.class.getName());
    fibonacciInvocations =
        meter
            .counterBuilder("fibonacci.invocations")
            .setDescription("Measures the number of times the fibonacci method is invoked.")
            .build();
  }

  @GetMapping(value = "/fibonacci")
  public Map<String, Object> getFibonacci(@RequestParam(required = true, name = "n") long n) {
    return Map.of("n", n, "result", fibonacci(n));
  }

  /**
   * Compute the fibonacci number for {@code n}.
   *
   * @param n must be >=1 and <= 90.
   */
  private long fibonacci(long n) {
    // Start a new span and set your first attribute
    var span = tracer.spanBuilder("fibonacci").setAttribute(ATTR_N, n).startSpan();

    try (var scope = span.makeCurrent()) {
      if (n < 1 || n > 90) {
        throw new IllegalArgumentException("n must be 1 <= n <= 90.");
      }

      long result = 1;
      if (n > 2) {
        long a = 0;
        long b = 1;

        for (long i = 1; i < n; i++) {
          result = a + b;
          a = b;
          b = result;
        }
      }

      span.setAttribute(ATTR_RESULT, result);
      fibonacciInvocations.add(1, Attributes.of(ATTR_VALID_N, true));
      LOGGER.info("Compute fibonacci(" + n + ") = " + result);
      return result;
    } catch (IllegalArgumentException e) {
      span.recordException(e).setStatus(StatusCode.ERROR, e.getMessage());
      fibonacciInvocations.add(1, Attributes.of(ATTR_VALID_N, false));
      LOGGER.info("Failed to compute fibonacci(" + n + ")");
      throw e;
    } finally {
      // End the span
      span.end();
    }
  }

  @ControllerAdvice
  private static class ErrorHandler {

    @ExceptionHandler({
      IllegalArgumentException.class,
      MissingServletRequestParameterException.class,
      HttpRequestMethodNotSupportedException.class
    })
    public ResponseEntity<Object> handleException(Exception e) {
      // Set the span status and description
      Span.current().setStatus(StatusCode.ERROR, e.getMessage());
      return new ResponseEntity<>(Map.of("message", e.getMessage()), HttpStatus.BAD_REQUEST);
    }
  }
}
