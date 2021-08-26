package com.newrelic.app;

import static com.newrelic.app.Utils.randomFromList;
import static com.newrelic.app.Utils.runInSpanScope;
import static com.newrelic.app.Utils.safeSleep;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.lang3.tuple.Pair;

public class GrpcGenerators {

  private static final Random RANDOM = new Random();
  private static final List<Pair<String, String>> SERVICE_METHODS =
      List.of(
          Pair.of("UserService", "AddUser"),
          Pair.of("UserService", "RemoveUser"),
          Pair.of("UserService", "GetUser"),
          Pair.of("RoleService", "AddRole"),
          Pair.of("RoleService", "RemoveRole"),
          Pair.of("RoleService", "GetRole"));
  private static final String RPC_SYSTEM_GRPC = "grpc";
  private static final List<String> PEER_IPS = List.of("127.0.0.1", "127.0.0.2");

  private GrpcGenerators() {}

  static class ServerGenerator implements Runnable {

    private final List<Runnable> outboundGenerators;
    private final Tracer tracer;
    private final LongHistogram duration;
    private final LongHistogram requestSizeRecorder;
    private final LongHistogram responseSizeRecorder;
    private final LongHistogram requestsPerRpcRecorder;
    private final LongHistogram responsesPerRpcRecorder;
    private final AtomicLong runCount = new AtomicLong();

    ServerGenerator(List<Runnable> outboundGenerators) {
      this.outboundGenerators = outboundGenerators;
      this.tracer = GlobalOpenTelemetry.getTracer(GrpcGenerators.class.getName());
      Meter meter = GlobalMeterProvider.get().get(GrpcGenerators.class.getName());
      this.duration = meter.histogramBuilder("rpc.server.duration").ofLongs().build();
      this.requestSizeRecorder =
          meter.histogramBuilder("rpc.server.request.size").ofLongs().build();
      this.responseSizeRecorder =
          meter.histogramBuilder("rpc.server.response.size").ofLongs().build();
      this.requestsPerRpcRecorder =
          meter.histogramBuilder("rpc.server.requests_per_rpc").ofLongs().build();
      this.responsesPerRpcRecorder =
          meter.histogramBuilder("rpc.server.responses_per_rpc").ofLongs().build();
    }

    @Override
    public void run() {
      var rr = randomGrpcRequestResponse();

      var span =
          tracer
              .spanBuilder(spanName(rr))
              .setAttribute(SemanticAttributes.RPC_SYSTEM, RPC_SYSTEM_GRPC)
              .setAttribute(SemanticAttributes.RPC_SERVICE, rr.service())
              .setAttribute(SemanticAttributes.RPC_METHOD, rr.method)
              .setAttribute(SemanticAttributes.RPC_GRPC_STATUS_CODE, rr.statusCode)
              .setSpanKind(SpanKind.SERVER)
              .setNoParent()
              .startSpan();

      runInSpanScope(
          span,
          () -> {
            var metricLabels =
                Attributes.builder()
                    .put(SemanticAttributes.RPC_SYSTEM.getKey(), RPC_SYSTEM_GRPC)
                    .put(SemanticAttributes.RPC_SERVICE.getKey(), rr.service())
                    .put(SemanticAttributes.RPC_METHOD.getKey(), rr.method)
                    .build();
            duration.record(rr.duration, metricLabels);
            requestSizeRecorder.record(rr.requestContentSize, metricLabels);
            responseSizeRecorder.record(rr.responseContentSize, metricLabels);
            requestsPerRpcRecorder.record(1, metricLabels);
            responsesPerRpcRecorder.record(1, metricLabels);

            safeSleep(rr.duration);
            randomFromList(outboundGenerators).run();
            span.setStatus(rr.statusCode == 0 ? StatusCode.UNSET : StatusCode.ERROR);

            long count = runCount.incrementAndGet();
            if (count % 10 == 0) {
              System.out.printf("%s grpc server spans have been produced.%n", count);
            }
          });
    }
  }

  static class ClientGenerator implements Runnable {

