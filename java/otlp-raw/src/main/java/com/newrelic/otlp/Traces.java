package com.newrelic.otlp;

import static com.newrelic.otlp.Common.allTheAttributes;
import static com.newrelic.otlp.Common.idAttribute;
import static com.newrelic.otlp.Common.instrumentationLibrary;
import static com.newrelic.otlp.Common.obfuscateKeyValues;
import static com.newrelic.otlp.Common.obfuscateResource;
import static com.newrelic.otlp.Common.spanIdByteString;
import static com.newrelic.otlp.Common.toByteString;
import static com.newrelic.otlp.Common.toEpochNano;
import static com.newrelic.otlp.Common.traceIdByteString;

import io.grpc.ManagedChannel;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.TraceId;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc;
import io.opentelemetry.proto.trace.v1.InstrumentationLibrarySpans;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.proto.trace.v1.Status;
import io.opentelemetry.sdk.trace.IdGenerator;
import java.time.Instant;
import java.util.List;
import java.util.Set;

class Traces implements TestCaseProvider<ExportTraceServiceRequest> {

  private final TraceServiceGrpc.TraceServiceBlockingStub traceService;

  Traces(ManagedChannel managedChannel) {
    traceService = TraceServiceGrpc.newBlockingStub(managedChannel).withCompression("gzip");
  }

  @Override
  public void exportGrpcProtobuf(ExportTraceServiceRequest request) {
    traceService.export(request);
  }

  @Override
  public List<TestCase<ExportTraceServiceRequest>> testCases() {
    return List.of(TestCase.of("kitchen sink trace", Traces::traceRequest));
  }

  @Override
  public String newRelicDataType() {
    return "Span";
  }

  @Override
  public ExportTraceServiceRequest obfuscateAttributeKeys(
      ExportTraceServiceRequest request, Set<String> attributeKeys) {
    var requestBuilder = ExportTraceServiceRequest.newBuilder();
    for (var rSpan : request.getResourceSpansList()) {
      var rSpanBuilder = rSpan.toBuilder();
      rSpanBuilder.setResource(obfuscateResource(rSpan.getResource(), attributeKeys));
      rSpanBuilder.clearInstrumentationLibrarySpans();
      for (var ilSpan : rSpan.getInstrumentationLibrarySpansList()) {
        var ilSpanBuilder = ilSpan.toBuilder();
        ilSpanBuilder.clearSpans();
        for (var span : ilSpan.getSpansList()) {
          var spanBuilder = span.toBuilder();
          spanBuilder.clearAttributes();
          spanBuilder.addAllAttributes(obfuscateKeyValues(span.getAttributesList(), attributeKeys));
          spanBuilder.clearEvents();
          for (var event : span.getEventsList()) {
            spanBuilder.addEvents(
                event.toBuilder()
                    .clearAttributes()
                    .addAllAttributes(obfuscateKeyValues(event.getAttributesList(), attributeKeys))
                    .build());
          }
          spanBuilder.clearLinks();
          for (var link : span.getLinksList()) {
            spanBuilder.addLinks(
                link.toBuilder()
                    .clearAttributes()
                    .addAllAttributes(obfuscateKeyValues(link.getAttributesList(), attributeKeys))
                    .build());
          }
          ilSpanBuilder.addSpans(spanBuilder.build());
        }
        rSpanBuilder.addInstrumentationLibrarySpans(ilSpanBuilder.build());
      }
      requestBuilder.addResourceSpans(rSpanBuilder.build());
    }
    return requestBuilder.build();
  }

  private static ExportTraceServiceRequest traceRequest(String id) {
    return ExportTraceServiceRequest.newBuilder()
        .addResourceSpans(
            ResourceSpans.newBuilder()
                .setResource(Common.resource())
                .setSchemaUrl("schema url")
                .addInstrumentationLibrarySpans(
                    InstrumentationLibrarySpans.newBuilder()
                        .setInstrumentationLibrary(instrumentationLibrary())
                        .addSpans(
                            Span.newBuilder()
                                .setTraceId(traceIdByteString())
                                .setSpanId(spanIdByteString())
                                .setTraceState("foo")
                                .setParentSpanId(spanIdByteString())
                                .setName("my-span")
                                .setKind(Span.SpanKind.SPAN_KIND_INTERNAL)
                                .setStartTimeUnixNano(toEpochNano(Instant.now()))
                                .setEndTimeUnixNano(toEpochNano(Instant.now().plusSeconds(10)))
                                .addAttributes(idAttribute(id))
                                .addAllAttributes(allTheAttributes("span_"))
                                .setDroppedAttributesCount(1)
                                .addEvents(
                                    Span.Event.newBuilder()
                                        .setName("event-name")
                                        .setDroppedAttributesCount(1)
                                        .setTimeUnixNano(toEpochNano(Instant.now()))
                                        .addAllAttributes(allTheAttributes("span_event_"))
                                        .build())
                                .setDroppedEventsCount(1)
                                .addLinks(
                                    Span.Link.newBuilder()
                                        .setTraceId(
                                            toByteString(
                                                IdGenerator.random().generateTraceId(),
                                                TraceId.getLength()))
                                        .setSpanId(
                                            toByteString(
                                                IdGenerator.random().generateSpanId(),
                                                SpanId.getLength()))
                                        .build())
                                .setDroppedLinksCount(1)
                                .setStatus(
                                    Status.newBuilder()
                                        .setCode(Status.StatusCode.STATUS_CODE_OK)
                                        .setMessage("status message!")
                                        .build())
                                .build())
                        .build())
                .buildPartial())
        .build();
  }
}
