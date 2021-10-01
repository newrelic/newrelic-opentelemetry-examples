package com.newrelic.otlp;

import static com.newrelic.otlp.Common.obfuscateJson;
import static com.newrelic.otlp.Common.serializeToJson;
import static com.newrelic.shared.EnvUtils.getEnvOrDefault;
import static com.newrelic.shared.EnvUtils.getOrThrow;
import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.protobuf.MessageOrBuilder;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;
import okhttp3.HttpUrl;

public class Application {

  private static final Supplier<String> NEW_RELIC_USER_API_KEY =
      getOrThrow("NEW_RELIC_USER_API_KEY", Function.identity());
  private static final Supplier<String> NEW_RELIC_LICENSE_KEY =
      getOrThrow("NEW_RELIC_LICENSE_KEY", Function.identity());
  private static final Supplier<String> NEW_RELIC_ACCOUNT_ID =
      getOrThrow("NEW_RELIC_ACCOUNT_ID", Function.identity());
  private static final Supplier<String> OUTPUT_DIR = getOrThrow("OUTPUT_DIR", Function.identity());
  private static final Supplier<String> NEW_RELIC_GRAPHQL_ENDPOINT =
      getEnvOrDefault(
          "NEW_RELIC_GRAPHQL_ENDPOINT",
          Function.identity(),
          "https://staging-api.newrelic.com/graphql");
  private static final Supplier<String> NEW_RELIC_OTLP_ENDPOINT =
      getEnvOrDefault("OTEL_HOST", Function.identity(), "https://staging-otlp.nr-data.net:4317");
  private static final Supplier<Boolean> OBFUSCATE_OUTPUT =
      getEnvOrDefault("OBFUSCATE_OUTPUT", Boolean::parseBoolean, true);
  private static final Supplier<Integer> INGEST_WAIT_SECONDS =
      getEnvOrDefault("INGEST_WAIT_SECONDS", Integer::valueOf, 20);

  public static void main(String[] args) {
    new Application().run();
  }

  private final ManagedChannel managedChannel;
  private final NrqlClient nrqlClient;
  private final List<TestCaseProvider<?>> testCaseProviders;
  private final ExecutorService nrqlExecutor = Executors.newFixedThreadPool(5);

  Application() {
    managedChannel = managedChannel();
    nrqlClient = nrqlClient();
    this.testCaseProviders =
        List.of(new Traces(managedChannel), new Metrics(managedChannel), new Logs(managedChannel));
  }

  void run() {
    resetOutputDir();
    var testFutures =
        testCaseProviders.stream()
            .flatMap(testCaseProvider -> runTests(testCaseProvider).stream())
            .toArray(CompletableFuture[]::new);
    CompletableFuture.allOf(testFutures).join();
    nrqlExecutor.shutdown();
  }

  <T extends MessageOrBuilder> List<CompletableFuture<?>> runTests(
      TestCaseProvider<T> testCaseProvider) {
    return testCaseProvider.testCases().stream()
        .map(testCase -> runTest(testCaseProvider, testCase))
        .collect(toList());
  }

  <T extends MessageOrBuilder> CompletableFuture<?> runTest(
      TestCaseProvider<T> testCaseProvider, TestCase<T> testCase) {
    System.out.format(
        "Starting protobuf export for %s %s%n", testCaseProvider.newRelicDataType(), testCase);
    try {
      testCaseProvider.exportGrpcProtobuf(testCase.payload);
      System.out.format(
          "Finished protobuf export for %s %s%n",
          testCaseProvider.newRelicDataType(), testCase.name);
    } catch (Exception e) {
      System.out.format(
          "An error occurred during protobuf export for %s %s: %s%n",
          testCaseProvider.newRelicDataType(), testCase.name, e.getMessage());
    }
    var runNrqlAfter = Instant.now().plusSeconds(INGEST_WAIT_SECONDS.get());
    return CompletableFuture.supplyAsync(
            () -> fetchNrqlResults(testCaseProvider, testCase, runNrqlAfter), nrqlExecutor)
        .thenAccept(
            nrqlJson -> {
              System.out.format(
                  "Saving results for %s %s%n", testCaseProvider.newRelicDataType(), testCase);
              T payload = testCase.payload;
              if (OBFUSCATE_OUTPUT.get()) {
                payload = testCaseProvider.obfuscateAttributeKeys(payload, Set.of(Common.ID_KEY));
              }
              var protobufJson = Common.serializeToProtobufJson(payload);
              saveTestResults(testCaseProvider, testCase, protobufJson, nrqlJson);
            });
  }

