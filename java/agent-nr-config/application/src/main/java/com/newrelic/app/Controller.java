package com.newrelic.app;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.trace.Span;
import java.util.Random;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {

  private static final Logger LOGGER = LogManager.getLogger(Controller.class);

  private static final LongCounter MY_COUNTER =
      GlobalOpenTelemetry.get()
          .getMeter(Application.class.getName())
          .counterBuilder("my-custom-counter")
          .build();

  @GetMapping("/ping")
  public String ping() {
    // Demonstrate adding a custom attribute to the current span.
    Span.current().setAttribute("my-key", "my-value");

    MY_COUNTER.add(new Random().nextInt(1000));
    LOGGER.info("A sample log message!");

    // Throw an exception ~25% of the time
    if (new Random().nextInt(4) == 0) {
      throw new IllegalStateException("Error!");
    }

    return "pong";
  }
}
