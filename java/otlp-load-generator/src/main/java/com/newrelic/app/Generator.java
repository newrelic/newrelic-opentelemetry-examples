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
    new Generator().run();
  }

  private final List<Runnable> generators;

  private Generator() {
    this.generators = List.of(new HttpGenerator(), new GrpcGenerator());
  }

  private void run() {
    // Create some configurable number of threads.
    // Each thread runs an infinite loop where in each iteration,
    // a random generator is selected and run.
    var threads = NUM_THREADS_SUPPLIER.get();
    var executor = Executors.newFixedThreadPool(threads);
    Runtime.getRuntime().addShutdownHook(new Thread(executor::shutdown));

    for (int i = 0; i < threads; i++) {
      executor.submit(new Task());
    }
  }

  private class Task implements Runnable {

    @Override
    public void run() {
      while (true) {
        try {
          randomFromList(generators).run();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
  }
}