  private <T extends MessageOrBuilder> String fetchNrqlResults(
      TestCaseProvider<T> testCaseProvider, TestCase<T> testCase, Instant runNrqlAfter) {
    System.out.format(
        "Waiting until time to run nrql query for %s %s%n",
        testCaseProvider.newRelicDataType(), testCase);
    while (Instant.now().isBefore(runNrqlAfter)) {
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        throw new IllegalStateException("Thread interrupted.", e);
      }
    }

    System.out.format(
        "Starting nrql query for %s %s%n", testCaseProvider.newRelicDataType(), testCase);
    var nrqlQuery =
        String.format(
            "SELECT * FROM %s WHERE %s = '%s'",
            testCaseProvider.newRelicDataType(), Common.ID_KEY, testCase.id);
    var nrqlResults = nrqlClient.postNrql(nrqlQuery);
    System.out.format(
        "Finished nrql query for %s %s%n", testCaseProvider.newRelicDataType(), testCase);
    return serializeToJson(nrqlResults);
  }

  private void resetOutputDir() {
    var outputDir = Paths.get(OUTPUT_DIR.get()).toFile();
    if (!outputDir.exists()) {
      outputDir.mkdir();
    }
    testCaseProviders.forEach(
        testCaseProvider -> {
          var dir =
              Paths.get(OUTPUT_DIR.get(), testCaseProvider.newRelicDataType().toLowerCase())
                  .toFile();
          if (dir.exists()) {
            var files = dir.listFiles();
            if (files != null && files.length > 0) {
              for (var file : files) {
                file.delete();
              }
            }
            dir.delete();
          }
          dir.mkdir();
        });
  }

  private static <T extends MessageOrBuilder> void saveTestResults(
      TestCaseProvider<T> testCaseProvider,
      TestCase<T> testCase,
      String protobufJson,
      String nrdbJson) {
    var outputDir = OUTPUT_DIR.get();
    var testCaseDir = testCaseProvider.newRelicDataType().toLowerCase();
    var protoFilename = String.format("%s-proto.json", testCase.name);
    var nrdbFileName = String.format("%s-nrdb.json", testCase.name);
    var obfuscatedProtobufJson = maybeObfuscateProtbufJson(protobufJson);
    var obfuscatedNrdbJson = maybeObfuscateNrdbJson(nrdbJson);
    writeToFile(Paths.get(outputDir, testCaseDir, protoFilename), obfuscatedProtobufJson);
    writeToFile(Paths.get(outputDir, testCaseDir, nrdbFileName), obfuscatedNrdbJson);
  }

  private static String maybeObfuscateNrdbJson(String nrdbJson) {
    if (!OBFUSCATE_OUTPUT.get()) {
      return nrdbJson;
    }
    try {
      return obfuscateJson(
          nrdbJson,
          Set.of(
              "entity.guid",
              "entityGuid",
              "message_id",
              "timestamp",
              "guid",
              "parent.id",
              "parentId",
              "id",
              "trace.id",
              "traceId",
              "span.id",
              "messageId",
              "entity.guids",
              "endTimestamp",
              "newrelic.logPattern"));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Error obfuscating NRDB json.", e);
    }
  }

  private static String maybeObfuscateProtbufJson(String protobufJson) {
    if (!OBFUSCATE_OUTPUT.get()) {
      return protobufJson;
    }
    try {
      return obfuscateJson(
          protobufJson,
          Set.of(
              "startTimeUnixNano",
              "timeUnixNano",
              "timestamp",
              "traceId",
              "parentSpanId",
              "spanId",
              "endTimeUnixNano"));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Error obfuscating NRDB json.", e);
    }
  }

  private static void writeToFile(Path path, String content) {
    try {
      Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new IllegalStateException("Failed to write to file " + path.toAbsolutePath(), e);
    }
  }

  private NrqlClient nrqlClient() {
    return new NrqlClient(
        NEW_RELIC_GRAPHQL_ENDPOINT.get(), NEW_RELIC_ACCOUNT_ID.get(), NEW_RELIC_USER_API_KEY.get());
  }

  private ManagedChannel managedChannel() {
    var url = HttpUrl.parse(NEW_RELIC_OTLP_ENDPOINT.get());
    var managedChannelBuilder = ManagedChannelBuilder.forTarget(url.uri().getAuthority());
    if (url.scheme().equals("https")) {
      managedChannelBuilder.useTransportSecurity();
    } else {
      managedChannelBuilder.usePlaintext();
    }
    var metadata = new Metadata();
    metadata.put(
        Metadata.Key.of("api-key", Metadata.ASCII_STRING_MARSHALLER), NEW_RELIC_LICENSE_KEY.get());
    managedChannelBuilder.intercept(MetadataUtils.newAttachHeadersInterceptor(metadata));
    return managedChannelBuilder.build();
  }
}
