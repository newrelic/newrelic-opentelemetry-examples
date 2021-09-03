package com.newrelic.otlp;

import static com.newrelic.otlp.Common.allTheAttributes;
import static com.newrelic.otlp.Common.idAttribute;
import static com.newrelic.otlp.Common.toEpochNano;
import static com.newrelic.otlp.TestCase.of;
import static io.opentelemetry.proto.metrics.v1.AggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE;
import static io.opentelemetry.proto.metrics.v1.AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA;

import io.grpc.ManagedChannel;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.MetricsServiceGrpc;
import io.opentelemetry.proto.common.v1.StringKeyValue;
import io.opentelemetry.proto.metrics.v1.AggregationTemporality;
import io.opentelemetry.proto.metrics.v1.Gauge;
import io.opentelemetry.proto.metrics.v1.Histogram;
import io.opentelemetry.proto.metrics.v1.HistogramDataPoint;
import io.opentelemetry.proto.metrics.v1.InstrumentationLibraryMetrics;
import io.opentelemetry.proto.metrics.v1.IntDataPoint;
import io.opentelemetry.proto.metrics.v1.IntGauge;
import io.opentelemetry.proto.metrics.v1.IntHistogram;
import io.opentelemetry.proto.metrics.v1.IntHistogramDataPoint;
import io.opentelemetry.proto.metrics.v1.IntSum;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.proto.metrics.v1.Sum;
import io.opentelemetry.proto.metrics.v1.Summary;
import io.opentelemetry.proto.metrics.v1.SummaryDataPoint;
import java.time.Instant;
import java.util.List;

public class Metrics implements TestCaseProvider<ExportMetricsServiceRequest> {

  MetricsServiceGrpc.MetricsServiceBlockingStub metricService;

  Metrics(ManagedChannel managedChannel) {
    metricService = MetricsServiceGrpc.newBlockingStub(managedChannel).withCompression("gzip");
  }

  @Override
  public void exportGrpcProtobuf(ExportMetricsServiceRequest request) {
    metricService.export(request);
  }

  @Override
  public List<TestCase<ExportMetricsServiceRequest>> testCases() {
    return List.of(
        of("gauge", id -> gauge("my_gauge", id)),
        of("intGauge", id -> intGauge("my_int_gauge", id)),
        of("summary", id -> summary("my_summary", id)),
        of(
            "cumulative monotonic sum",
            id -> sum("my_cumulative_monotonic_sum", id, true, AGGREGATION_TEMPORALITY_CUMULATIVE)),
        of(
            "cumulative non-monotonic sum",
            id ->
                sum(
                    "my_cumulative_non_monotonic_sum",
                    id,
                    false,
                    AGGREGATION_TEMPORALITY_CUMULATIVE)),
        of(
            "delta monotonic sum",
            id -> sum("my_cumulative_monotonic_sum", id, true, AGGREGATION_TEMPORALITY_DELTA)),
        of(
            "delta non-monotonic sum",
            id -> sum("my_cumulative_non_monotonic_sum", id, false, AGGREGATION_TEMPORALITY_DELTA)),
        of(
            "cumulative monotonic intSum",
            id ->
                intSum(
                    "my_cumulative_monotonic_int_sum",
                    id,
                    true,
                    AGGREGATION_TEMPORALITY_CUMULATIVE)),
        of(
            "cumulative non-monotonic intSum",
            id ->
                intSum(
                    "my_cumulative_non_monotonic_int_sum",
                    id,
                    false,
                    AGGREGATION_TEMPORALITY_CUMULATIVE)),
        of(
            "delta monotonic int intSum",
            id ->
                intSum("my_cumulative_monotonic_int_sum", id, true, AGGREGATION_TEMPORALITY_DELTA)),
        of(
            "delta non-monotonic intSum",
            id ->
                intSum(
                    "my_cumulative_non_monotonic_int_sum",
                    id,
                    false,
                    AGGREGATION_TEMPORALITY_DELTA)),
        of(
            "cumulative histogram",
            id -> histogram("my_cumulative_histogram", id, AGGREGATION_TEMPORALITY_CUMULATIVE)),
        of(
            "delta histogram",
            id -> histogram("my_delta_histogram", id, AGGREGATION_TEMPORALITY_DELTA)),
        of(
            "cumulative intHistogram",
            id ->
                intHistogram(
                    "my_cumulative_int_histogram", id, AGGREGATION_TEMPORALITY_CUMULATIVE)),
        of(
            "delta intHistogram",
            id -> intHistogram("my_delta_int_histogram", id, AGGREGATION_TEMPORALITY_DELTA)));
  }

