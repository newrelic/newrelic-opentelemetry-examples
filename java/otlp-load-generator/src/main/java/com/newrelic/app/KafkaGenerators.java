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
import java.util.concurrent.atomic.AtomicLong;

public class KafkaGenerators {

  private static final Random RANDOM = new Random();
  private static final List<String> KAFKA_TOPICS = List.of("Topic1", "Topic2", "Topic3");
  private static final String MESSAGING_SYSTEM_KAFKA = "kafka";
  private static final String MESSAGING_DESTINATION_KIND_TOPIC = "topic";

  private KafkaGenerators() {}

  private static String messagingSpanName(String destination, String operation) {
    return String.format("%s %s", destination, operation);
  }

  static class ProducerGenerator implements Runnable {

    private final Tracer tracer;
    private final AtomicLong runCount = new AtomicLong();

    ProducerGenerator() {
      this.tracer = GlobalOpenTelemetry.getTracer(KafkaGenerators.class.getName());
    }

    @Override
    public void run() {
      var duration = RANDOM.nextInt(1000);
      var topic = randomFromList(KAFKA_TOPICS);

      var span =
          tracer
              .spanBuilder(messagingSpanName(topic, "send"))
              .setAttribute(SemanticAttributes.MESSAGING_SYSTEM, MESSAGING_SYSTEM_KAFKA)
              .setAttribute(SemanticAttributes.MESSAGING_DESTINATION, topic)
              .setAttribute(
                  SemanticAttributes.MESSAGING_DESTINATION_KIND, MESSAGING_DESTINATION_KIND_TOPIC)
              .setAttribute(
                  SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES,
                  (long) RANDOM.nextInt(1000))
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
      var topic = randomFromList(KAFKA_TOPICS);
      var operation =
          randomFromList(List.of(SemanticAttributes.MessagingOperationValues.values())).getValue();

      var span =
          tracer
              .spanBuilder(messagingSpanName(topic, operation))
              .setAttribute(SemanticAttributes.MESSAGING_SYSTEM, MESSAGING_SYSTEM_KAFKA)
              .setAttribute(SemanticAttributes.MESSAGING_DESTINATION, topic)
              .setAttribute(SemanticAttributes.MESSAGING_OPERATION, operation)
              .setAttribute(
                  SemanticAttributes.MESSAGING_DESTINATION_KIND, MESSAGING_DESTINATION_KIND_TOPIC)
              .setAttribute(
                  SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES,
                  (long) RANDOM.nextInt(1000))
              .setAttribute(SemanticAttributes.MESSAGING_KAFKA_PARTITION, (long) RANDOM.nextInt(24))
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
}
