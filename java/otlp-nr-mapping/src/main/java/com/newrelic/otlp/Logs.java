package com.newrelic.otlp;

import static com.newrelic.otlp.Common.allTheAttributes;
import static com.newrelic.otlp.Common.idAttribute;
import static com.newrelic.otlp.Common.obfuscateKeyValues;
import static com.newrelic.otlp.Common.obfuscateResource;
import static com.newrelic.otlp.Common.spanIdByteString;
import static com.newrelic.otlp.Common.toEpochNano;
import static com.newrelic.otlp.Common.traceIdByteString;

import io.grpc.ManagedChannel;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.collector.logs.v1.LogsServiceGrpc;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.logs.v1.LogRecord;
import io.opentelemetry.proto.logs.v1.ResourceLogs;
import io.opentelemetry.proto.logs.v1.ScopeLogs;
import io.opentelemetry.proto.logs.v1.SeverityNumber;
import java.time.Instant;
import java.util.List;
import java.util.Set;

class Logs implements TestCaseProvider<ExportLogsServiceRequest> {

  private final LogsServiceGrpc.LogsServiceBlockingStub logService;

  Logs(ManagedChannel managedChannel) {
    logService = LogsServiceGrpc.newBlockingStub(managedChannel).withCompression("gzip");
  }

  @Override
  public void exportGrpcProtobuf(ExportLogsServiceRequest request) {
    logService.export(request);
  }

  @Override
  public List<TestCase<ExportLogsServiceRequest>> testCases() {
    return List.of(
        TestCase.of("kitchen sink log", Logs::kitchenSinkLog),
        TestCase.of("attribute precedence", Logs::attributePrecedence));
  }

  @Override
  public String newRelicDataType() {
    return "Log";
  }

  @Override
  public ExportLogsServiceRequest obfuscateAttributeKeys(
      ExportLogsServiceRequest request, Set<String> attributeKeys) {
    var requestBuilder = ExportLogsServiceRequest.newBuilder();
    for (var rLog : request.getResourceLogsList()) {
      var rLogBuilder = rLog.toBuilder();
      rLogBuilder.setResource(obfuscateResource(rLog.getResource(), attributeKeys));
      rLogBuilder.clearScopeLogs();
      for (var isLog : rLog.getScopeLogsList()) {
        var isLogBuilder = isLog.toBuilder();
        isLogBuilder.clearLogRecords();
        for (var log : isLog.getLogRecordsList()) {
          var logBuilder = log.toBuilder();
          logBuilder.clearAttributes();
          logBuilder.addAllAttributes(obfuscateKeyValues(log.getAttributesList(), attributeKeys));
          isLogBuilder.addLogRecords(logBuilder.build());
        }
        rLogBuilder.addScopeLogs(isLogBuilder.build());
      }
      requestBuilder.addResourceLogs(rLogBuilder.build());
    }
    return requestBuilder.build();
  }

  private static ExportLogsServiceRequest kitchenSinkLog(String id) {
    return ExportLogsServiceRequest.newBuilder()
        .addResourceLogs(
            ResourceLogs.newBuilder()
                .setResource(Common.resource().addAllAttributes(allTheAttributes("resource_")))
                .addScopeLogs(
                    ScopeLogs.newBuilder()
                        .setScope(Common.instrumentationScope())
                        .addLogRecords(
                            LogRecord.newBuilder()
                                .setTimeUnixNano(toEpochNano(Instant.now()))
                                .setSeverityNumber(SeverityNumber.SEVERITY_NUMBER_DEBUG)
                                .setSeverityText("DEBUG")
                                .setBody(AnyValue.newBuilder().setStringValue("body").build())
                                .addAttributes(idAttribute(id))
                                .addAllAttributes(allTheAttributes("log_"))
                                .setDroppedAttributesCount(1)
                                .setFlags(1)
                                .setTraceId(traceIdByteString())
                                .setSpanId(spanIdByteString())
                                .build())
                        .build())
                .buildPartial())
        .build();
  }

  private static ExportLogsServiceRequest attributePrecedence(String id) {
    var duplicateKey = "duplicate-key";
    return ExportLogsServiceRequest.newBuilder()
        .addResourceLogs(
            ResourceLogs.newBuilder()
                .setResource(
                    Common.resource()
                        .addAttributes(
                            KeyValue.newBuilder()
                                .setKey(duplicateKey)
                                .setValue(
                                    AnyValue.newBuilder().setStringValue("resource-value").build())
                                .build()))
                .addScopeLogs(
                    ScopeLogs.newBuilder()
                        .setScope(Common.instrumentationScope())
                        .addLogRecords(
                            LogRecord.newBuilder()
                                .setTimeUnixNano(toEpochNano(Instant.now()))
                                .setBody(AnyValue.newBuilder().setStringValue("body").build())
                                .addAttributes(idAttribute(id))
                                .addAttributes(
                                    KeyValue.newBuilder()
                                        .setKey(duplicateKey)
                                        .setValue(
                                            AnyValue.newBuilder()
                                                .setStringValue("log-record-value")
                                                .build())
                                        .build())
                                .build())
                        .build())
                .buildPartial())
        .build();
  }
}
