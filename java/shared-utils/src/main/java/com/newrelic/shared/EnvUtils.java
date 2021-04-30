package com.newrelic.shared;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public class EnvUtils {

  public static <T> Supplier<T> getEnvOrDefault(
      String key, Function<String, T> transformer, T defaultValue) {
    return () ->
        Optional.ofNullable(System.getenv(key))
            .filter(s -> !s.isBlank() && !s.isEmpty())
            .map(transformer)
            .orElse(defaultValue);
  }
}
