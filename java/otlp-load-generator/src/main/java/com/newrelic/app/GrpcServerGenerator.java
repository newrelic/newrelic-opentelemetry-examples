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

public class GrpcServerGenerator implements Runnable {

  private static final String PACKAGE = "com.foo.package";
  private static final Random RANDOM = new Random();
  private static final List<RpcMethod> RPC_METHODS =
      List.of(
          new RpcMethod(PACKAGE, "UserService", "AddUser"),
          new RpcMethod(PACKAGE, "UserService", "RemoveUser"),
          new RpcMethod(PACKAGE, "UserService", "GetUser"),
          new RpcMethod(PACKAGE, "RoleService", "AddRole"),
          new RpcMethod(PACKAGE, "RoleService", "RemoveRole"),
          new RpcMethod(PACKAGE, "RoleService", "GetRole"));
  private static final String RPC_SYSTEM = "grpc";

  private final Tracer tracer;
  private final AtomicLong runCount = new AtomicLong();

  public GrpcServerGenerator() {
    this.tracer = GlobalOpenTelemetry.getTracer(GrpcServerGenerator.class.getName());
  }

  @Override
  public void run() {
    var rpcMethod = randomFromList(RPC_METHODS);
    var spanName =
        String.format(
            "%s.%s/%s", rpcMethod.packageName, rpcMethod.serviceName, rpcMethod.methodName);
    var rpcService = String.format("%s.%s", rpcMethod.packageName, rpcMethod.serviceName);
    var duration = RANDOM.nextInt(1000);
    var statusCode =
        randomFromList(List.of(SemanticAttributes.RpcGrpcStatusCodeValues.values())).getValue();

    var span =
        tracer
            .spanBuilder(spanName)
            .setAttribute(SemanticAttributes.RPC_SYSTEM, RPC_SYSTEM)
            .setAttribute(SemanticAttributes.RPC_SERVICE, rpcService)
            .setAttribute(SemanticAttributes.RPC_METHOD, rpcMethod.methodName)
            .setAttribute(SemanticAttributes.RPC_GRPC_STATUS_CODE, statusCode)
            .setSpanKind(SpanKind.SERVER)
            .setNoParent()
            .startSpan();

    Utils.safeSleep(duration);

    span.setStatus(statusCode == 0 ? StatusCode.OK : StatusCode.ERROR);
    span.end();
    long count = runCount.incrementAndGet();
    if (count % 10 == 0) {
      System.out.printf("%s grpc server spans have been produced.%n", count);
    }
  }

  private static class RpcMethod {
    private final String packageName;
    private final String serviceName;
    private final String methodName;

    private RpcMethod(String packageName, String serviceName, String methodName) {
      this.packageName = packageName;
      this.serviceName = serviceName;
      this.methodName = methodName;
    }
  }
}
