package com.newrelic.otlp;

import static com.newrelic.otlp.Common.serializeToJson;
import static com.newrelic.shared.EnvUtils.getEnvOrDefault;
import static com.newrelic.shared.EnvUtils.getOrThrow;

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
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class Application {

  private static final Supplier<String> NEW_RELIC_USER_API_KEY =
      getOrThrow("NEW_RELIC_USER_API_KEY", Function.identity());
  private static final Supplier<String> NEW_RELIC_INSIGHTS_API_KEY =
      getOrThrow("NEW_RELIC_INSIGHTS_API_KEY", Function.identity());
  private static final Supplier<String> NEW_RELIC_ACCOUNT_ID =
      getOrThrow("NEW_RELIC_ACCOUNT_ID", Function.identity());
  private static final Supplier<String> OUTPUT_DIR =
      getOrThrow("OUTPUT_DIR", Function.identity());
  private static final Supplier<String> NEW_RELIC_GRAPHQL_ENDPOINT =
      getEnvOrDefault(
          "NEW_RELIC_GRAPHQL_ENDPOINT",
          Function.identity(),
          "https://staging-api.newrelic.com/graphql");

  public static void main(String[] args) {
    new Application().run();
  }

  private final ManagedChannel managedChannel;
  private final NrqlClient nrqlClient;

  Application() {
    managedChannel = managedChannel();
    nrqlClient = nrqlClient();
  }

  void run() {
    var traces = new Traces(managedChannel);
    var metrics = new Metrics(managedChannel);
    var logs = new Logs(managedChannel);

    List.of(traces, metrics, logs).forEach(this::runContract);
  }

  <T extends MessageOrBuilder> void runContract(TestCaseProvider<T> testCaseProvider) {
    var testCases = testCaseProvider.testCases();
    for (var testCase : testCases) {
      System.out.format(
          "%n%n%nExporting %s test case %s with id: %s%n",
          testCaseProvider.getClass().getSimpleName(), testCase.name, testCase.id);
      var protobufJson = Common.serializeToProtobufJson(testCase.payload);
      System.out.format("Protobuf JSON: %n%s%n", protobufJson);
      testCaseProvider.exportGrpcProtobuf(testCase.payload);
      try {
        Thread.sleep(10000);
      } catch (InterruptedException e) {
        throw new IllegalStateException("Thread interrupted", e);
      }
      String nrqlQuery =
          String.format(
              "SELECT * FROM %s WHERE %s = '%s'",
              testCaseProvider.newRelicDataType(), Common.ID_KEY, testCase.id);
      var nrqlResults = nrqlClient.postNrql(nrqlQuery);
      var nrdbJson = serializeToJson(nrqlResults);
      System.out.format("NRDB JSON: %n%s%n", nrdbJson);
      saveResults(testCaseProvider, testCase, protobufJson, nrdbJson);
    }
  }

  private static <T extends MessageOrBuilder> void saveResults(
      TestCaseProvider<T> testCaseProvider, TestCase<T> testCase, String protobufJson, String nrdbJson) {
    var dirString = OUTPUT_DIR.get();
    var dir = Paths.get(dirString).toFile();
    if (!dir.exists()) {
      dir.mkdir();
    }
    var normalizedTestName = testCase.name.replace(" ", "-").toLowerCase();
    var protoFilename =
        String.format(
            "%s-%s-proto.json", testCaseProvider.newRelicDataType().toLowerCase(), normalizedTestName);
    var nrdbFileName =
        String.format(
            "%s-%s-nrdb.json", testCaseProvider.newRelicDataType().toLowerCase(), normalizedTestName);
    writeToFile(Paths.get(dirString, protoFilename), protobufJson);
    writeToFile(Paths.get(dirString, nrdbFileName), nrdbJson);
  }

  private static void writeToFile(Path path, String content) {
    try {
      Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new IllegalStateException(
          "Failed to write to file " + path.toAbsolutePath(), e);
    }
  }

  private NrqlClient nrqlClient() {
    return new NrqlClient(NEW_RELIC_GRAPHQL_ENDPOINT.get(), NEW_RELIC_ACCOUNT_ID.get(), NEW_RELIC_USER_API_KEY.get());
  }

  private ManagedChannel managedChannel() {
    var managedChannelBuilder = ManagedChannelBuilder.forTarget("staging-otlp.nr-data.net:4317");
    managedChannelBuilder.useTransportSecurity();
    var metadata = new Metadata();
    metadata.put(
        Metadata.Key.of("api-key", Metadata.ASCII_STRING_MARSHALLER),
        NEW_RELIC_INSIGHTS_API_KEY.get());
    managedChannelBuilder.intercept(MetadataUtils.newAttachHeadersInterceptor(metadata));
    return managedChannelBuilder.build();
  }
}
