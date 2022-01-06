package com.example.demo;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class Controller {

  private static final Tracer TRACER = GlobalOpenTelemetry.getTracer(Controller.class.getName());
  private static final Meter METER = GlobalMeterProvider.get().get(Controller.class.getName());
  private static final LongHistogram fibonacciDuration = METER.histogramBuilder("fibonacci").ofLongs().build();

  @GetMapping(value = "/fibonacci")
  public Map<String, Object> ping(@RequestParam(required = true, name = "n") long n) {
    if (n < 1 || n > 1000) {
      throw new IllegalArgumentException("n must be 1 <= n <= 1000.");
    }
    return Map.of("n", n, "result", fibonacci(n));
  }

  /**
   * Compute the fibonacci number for {@code n}.
   *
   * @param n must be >=1 and <= 1000.
   */
  private long fibonacci(long n) {
    var start = System.nanoTime();
    var span = TRACER.spanBuilder("fibonacci").startSpan();
    try (var scope = span.makeCurrent()) {
      // Base cases
      if (n == 1) {
        return 1;
      }
      if (n == 2) {
        return 1;
      }


      long lastLast = 1;
      long last = 2;
      for (long i = 4; i <= n; i++) {
        long cur = last + lastLast;
        lastLast = last;
        last = cur;
      }
      return last;
    } finally {
      fibonacciDuration.record(System.nanoTime() - start);
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
      return new ResponseEntity<>(Map.of("message", e.getMessage()), HttpStatus.BAD_REQUEST);
    }

  }
}
