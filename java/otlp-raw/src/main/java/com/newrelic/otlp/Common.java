package com.newrelic.otlp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.UnsafeByteOperations;
import com.google.protobuf.util.JsonFormat;
import io.opentelemetry.api.internal.OtelEncodingUtils;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.TraceId;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.InstrumentationLibrary;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.sdk.trace.IdGenerator;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Common {

  // Key for unique identifier attribute
  static final String ID_KEY = "message_id";

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new Jdk8Module())
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private Common() {}

  static String serializeToProtobufJson(MessageOrBuilder request) {
    try {
      return JsonFormat.printer().print(request);
    } catch (InvalidProtocolBufferException e) {
      throw new IllegalStateException("An error occurred serializing.", e);
    }
  }

  static <T> T deserializeFromJson(String json, TypeReference<T> typeReference) {
    try {
      return MAPPER.readValue(json, typeReference);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to deserialize JSON: " + json, e);
    }
  }

  static String serializeToJson(Object object) {
    try {
      return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(object);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize to JSON", e);
    }
  }

  static Resource resource() {
    return Resource.newBuilder()
        .addAttributes(
            KeyValue.newBuilder()
                .setKey("service.name")
                .setValue(AnyValue.newBuilder().setStringValue("native-otlp-test").build())
                .build())
        .addAttributes(
            KeyValue.newBuilder()
                .setKey("telemetry.sdk.name")
                .setValue(AnyValue.newBuilder().setStringValue("opentelemetry").build())
                .build())
        .addAttributes(
            KeyValue.newBuilder()
                .setKey("instrumentation.provider")
                .setValue(AnyValue.newBuilder().setStringValue("opentelemetry").build())
                .build())
        .addAllAttributes(allTheAttributes("resource_"))
        .build();
  }

  static InstrumentationLibrary instrumentationLibrary() {
    return InstrumentationLibrary.newBuilder()
        .setName("my-instrumentation-library")
        .setVersion("foo")
        .build();
  }

  static KeyValue idAttribute(String id) {
    return KeyValue.newBuilder()
        .setKey(ID_KEY)
        .setValue(AnyValue.newBuilder().setStringValue(id).build())
        .build();
  }

  static List<KeyValue> allTheAttributes(String prefix) {
    return List.of(
        KeyValue.newBuilder()
            .setKey(prefix + "skey")
            .setValue(AnyValue.newBuilder().setStringValue("value").build())
            .build(),
        KeyValue.newBuilder()
            .setKey(prefix + "ikey")
            .setValue(AnyValue.newBuilder().setIntValue(1).build())
            .build(),
        KeyValue.newBuilder()
            .setKey(prefix + "bkey")
            .setValue(AnyValue.newBuilder().setBoolValue(true).build())
            .build(),
        KeyValue.newBuilder()
            .setKey(prefix + "dkey")
            .setValue(AnyValue.newBuilder().setDoubleValue(1.0).build())
            .build());
  }

  static ByteString traceIdByteString() {
    return toByteString(IdGenerator.random().generateTraceId(), TraceId.getLength());
  }

  static ByteString spanIdByteString() {
    return toByteString(IdGenerator.random().generateSpanId(), SpanId.getLength());
  }

  static ByteString toByteString(String str, int length) {
    return UnsafeByteOperations.unsafeWrap(OtelEncodingUtils.bytesFromBase16(str, length));
  }

  static long toEpochNano(Instant instant) {
    return TimeUnit.MILLISECONDS.toNanos(instant.toEpochMilli());
  }
}