  @Override
  public String newRelicDataType() {
    return "Metric";
  }

  private static ExportMetricsServiceRequest gauge(String name, String id) {
    return metricsRequest(
        Metric.newBuilder()
            .setName(name)
            .setDescription("description")
            .setGauge(
                Gauge.newBuilder()
                    .addDataPoints(
                        NumberDataPoint.newBuilder()
                            .setStartTimeUnixNano(toEpochNano(Instant.now().minusSeconds(10)))
                            .setTimeUnixNano(toEpochNano(Instant.now()))
                            .setAsDouble(1.0)
                            .addAttributes(idAttribute(id))
                            .addAllAttributes(allTheAttributes(name + "_"))
                            .build())
                    .build())
            .build());
  }

  private static ExportMetricsServiceRequest intGauge(String name, String id) {
    return metricsRequest(
        Metric.newBuilder()
            .setName(name)
            .setDescription("description")
            .setIntGauge(
                IntGauge.newBuilder()
                    .addDataPoints(
                        IntDataPoint.newBuilder()
                            .setStartTimeUnixNano(toEpochNano(Instant.now().minusSeconds(10)))
                            .setTimeUnixNano(toEpochNano(Instant.now()))
                            .addLabels(idStringKeyValue(id))
                            .setValue(100)
                            .build())
                    .build())
            .build());
  }

  private static StringKeyValue idStringKeyValue(String id) {
    var idAttribute = idAttribute(id);
    return StringKeyValue.newBuilder()
        .setKey(idAttribute.getKey())
        .setValue(idAttribute.getValue().getStringValue())
        .build();
  }

  private static ExportMetricsServiceRequest summary(String name, String id) {
    return metricsRequest(
        Metric.newBuilder()
            .setName(name)
            .setDescription("description")
            .setSummary(
                Summary.newBuilder()
                    .addDataPoints(
                        SummaryDataPoint.newBuilder()
                            .setStartTimeUnixNano(toEpochNano(Instant.now().minusSeconds(10)))
                            .setTimeUnixNano(toEpochNano(Instant.now()))
                            .setCount(2)
                            .setSum(99.0)
                            .addQuantileValues(
                                SummaryDataPoint.ValueAtQuantile.newBuilder()
                                    .setQuantile(0.0)
                                    .setValue(0.0)
                                    .build())
                            .addQuantileValues(
                                SummaryDataPoint.ValueAtQuantile.newBuilder()
                                    .setQuantile(1.0)
                                    .setValue(99.0)
                                    .build())
                            .addAttributes(idAttribute(id))
                            .addAllAttributes(allTheAttributes(name + "_"))
                            .build())
                    .build())
            .build());
  }

  private static ExportMetricsServiceRequest sum(
      String name, String id, boolean isMonotonic, AggregationTemporality aggregationTemporality) {
    return metricsRequest(
        Metric.newBuilder()
            .setName(name)
            .setDescription("description")
            .setSum(
                Sum.newBuilder()
                    .setIsMonotonic(isMonotonic)
                    .setAggregationTemporality(aggregationTemporality)
                    .addDataPoints(
                        NumberDataPoint.newBuilder()
                            .setStartTimeUnixNano(toEpochNano(Instant.now().minusSeconds(10)))
                            .setTimeUnixNano(toEpochNano(Instant.now()))
                            .setAsDouble(1.0)
                            .addAttributes(idAttribute(id))
                            .addAllAttributes(allTheAttributes(name + "_"))
                            .build())
                    .build())
            .build());
  }

