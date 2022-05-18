package com.newrelic.otlp;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class EnvUtils {

  private static final Predicate<String> NOT_EMPTY = s -> !s.isBlank() && !s.isEmpty();

  public static <T> Supplier<T> getEnvOrDefault(
      String key, Function<String, T> transformer, T defaultValue) {
    return () -> envOrSystemProperty(key).map(transformer).orElse(defaultValue);
  }

  public static <T> Supplier<T> getOrThrow(String key, Function<String, T> transformer) {
    return () ->
        envOrSystemProperty(key)
            .map(transformer)
            .orElseThrow(() -> new IllegalStateException("Missing environment variable " + key));
  }

  private static Optional<String> envOrSystemProperty(String key) {
    return Optional.ofNullable(System.getenv(key))
        .filter(NOT_EMPTY)
        .or(() -> Optional.ofNullable(System.getProperty(key)))
        .filter(NOT_EMPTY);
  }
}
