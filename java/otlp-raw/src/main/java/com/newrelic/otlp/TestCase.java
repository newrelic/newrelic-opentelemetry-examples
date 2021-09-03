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
    var id = UUID.randomUUID().toString();
    var normalizedName = name.replace(" ", "-").toLowerCase();
    return new TestCase<>(normalizedName, id, payloadGenerator.apply(id));
  }

  @Override
  public String toString() {
    return "TestCase{" + "name=" + name + "," + "id=" + id + "}";
  }
}
