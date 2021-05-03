package com.newrelic.app;

import io.opentelemetry.api.trace.Span;
import java.util.List;
import java.util.Random;

public class Utils {

  private static final Random RANDOM = new Random();

  public static <T> T randomFromList(List<T> list) {
    if (list.isEmpty()) {
      throw new IllegalArgumentException("Cannot choose random of empty list.");
    }
    return list.get(RANDOM.nextInt(list.size()));
  }

  public static void safeSleep(long sleepMillis) {
    try {
      Thread.sleep(sleepMillis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Thread interrupted.", e);
    }
  }

  /**
   * Run the {@code runnable} in the scope of the {@code span}, closing the span afterwards.
   *
   * @param span the span
   * @param runnable the task to run
   */
  public static void runInSpanScope(Span span, Runnable runnable) {
    try (var scope = span.makeCurrent()) {
      runnable.run();
    } finally {
      span.end();
    }
  }
}
