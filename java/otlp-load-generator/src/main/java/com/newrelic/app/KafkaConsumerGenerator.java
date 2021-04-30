package com.newrelic.app;

import static com.newrelic.app.Utils.randomFromList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

public class KafkaConsumerGenerator implements Runnable {

  private static final Random RANDOM = new Random();
  private static final List<String> TOPICS = List.of("Topic1", "Topic2", "Topic3");
  private static final List<String> OPERATIONS = List.of("receive", "process");
  private static final String MESSAGING_SYSTEM = "kafka";
  private static final String MESSAGING_DESTINATION_KIND = "topic";

  private final Tracer tracer;
  private final AtomicLong runCount = new AtomicLong();

  public KafkaConsumerGenerator() {
    this.tracer = GlobalOpenTelemetry.getTracer(KafkaConsumerGenerator.class.getName());
  }

  @Override
  public void run() {
    var duration = RANDOM.nextInt(1000);
    var topic = randomFromList(TOPICS);
    var operation = randomFromList(OPERATIONS);
    var spanName = String.format("%s %s", topic, operation);

    var span =
        tracer
            .spanBuilder(spanName)
            .setAttribute(SemanticAttributes.MESSAGING_SYSTEM, MESSAGING_SYSTEM)
            .setAttribute(SemanticAttributes.MESSAGING_DESTINATION, topic)
            .setAttribute(SemanticAttributes.MESSAGING_OPERATION, operation)
            .setAttribute(SemanticAttributes.MESSAGING_DESTINATION_KIND, MESSAGING_DESTINATION_KIND)
            .setAttribute(
                SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES,
                (long) RANDOM.nextInt(1000))
            .setAttribute(SemanticAttributes.MESSAGING_KAFKA_PARTITION, (long) RANDOM.nextInt(24))
            .setSpanKind(SpanKind.CONSUMER)
            .setNoParent()
            .startSpan();

    Utils.safeSleep(duration);

    span.setStatus(StatusCode.OK);
    span.end();
    long count = runCount.incrementAndGet();
    if (count % 10 == 0) {
      System.out.printf("%s kafka consumer spans have been produced.%n", count);
    }
  }
}
