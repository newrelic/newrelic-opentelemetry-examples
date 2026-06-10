package com.example.demo;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Logger;

/**
 * Thin wrapper around the OpenTelemetry logs/events API that makes emitting custom events a
 * one-liner. Obtain one with {@link #create(OpenTelemetry, String)} and call {@link #emit}.
 */
public final class EventEmitter {

  // Carried on each event as the PascalCased event name (e.g. "FibonacciComputed"). Forwarded as a
  // log attribute by the New Relic agent in hybrid mode.
  private static final AttributeKey<String> NEWRELIC_EVENT_TYPE =
      AttributeKey.stringKey("newrelic.event.type");

  private final Logger logger;

  private EventEmitter(Logger logger) {
    this.logger = logger;
  }

  /** Create an emitter whose events are scoped to {@code instrumentationScopeName}. */
  static EventEmitter create(OpenTelemetry openTelemetry, String instrumentationScopeName) {
    return new EventEmitter(openTelemetry.getLogsBridge().get(instrumentationScopeName));
  }

  /** Emit an event with no attributes. */
  void emit(String eventName) {
    emit(eventName, Attributes.empty());
  }

  /** Emit an event carrying the given attributes. */
  void emit(String eventName, Attributes attributes) {
    logger
        .logRecordBuilder()
        .setEventName(eventName)
        // The New Relic agent only forwards OpenTelemetry log records that have a body, so use the
        // event name as the body. Without it the record is dropped before reaching New Relic.
        .setBody(eventName)
        .setAllAttributes(attributes)
        .setAttribute(NEWRELIC_EVENT_TYPE, toEventType(eventName))
        .emit();
  }

  /** Convert a dot/underscore-delimited event name into a PascalCase event type. */
  private static String toEventType(String eventName) {
    StringBuilder sb = new StringBuilder(eventName.length());
    boolean capitalizeNext = true;
    for (int i = 0; i < eventName.length(); i++) {
      char c = eventName.charAt(i);
      if (c == '.' || c == '_') {
        capitalizeNext = true;
      } else if (capitalizeNext) {
        sb.append(Character.toUpperCase(c));
        capitalizeNext = false;
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }
}
