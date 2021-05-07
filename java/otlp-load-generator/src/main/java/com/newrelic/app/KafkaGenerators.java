package com.newrelic.app;

import static com.newrelic.app.Utils.randomFromList;
import static com.newrelic.app.Utils.runInSpanScope;
import static com.newrelic.app.Utils.safeSleep;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public class KafkaGenerators {

  private static final Random RANDOM = new Random();
  private static final List<String> TOPICS = List.of("Topic1", "Topic2", "Topic3");
  private static final List<String> MESSAGE_KEYS = List.of("myKey", "anotherKey");
  private static final List<UUID> KAFKA_CLIENT_IDS =
      List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
  private static final String KAFKA_CONSUMER_GROUP = "otlp-load-generator";
  private static final String MESSAGING_SYSTEM_KAFKA = "kafka";
  private static final String MESSAGING_DESTINATION_KIND_TOPIC = "topic";

  private KafkaGenerators() {}

  static class ProducerGenerator implements Runnable {

    private final Tracer tracer;
    private final AtomicLong runCount = new AtomicLong();

    ProducerGenerator() {
      this.tracer = GlobalOpenTelemetry.getTracer(KafkaGenerators.class.getName());
    }

    @Override
    public void run() {
      var duration = RANDOM.nextInt(1000);
      var km = randomKafkaMessage();

      var span =
          tracer
              .spanBuilder(messagingSpanName(km.topic, "send"))
              .setAttribute(SemanticAttributes.MESSAGING_SYSTEM, MESSAGING_SYSTEM_KAFKA)
              .setAttribute(SemanticAttributes.MESSAGING_DESTINATION, km.topic)
              .setAttribute(
                  SemanticAttributes.MESSAGING_DESTINATION_KIND, MESSAGING_DESTINATION_KIND_TOPIC)
              .setAttribute(SemanticAttributes.MESSAGING_KAFKA_MESSAGE_KEY, km.messageKey)
              .setAttribute(SemanticAttributes.MESSAGING_KAFKA_PARTITION, km.partition)
              .setSpanKind(SpanKind.PRODUCER)
              .startSpan();

      runInSpanScope(
          span,
          () -> {
            safeSleep(duration);
            span.setStatus(StatusCode.OK);

            long count = runCount.incrementAndGet();
            if (count % 10 == 0) {
              System.out.printf("%s kafka producer spans have been produced.%n", count);
            }
          });
    }
  }

  static class ConsumerGenerator implements Runnable {

    private final List<Runnable> outboundGenerators;
    private final Tracer tracer;
    private final AtomicLong runCount = new AtomicLong();

    ConsumerGenerator(List<Runnable> outboundGenerator) {
      this.outboundGenerators = outboundGenerator;
      this.tracer = GlobalOpenTelemetry.getTracer(ConsumerGenerator.class.getName());
    }

    @Override
    public void run() {
      var duration = RANDOM.nextInt(1000);
      var km = randomKafkaMessage();
      var operation =
          randomFromList(List.of(SemanticAttributes.MessagingOperationValues.values())).getValue();

      var span =
          tracer
              .spanBuilder(messagingSpanName(km.topic, operation))
              .setAttribute(SemanticAttributes.MESSAGING_SYSTEM, MESSAGING_SYSTEM_KAFKA)
              .setAttribute(SemanticAttributes.MESSAGING_DESTINATION, km.topic)
              .setAttribute(
                  SemanticAttributes.MESSAGING_DESTINATION_KIND, MESSAGING_DESTINATION_KIND_TOPIC)
              .setAttribute(SemanticAttributes.MESSAGING_OPERATION, operation)
              .setAttribute(SemanticAttributes.MESSAGING_KAFKA_MESSAGE_KEY, km.messageKey)
              .setAttribute(SemanticAttributes.MESSAGING_KAFKA_CONSUMER_GROUP, KAFKA_CONSUMER_GROUP)
              .setAttribute(
                  SemanticAttributes.MESSAGING_KAFKA_CLIENT_ID,
                  randomFromList(KAFKA_CLIENT_IDS).toString())
              .setAttribute(SemanticAttributes.MESSAGING_KAFKA_PARTITION, km.partition)
              .setAttribute(SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES, km.payloadSize)
              .setSpanKind(SpanKind.CONSUMER)
              .setNoParent()
              .startSpan();

      runInSpanScope(
          span,
          () -> {
            safeSleep(duration);
            randomFromList(outboundGenerators).run();
            span.setStatus(StatusCode.OK);

            long count = runCount.incrementAndGet();
            if (count % 10 == 0) {
              System.out.printf("%s kafka consumer spans have been produced.%n", count);
            }
          });
    }
  }

  private static String messagingSpanName(String destination, String operation) {
    return String.format("%s %s", destination, operation);
  }

  private static KafkaMessage randomKafkaMessage() {
    var km = new KafkaMessage();

    km.topic = randomFromList(TOPICS);
    km.messageKey = randomFromList(MESSAGE_KEYS);
    km.payloadSize = RANDOM.nextInt(1000);
    km.partition = RANDOM.nextInt(24);

    return km;
  }

  private static class KafkaMessage {
    private String topic; // MyTopic
    private String messageKey; // myKey
    private long payloadSize; // e.g. 1000
    private long partition; // e.g. 5
  }
}
