package com.newrelic.shared;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;
import static io.grpc.Status.Code.ABORTED;
import static io.grpc.Status.Code.CANCELLED;
import static io.grpc.Status.Code.DATA_LOSS;
import static io.grpc.Status.Code.DEADLINE_EXCEEDED;
import static io.grpc.Status.Code.OUT_OF_RANGE;
import static io.grpc.Status.Code.RESOURCE_EXHAUSTED;
import static io.grpc.Status.Code.UNAVAILABLE;
import static java.util.stream.Collectors.toList;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.stub.MetadataUtils;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.proto.collector.logs.v1.LogsServiceGrpc;
import io.opentelemetry.proto.collector.metrics.v1.MetricsServiceGrpc;
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OtlpUtil {

  static final List<Status.Code> RETRYABLE_CODES =
      List.of(
          CANCELLED,
          DEADLINE_EXCEEDED,
          RESOURCE_EXHAUSTED,
          ABORTED,
          OUT_OF_RANGE,
          UNAVAILABLE,
          DATA_LOSS);

  /**
   * Build a managed channel with retryable status codes configured in alignment with the OTLP
   * specification. Retry configuration is not directly supported in {@link OtlpGrpcSpanExporter} or
   * {@link OtlpGrpcMetricExporter} so we must use escape hatch of providing {@link ManagedChannel}.
   */
  static ManagedChannel managedChannel(String endpointStr, String apiKey) {
    URI endpoint;
    try {
      endpoint = new URI(endpointStr);
    } catch (URISyntaxException e) {
      throw new IllegalStateException("Invalid endpoint, must be a URL: " + endpointStr, e);
    }
    if (endpoint.getScheme() == null
        || (!endpoint.getScheme().equals("http") && !endpoint.getScheme().equals("https"))) {
      throw new IllegalArgumentException(
          "Invalid endpoint, must start with http:// or https://: " + endpoint);
    }

    ManagedChannelBuilder<?> managedChannelBuilder =
        ManagedChannelBuilder.forTarget(endpoint.getAuthority());

    if (endpoint.getScheme().equals("https")) {
      managedChannelBuilder.useTransportSecurity();
    } else {
      managedChannelBuilder.usePlaintext();
    }

    Metadata metadata = new Metadata();
    metadata.put(Metadata.Key.of("api-key", ASCII_STRING_MARSHALLER), apiKey);
    managedChannelBuilder.intercept(MetadataUtils.newAttachHeadersInterceptor(metadata));

    var serviceConfig = retryServiceConfig();
    managedChannelBuilder.defaultServiceConfig(serviceConfig);
    managedChannelBuilder.enableRetry();

    return managedChannelBuilder.build();
  }

  /**
   * Return a service config that specifies retryable status codes according to the OTLP
   * specification.
   *
   * @see <a
   *     href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/protocol/otlp.md">OTLP
   *     Specification</a>
   * @see <a
   *     href="https://github.com/grpc/proposal/blob/master/A6-client-retries.md#integration-with-service-config">gRPC
   *     Retry Design</a>
   */
  static Map<String, ?> retryServiceConfig() {
    return Map.of(
        "methodConfig",
        List.of(
            methodConfigForService(
                List.of(
                    TraceServiceGrpc.SERVICE_NAME,
                    MetricsServiceGrpc.SERVICE_NAME,
                    LogsServiceGrpc.SERVICE_NAME))));
  }

  private static Map<String, ?> methodConfigForService(List<String> serviceNames) {
    var retryableStatusCodes =
        RETRYABLE_CODES.stream().map(Status.Code::value).map(i -> (double) i).collect(toList());

    var retryPolicy = new HashMap<String, Object>();
    retryPolicy.put("maxAttempts", (double) 5);
    retryPolicy.put("initialBackoff", "0.5s");
    retryPolicy.put("maxBackoff", "30s");
    retryPolicy.put("backoffMultiplier", (double) 2);
    retryPolicy.put("retryableStatusCodes", retryableStatusCodes);

    var name =
        serviceNames.stream().map(serviceName -> Map.of("service", serviceName)).collect(toList());

    return Map.of("name", name, "retryPolicy", retryPolicy);
  }
}