  private static ExportMetricsServiceRequest intSum(
      String name, String id, boolean isMonotonic, AggregationTemporality aggregationTemporality) {
    return metricsRequest(
        Metric.newBuilder()
            .setName(name)
            .setDescription("description")
            .setIntSum(
                IntSum.newBuilder()
                    .setIsMonotonic(isMonotonic)
                    .setAggregationTemporality(aggregationTemporality)
                    .addDataPoints(
                        IntDataPoint.newBuilder()
                            .setStartTimeUnixNano(toEpochNano(Instant.now().minusSeconds(10)))
                            .setTimeUnixNano(toEpochNano(Instant.now()))
                            .setValue(1L)
                            .addLabels(idStringKeyValue(id))
                            .addAllLabels(
                                List.of(
                                    StringKeyValue.newBuilder()
                                        .setKey("skey")
                                        .setValue("value")
                                        .build()))
                            .build())
                    .build())
            .build());
  }

  private static ExportMetricsServiceRequest histogram(
      String name, String id, AggregationTemporality aggregationTemporality) {
    return metricsRequest(
        Metric.newBuilder()
            .setName(name)
            .setDescription("description")
            .setUnit("1")
            .setHistogram(
                Histogram.newBuilder()
                    .setAggregationTemporality(aggregationTemporality)
                    .addDataPoints(
                        HistogramDataPoint.newBuilder()
                            .setStartTimeUnixNano(toEpochNano(Instant.now().minusSeconds(10)))
                            .setTimeUnixNano(toEpochNano(Instant.now()))
                            .setCount(11)
                            .setSum(100)
                            .addAllExplicitBounds(List.of(1.0, 2.0, 3.0))
                            .addAllBucketCounts(List.of(5L, 4L, 1L, 1L))
                            .addAttributes(idAttribute(id))
                            .addAllAttributes(allTheAttributes(name + "_"))
                            .build())
                    .build())
            .build());
  }

  private static ExportMetricsServiceRequest intHistogram(
      String name, String id, AggregationTemporality aggregationTemporality) {
    return metricsRequest(
        Metric.newBuilder()
            .setName(name)
            .setDescription("description")
            .setIntHistogram(
                IntHistogram.newBuilder()
                    .setAggregationTemporality(aggregationTemporality)
                    .addDataPoints(
                        IntHistogramDataPoint.newBuilder()
                            .setStartTimeUnixNano(toEpochNano(Instant.now().minusSeconds(10)))
                            .setTimeUnixNano(toEpochNano(Instant.now()))
                            .setCount(11)
                            .setSum(100)
                            .addAllExplicitBounds(List.of(1.0, 2.0, 3.0))
                            .addAllBucketCounts(List.of(5L, 4L, 1L, 1L))
                            .addLabels(idStringKeyValue(id))
                            .addAllLabels(
                                List.of(
                                    StringKeyValue.newBuilder()
                                        .setKey("skey")
                                        .setValue("value")
                                        .build()))
                            .build())
                    .build())
            .build());
  }

  private static ExportMetricsServiceRequest metricsRequest(Metric metric) {
    return ExportMetricsServiceRequest.newBuilder()
        .addResourceMetrics(
            ResourceMetrics.newBuilder()
                .setResource(Common.resource())
                .setSchemaUrl("schema url")
                .addInstrumentationLibraryMetrics(
                    InstrumentationLibraryMetrics.newBuilder()
                        .setInstrumentationLibrary(Common.instrumentationLibrary())
                        .addMetrics(metric)
                        .build())
                .build())
        .build();
  }
}
