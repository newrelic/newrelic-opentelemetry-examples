package com.newrelic.shared;

import static com.newrelic.shared.OtlpUtil.RETRYABLE_CODES;
import static com.newrelic.shared.OtlpUtil.managedChannel;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.grpc.GrpcService;
import com.linecorp.armeria.testing.junit5.server.ServerExtension;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceResponse;
import io.opentelemetry.proto.collector.metrics.v1.MetricsServiceGrpc;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceResponse;
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.LongSumData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.trace.TestSpanData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class OtlpRetryTest {

  @RegisterExtension public static OtlpGrpcServer server = new OtlpGrpcServer();

  @Test
  void testOtlpSpanExporterRetry() {
    var channel = managedChannel("http://localhost:" + server.httpPort(), "dummy-key");
    var exporter = OtlpGrpcSpanExporter.builder().setChannel(channel).build();

    var fakeSpanData =
        List.<SpanData>of(
            TestSpanData.builder()
                .setName("name")
                .setKind(SpanKind.CLIENT)
                .setStartEpochNanos(1)
                .setEndEpochNanos(2)
                .setStatus(StatusData.ok())
                .setHasEnded(true)
                .build());
    validateRetryableStatusCodes(
        OtlpGrpcSpanExporter.class.getSimpleName(), () -> exporter.export(fakeSpanData));
  }

  @Test
  void testOtlpMetricExporterRetry() {
    var channel = managedChannel("http://localhost:" + server.httpPort(), "dummy-key");
    var exporter = OtlpGrpcMetricExporter.builder().setChannel(channel).build();

    long startNs = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis());
    var fakeMetricData =
        List.of(
            MetricData.createLongSum(
                Resource.empty(),
                InstrumentationLibraryInfo.empty(),
                "name",
                "description",
                "1",
                LongSumData.create(
                    /* isMonotonic= */ true,
                    AggregationTemporality.CUMULATIVE,
                    List.of(
                        LongPointData.create(
                            startNs,
                            startNs + TimeUnit.MICROSECONDS.toNanos(900),
                            Attributes.of(stringKey("k"), "v"),
                            5)))));
    validateRetryableStatusCodes(
        OtlpGrpcMetricExporter.class.getSimpleName(), () -> exporter.export(fakeMetricData));
  }

  // TODO: add LogExporter test once available

  void validateRetryableStatusCodes(
      String exporterName, Supplier<CompletableResultCode> exportResultSupplier) {
    for (var code : Status.Code.values()) {
      server.requests.clear();
      server.responses.clear();

      // Respond with the code, then OK
      server.responses.add(code);
      server.responses.add(Status.Code.OK);

      var result = exportResultSupplier.get();

      var retryable = RETRYABLE_CODES.contains(code);
      var expectedExportResult = retryable || code == Status.Code.OK;
      assertEquals(
          expectedExportResult,
          result.join(10, TimeUnit.SECONDS).isSuccess(),
          String.format(
              "While exporting from %s, expected code %s to have export result %s.",
              exporterName, code.name(), expectedExportResult));
      var expectedRequests = retryable ? 2 : 1;
      assertEquals(
          expectedRequests,
          server.requests.size(),
          String.format(
              "While exporting from %s, expected code %s to send %s requests but sent %s.",
              exporterName, code.name(), expectedRequests, server.requests.size()));
    }
  }

  private static class OtlpGrpcServer extends ServerExtension {
    private final Queue<Object> requests = new ArrayDeque<>();
    private final Queue<Status.Code> responses = new ArrayDeque<>();

    @Override
    protected void configure(ServerBuilder sb) {
      sb.service(
          GrpcService.builder()
              .addService(
                  new TraceServiceGrpc.TraceServiceImplBase() {
                    @Override
                    public void export(
                        ExportTraceServiceRequest request,
                        StreamObserver<ExportTraceServiceResponse> responseObserver) {
                      OtlpGrpcServer.this.export(
                          request,
                          responseObserver,
                          ExportTraceServiceResponse.getDefaultInstance());
                    }
                  })
              .addService(
                  new MetricsServiceGrpc.MetricsServiceImplBase() {
                    @Override
                    public void export(
                        ExportMetricsServiceRequest request,
                        StreamObserver<ExportMetricsServiceResponse> responseObserver) {
                      OtlpGrpcServer.this.export(
                          request,
                          responseObserver,
                          ExportMetricsServiceResponse.getDefaultInstance());
                    }
                  })
              .build());
    }

    private <REQUEST, RESPONSE> void export(
        REQUEST request,
        StreamObserver<RESPONSE> responseStreamObserver,
        RESPONSE defaultResponse) {
      requests.add(request);
      Status.Code statusCode = Optional.ofNullable(responses.poll()).orElse(Status.Code.OK);
      if (statusCode == Status.Code.OK) {
        responseStreamObserver.onNext(defaultResponse);
        responseStreamObserver.onCompleted();
      } else {
        responseStreamObserver.onError(new StatusException(Status.fromCode(statusCode)));
      }
    }
  }
}
