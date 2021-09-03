package com.newrelic.otlp;

import java.util.UUID;
import java.util.function.Function;

class TestCase<T> {

  final String name;
  final String id;
  final T payload;

  private TestCase(String name, String id, T payload) {
    this.name = name;
    this.id = id;
    this.payload = payload;
  }

  static <T> TestCase<T> of(String name, Function<String, T> payloadGenerator) {
    String id = UUID.randomUUID().toString();
    return new TestCase<>(name, id, payloadGenerator.apply(id));
  }
}
