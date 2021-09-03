package com.newrelic.otlp;

import static com.newrelic.otlp.Common.allTheAttributes;
import static com.newrelic.otlp.Common.idAttribute;
import static com.newrelic.otlp.Common.spanIdByteString;
import static com.newrelic.otlp.Common.toEpochNano;
import static com.newrelic.otlp.Common.traceIdByteString;

import io.grpc.ManagedChannel;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.collector.logs.v1.LogsServiceGrpc;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.logs.v1.InstrumentationLibraryLogs;
import io.opentelemetry.proto.logs.v1.LogRecord;
import io.opentelemetry.proto.logs.v1.ResourceLogs;
import io.opentelemetry.proto.logs.v1.SeverityNumber;
import java.time.Instant;
import java.util.List;

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
    return List.of(TestCase.of("kitchen sink log", Logs::logRequest));
  }

  @Override
  public String newRelicDataType() {
    return "Log";
  }

  private static ExportLogsServiceRequest logRequest(String id) {
    return ExportLogsServiceRequest.newBuilder()
        .addResourceLogs(
            ResourceLogs.newBuilder()
                .setResource(Common.resource())
                .addInstrumentationLibraryLogs(
                    InstrumentationLibraryLogs.newBuilder()
                        .setInstrumentationLibrary(Common.instrumentationLibrary())
                        .addLogs(
                            LogRecord.newBuilder()
                                .setTimeUnixNano(toEpochNano(Instant.now()))
                                .setSeverityNumber(SeverityNumber.SEVERITY_NUMBER_DEBUG)
                                .setSeverityText("DEBUG")
                                .setName("name")
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
}
