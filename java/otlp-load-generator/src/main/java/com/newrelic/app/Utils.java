package com.newrelic.app;

import java.util.List;
import java.util.Random;

public class Utils {

  private static final Random RANDOM = new Random();

  public static <T> T randomFromList(List<T> list) {
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
}
