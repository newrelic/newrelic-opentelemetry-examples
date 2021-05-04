package com.newrelic.app;

import static com.newrelic.app.Utils.randomFromList;

import com.newrelic.shared.EnvUtils;
import com.newrelic.shared.OpenTelemetryConfig;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class Generator {

  private static final Supplier<Integer> NUM_THREADS_SUPPLIER =
      EnvUtils.getEnvOrDefault("NUM_THREADS", Integer::valueOf, 1);

  public static void main(String[] args) {
    OpenTelemetryConfig.configureGlobal("otlp-load-generator");

    var outboundGenerators =
        List.of(
            new GrpcGenerators.ClientGenerator(),
            new HttpGenerators.ClientGenerator(),
            new KafkaGenerators.ProducerGenerator());

    var inboundGenerators =
        List.of(
            new GrpcGenerators.ServerGenerator(outboundGenerators),
            new HttpGenerators.ServerGenerator(outboundGenerators),
            new KafkaGenerators.ConsumerGenerator(outboundGenerators));

    // Create some configurable number of threads.
    // Each thread runs an infinite loop where in each iteration,
    // a random generator is selected and run.
    var threads = NUM_THREADS_SUPPLIER.get();
    var executor = Executors.newFixedThreadPool(threads);
    Runtime.getRuntime().addShutdownHook(new Thread(executor::shutdown));

    for (int i = 0; i < threads; i++) {
      executor.submit(new Task(inboundGenerators));
    }
  }

  private static class Task implements Runnable {

    private final List<Runnable> inboundGenerators;

    private Task(List<Runnable> inboundGenerators) {
      this.inboundGenerators = inboundGenerators;
    }

    @Override
    public void run() {
      while (true) {
        randomFromList(inboundGenerators).run();
      }
    }
  }
}