    private final Tracer tracer;
    private final LongHistogram durationRecorder;
    private final LongHistogram requestSizeRecorder;
    private final LongHistogram responseSizeRecorder;
    private final LongHistogram requestsPerRpcRecorder;
    private final LongHistogram responsesPerRpcRecorder;
    private final AtomicLong runCount = new AtomicLong();

    ClientGenerator() {
      this.tracer = GlobalOpenTelemetry.getTracer(GrpcGenerators.class.getName());
      Meter meter = GlobalMeterProvider.get().get(GrpcGenerators.class.getName());
      this.durationRecorder = meter.histogramBuilder("rpc.client.duration").ofLongs().build();
      this.requestSizeRecorder =
          meter.histogramBuilder("rpc.client.request.size").ofLongs().build();
      this.responseSizeRecorder =
          meter.histogramBuilder("rpc.client.response.size").ofLongs().build();
      this.requestsPerRpcRecorder =
          meter.histogramBuilder("rpc.client.requests_per_rpc").ofLongs().build();
      this.responsesPerRpcRecorder =
          meter.histogramBuilder("rpc.client.responses_per_rpc").ofLongs().build();
    }

    @Override
    public void run() {
      var rr = randomGrpcRequestResponse();

      var span =
          tracer
              .spanBuilder(spanName(rr))
              .setAttribute(SemanticAttributes.RPC_SYSTEM, RPC_SYSTEM_GRPC)
              .setAttribute(SemanticAttributes.RPC_SERVICE, rr.service())
              .setAttribute(SemanticAttributes.RPC_METHOD, rr.method)
              .setAttribute(SemanticAttributes.RPC_GRPC_STATUS_CODE, rr.statusCode)
              .setAttribute(SemanticAttributes.NET_PEER_IP, rr.peerIp)
              .setSpanKind(SpanKind.CLIENT)
              .startSpan();

      runInSpanScope(
          span,
          () -> {
            var metricLabels =
                Attributes.builder()
                    .put(SemanticAttributes.RPC_SYSTEM.getKey(), RPC_SYSTEM_GRPC)
                    .put(SemanticAttributes.RPC_SERVICE.getKey(), rr.service())
                    .put(SemanticAttributes.RPC_METHOD.getKey(), rr.method)
                    .put(SemanticAttributes.NET_PEER_IP.getKey(), rr.peerIp)
                    .build();
            durationRecorder.record(rr.duration, metricLabels);
            requestSizeRecorder.record(rr.requestContentSize, metricLabels);
            responseSizeRecorder.record(rr.responseContentSize, metricLabels);
            requestsPerRpcRecorder.record(1, metricLabels);
            responsesPerRpcRecorder.record(1, metricLabels);

            safeSleep(rr.duration);
            span.setStatus(rr.statusCode == 0 ? StatusCode.UNSET : StatusCode.ERROR);

            long count = runCount.incrementAndGet();
            if (count % 10 == 0) {
              System.out.printf("%s grpc client spans have been produced.%n", count);
            }
          });
    }
  }

  private static String spanName(GrpcRequestResponse rr) {
    return String.format("%s.%s", rr.packageName, rr.service());
  }

  private static GrpcRequestResponse randomGrpcRequestResponse() {
    var rr = new GrpcRequestResponse();

    var serviceMethod = randomFromList(SERVICE_METHODS);
    rr.packageName = "com.foo.package";
    rr.service = serviceMethod.getLeft();
    rr.method = serviceMethod.getRight();
    rr.statusCode = RANDOM.nextInt(17); // gRPC codes are [0, 16]
    rr.duration = RANDOM.nextInt(1000);
    rr.requestContentSize = RANDOM.nextInt(1000);
    rr.responseContentSize = RANDOM.nextInt(1000);
    rr.peerIp = randomFromList(PEER_IPS);

    return rr;
  }

  private static class GrpcRequestResponse {
    private String packageName;
    private String service;
    private String method;
    private long statusCode;
    private long duration;
    private long requestContentSize;
    private long responseContentSize;
    private String peerIp;

    private String service() {
      return String.format("%s.%s", service, method);
    }
  }
}
