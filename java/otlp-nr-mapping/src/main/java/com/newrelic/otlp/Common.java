package com.newrelic.otlp;

import static java.util.stream.Collectors.toList;

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
import io.opentelemetry.proto.common.v1.ArrayValue;
import io.opentelemetry.proto.common.v1.InstrumentationScope;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.common.v1.KeyValueList;
import io.opentelemetry.proto.resource.v1.Resource;
import io.opentelemetry.sdk.trace.IdGenerator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class Common {

  // Key for unique identifier attribute
  static final String ID_KEY = "message_id";

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .registerModule(new Jdk8Module())
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

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

  static String obfuscateJson(String json, Set<String> fields) throws JsonProcessingException {
    Object deserialized = MAPPER.readValue(json, Object.class);
    Object obfuscated = obfuscateObject(deserialized, fields);
    return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obfuscated);
  }

  private static Object obfuscateObject(Object object, Set<String> fields) {
    if (object instanceof List) {
      List<Object> response = new ArrayList<>();
      List<?> list = (List<?>) object;
      for (Object entry : list) {
        response.add(obfuscateObject(entry, fields));
      }
      return response;
    }
    if (object instanceof Map) {
      Map<Object, Object> response = new HashMap<>();
      Map<?, ?> map = new HashMap<>((Map<?, ?>) object);
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        Object key = entry.getKey();
        Object value = entry.getValue();
        if (fields.contains(key)) {
          Object obfuscatedValue = null;
          if (value instanceof Number) {
            obfuscatedValue = 0;
          } else if (value instanceof Boolean) {
            obfuscatedValue = false;
          } else if (value instanceof String) {
            obfuscatedValue = "OBFUSCATED";
          }
          response.put(key, obfuscatedValue);
        } else {
          response.put(key, obfuscateObject(value, fields));
        }
      }
      return response;
    }
    return object;
  }

  static Resource obfuscateResource(Resource original, Set<String> attributeKeys) {
    var attributes = original.getAttributesList();
    return original.toBuilder()
        .clearAttributes()
        .addAllAttributes(obfuscateKeyValues(attributes, attributeKeys))
        .build();
  }

  static List<KeyValue> obfuscateKeyValues(List<KeyValue> original, Set<String> attributeKeys) {
    return original.stream()
        .map(
            keyValue -> {
              var key = keyValue.getKey();
              if (!attributeKeys.contains(key)) {
                return keyValue;
              }
              var value = keyValue.getValue();
              var builder = KeyValue.newBuilder().setKey(key);
              if (value.hasArrayValue()) {
                builder.setValue(
                    AnyValue.newBuilder().setArrayValue(ArrayValue.newBuilder().build()).build());
              }
              if (value.hasBoolValue()) {
                builder.setValue(AnyValue.newBuilder().setBoolValue(false).build());
              }
              if (value.hasStringValue()) {
                builder.setValue(AnyValue.newBuilder().setStringValue("OBFUSCATED").build());
              }
              if (value.hasIntValue()) {
                builder.setValue(AnyValue.newBuilder().setIntValue(0).build());
              }
              if (value.hasIntValue()) {
                builder.setValue(AnyValue.newBuilder().setIntValue(0).build());
              }
              if (value.hasDoubleValue()) {
                builder.setValue(AnyValue.newBuilder().setDoubleValue(0.0).build());
              }
              if (value.hasKvlistValue()) {
                builder.setValue(
                    AnyValue.newBuilder()
                        .setKvlistValue(KeyValueList.newBuilder().build())
                        .build());
              }
              if (value.hasBytesValue()) {
                builder
                    .setValue(AnyValue.newBuilder().setBytesValue(ByteString.EMPTY).build())
                    .build();
              }
              return builder.build();
            })
        .collect(toList());
  }

  static Resource.Builder resource() {
    return Resource.newBuilder()
        .addAttributes(
            KeyValue.newBuilder()
                .setKey("service.name")
                .setValue(AnyValue.newBuilder().setStringValue("native-otlp-test").build())
                .build());
  }

  static InstrumentationScope instrumentationScope() {
    return InstrumentationScope.newBuilder()
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
            .build(),
        KeyValue.newBuilder()
            .setKey(prefix + "sarrkey")
            .setValue(
                AnyValue.newBuilder()
                    .setArrayValue(
                        ArrayValue.newBuilder()
                            .addValues(AnyValue.newBuilder().setStringValue("value1").build())
                            .addValues(AnyValue.newBuilder().setStringValue("value2").build())
                            .build()))
            .build(),
        KeyValue.newBuilder()
            .setKey(prefix + "iarrkey")
            .setValue(
                AnyValue.newBuilder()
                    .setArrayValue(
                        ArrayValue.newBuilder()
                            .addValues(AnyValue.newBuilder().setIntValue(1).build())
                            .addValues(AnyValue.newBuilder().setIntValue(2).build())
                            .build()))
            .build(),
        KeyValue.newBuilder()
            .setKey(prefix + "barrkey")
            .setValue(
                AnyValue.newBuilder()
                    .setArrayValue(
                        ArrayValue.newBuilder()
                            .addValues(AnyValue.newBuilder().setBoolValue(true).build())
                            .addValues(AnyValue.newBuilder().setBoolValue(false).build())
                            .build()))
            .build(),
        KeyValue.newBuilder()
            .setKey(prefix + "darrkey")
            .setValue(
                AnyValue.newBuilder()
                    .setArrayValue(
                        ArrayValue.newBuilder()
                            .addValues(AnyValue.newBuilder().setDoubleValue(1.0).build())
                            .addValues(AnyValue.newBuilder().setDoubleValue(2.0).build())
                            .build()))
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
