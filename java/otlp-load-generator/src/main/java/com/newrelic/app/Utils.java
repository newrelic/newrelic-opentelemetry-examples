package com.newrelic.app;

import java.util.List;
import java.util.Random;

public class Utils {

  private static final Random RANDOM = new Random();

  public static <T> T randomFromList(List<T> list) {
    return list.get(RANDOM.nextInt(list.size()));
  }
}
