package com.newrelic.otlp;

import static com.newrelic.otlp.Common.allTheAttributes;
import static com.newrelic.otlp.Common.idAttribute;
import static com.newrelic.otlp.Common.obfuscateKeyValues;
import static com.newrelic.otlp.Common.obfuscateResource;
import static com.newrelic.otlp.Common.toEpochNano;
import static com.newrelic.otlp.TestCase.of;
import static io.opentelemetry.proto.metrics.v1.AggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE;
import static io.opentelemetry.proto.metrics.v1.AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA;

import io.grpc.ManagedChannel;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.MetricsServiceGrpc;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.AggregationTemporality;
import io.opentelemetry.proto.metrics.v1.Gauge;
import io.opentelemetry.proto.metrics.v1.Histogram;
import io.opentelemetry.proto.metrics.v1.HistogramDataPoint;
import io.opentelemetry.proto.metrics.v1.Metric;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.proto.metrics.v1.ScopeMetrics;
import io.opentelemetry.proto.metrics.v1.Sum;
import io.opentelemetry.proto.metrics.v1.Summary;
import io.opentelemetry.proto.metrics.v1.SummaryDataPoint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
    var testCases = new ArrayList<TestCase<ExportMetricsServiceRequest>>();

    // gauge
    testCases.add(of("gauge", id -> gauge("my_gauge", id)));

    // summary
    testCases.add(of("summary", id -> summary("my_summary", id)));

    // sum
    testCases.add(
        of(
            "sum monotonic cumulative",
            id -> sum("my_sum", id, true, AGGREGATION_TEMPORALITY_CUMULATIVE)));
    testCases.add(
        of("sum monotonic delta", id -> sum("my_sum", id, true, AGGREGATION_TEMPORALITY_DELTA)));
    testCases.add(
        of(
            "sum non monotonic cumulative",
            id -> sum("my_sum", id, false, AGGREGATION_TEMPORALITY_CUMULATIVE)));
    testCases.add(
        of(
            "sum non monotonic delta",
            id -> sum("my_sum", id, false, AGGREGATION_TEMPORALITY_DELTA)));

    // histogram
    testCases.add(
        of(
            "histogram cumulative",
            id -> histogram("my_histogram", id, AGGREGATION_TEMPORALITY_CUMULATIVE)));
    testCases.add(
        of("histogram delta", id -> histogram("my_histogram", id, AGGREGATION_TEMPORALITY_DELTA)));

    // other test cases
    testCases.add(of("attribute precedence", Metrics::attributePrecedence));

    return testCases;
  }

  @Override
  public String newRelicDataType() {
    return "Metric";
  }

  @Override
  public ExportMetricsServiceRequest obfuscateAttributeKeys(
      ExportMetricsServiceRequest request, Set<String> attributeKeys) {
    var requestBuilder = ExportMetricsServiceRequest.newBuilder();
    for (var rMetric : request.getResourceMetricsList()) {
      var rMetricBuilder = rMetric.toBuilder();
      rMetricBuilder.setResource(obfuscateResource(rMetric.getResource(), attributeKeys));
      rMetricBuilder.clearScopeMetrics();
      for (var isMetric : rMetric.getScopeMetricsList()) {
        var isMetricBuilder = isMetric.toBuilder();
        isMetricBuilder.clearMetrics();
        for (var metric : isMetric.getMetricsList()) {
          var metricBuilder = metric.toBuilder();
          if (metricBuilder.hasGauge()) {
            metricBuilder.clearGauge();
            var gaugeBuilder = metricBuilder.getGaugeBuilder();
            gaugeBuilder.clearDataPoints();
            for (var datapoint : metric.getGauge().getDataPointsList()) {
              gaugeBuilder.addDataPoints(
                  datapoint.toBuilder()
                      .clearAttributes()
                      .addAllAttributes(
                          obfuscateKeyValues(datapoint.getAttributesList(), attributeKeys))
                      .build());
            }
            metricBuilder.setGauge(gaugeBuilder.build());
          }
          if (metricBuilder.hasSum()) {
            metricBuilder.clearSum();
            var sumBuilder = metricBuilder.getSumBuilder();
            sumBuilder.setIsMonotonic(metric.getSum().getIsMonotonic());
            sumBuilder.setAggregationTemporality(metric.getSum().getAggregationTemporality());
            sumBuilder.clearDataPoints();
            for (var datapoint : metric.getSum().getDataPointsList()) {
              sumBuilder.addDataPoints(
                  datapoint.toBuilder()
                      .clearAttributes()
                      .addAllAttributes(
                          obfuscateKeyValues(datapoint.getAttributesList(), attributeKeys))
                      .build());
            }
            metricBuilder.setSum(sumBuilder.build());
          }
          if (metricBuilder.hasHistogram()) {
            metricBuilder.clearHistogram();
            var histogramBuilder = metricBuilder.getHistogramBuilder();
            histogramBuilder.setAggregationTemporality(
                metric.getHistogram().getAggregationTemporality());
            histogramBuilder.clearDataPoints();
            for (var datapoint : metric.getHistogram().getDataPointsList()) {
              histogramBuilder.addDataPoints(
                  datapoint.toBuilder()
                      .clearAttributes()
                      .addAllAttributes(
                          obfuscateKeyValues(datapoint.getAttributesList(), attributeKeys))
                      .build());
            }
            metricBuilder.setHistogram(histogramBuilder.build());
          }
          if (metricBuilder.hasSummary()) {
            metricBuilder.clearSummary();
            var summaryBuilder = metricBuilder.getSummaryBuilder();
            summaryBuilder.clearDataPoints();
            for (var datapoint : metric.getSummary().getDataPointsList()) {
              summaryBuilder.addDataPoints(
                  datapoint.toBuilder()
                      .clearAttributes()
                      .addAllAttributes(
                          obfuscateKeyValues(datapoint.getAttributesList(), attributeKeys))
                      .build());
            }
            metricBuilder.setSummary(summaryBuilder.build());
          }
          isMetricBuilder.addMetrics(metricBuilder.build());
        }
        rMetricBuilder.addScopeMetrics(isMetricBuilder.build());
      }
      requestBuilder.addResourceMetrics(rMetricBuilder.build());
    }
    return requestBuilder.build();
  }

  private static ExportMetricsServiceRequest gauge(String name, String id) {
    return metricsRequest(
        metricBuilder(name)
            .setGauge(Gauge.newBuilder().addDataPoints(numberDataPoint(name, id)).build())
            .build());
  }

  private static ExportMetricsServiceRequest summary(String name, String id) {
    var summaryDataPointBuilder =
        SummaryDataPoint.newBuilder()
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
            .setStartTimeUnixNano(toEpochNano(Instant.now().minusSeconds(10)));
    return metricsRequest(
        metricBuilder(name)
            .setSummary(Summary.newBuilder().addDataPoints(summaryDataPointBuilder.build()).build())
            .build());
  }

  private static ExportMetricsServiceRequest sum(
      String name, String id, boolean isMonotonic, AggregationTemporality aggregationTemporality) {
    return metricsRequest(
        metricBuilder(name)
            .setSum(
                Sum.newBuilder()
                    .setIsMonotonic(isMonotonic)
                    .setAggregationTemporality(aggregationTemporality)
                    .addDataPoints(numberDataPoint(name, id)))
            .build());
  }

  private static ExportMetricsServiceRequest histogram(
      String name, String id, AggregationTemporality aggregationTemporality) {
    var histogramDataPointBuilder =
        HistogramDataPoint.newBuilder()
            .setTimeUnixNano(toEpochNano(Instant.now()))
            .setCount(11)
            .setSum(100)
            .addAllExplicitBounds(List.of(1.0, 2.0, 3.0))
            .addAllBucketCounts(List.of(5L, 4L, 1L, 1L))
            .addAttributes(idAttribute(id))
            .addAllAttributes(allTheAttributes(name + "_"))
            .setStartTimeUnixNano(toEpochNano(Instant.now().minusSeconds(10)));
    return metricsRequest(
        metricBuilder(name)
            .setHistogram(
                Histogram.newBuilder()
                    .setAggregationTemporality(aggregationTemporality)
                    .addDataPoints(histogramDataPointBuilder)
                    .build())
            .build());
  }

  private static NumberDataPoint numberDataPoint(String metricName, String id) {
    return NumberDataPoint.newBuilder()
        .setTimeUnixNano(toEpochNano(Instant.now()))
        .setAsDouble(1.0)
        .addAttributes(idAttribute(id))
        .addAllAttributes(allTheAttributes(metricName + "_"))
        .setStartTimeUnixNano(toEpochNano(Instant.now().minusSeconds(10)))
        .build();
  }

  private static Metric.Builder metricBuilder(String name) {
    return Metric.newBuilder().setName(name).setDescription("description").setUnit("unit");
  }

  private static ExportMetricsServiceRequest metricsRequest(Metric metric) {
    return ExportMetricsServiceRequest.newBuilder()
        .addResourceMetrics(
            ResourceMetrics.newBuilder()
                .setResource(Common.resource().addAllAttributes(allTheAttributes("resource_")))
                .setSchemaUrl("schema url")
                .addScopeMetrics(
                    ScopeMetrics.newBuilder()
                        .setScope(Common.instrumentationScope())
                        .addMetrics(metric)
                        .build())
                .build())
        .build();
  }

  private static ExportMetricsServiceRequest attributePrecedence(String id) {
    var duplicateKey = "duplicate-key";
    return ExportMetricsServiceRequest.newBuilder()
        .addResourceMetrics(
            ResourceMetrics.newBuilder()
                .setResource(
                    Common.resource()
                        .addAttributes(
                            KeyValue.newBuilder()
                                .setKey(duplicateKey)
                                .setValue(
                                    AnyValue.newBuilder().setStringValue("resource-value").build())
                                .build()))
                .addScopeMetrics(
                    ScopeMetrics.newBuilder()
                        .setScope(Common.instrumentationScope())
                        .addMetrics(
                            metricBuilder("my-sum")
                                .setSum(
                                    Sum.newBuilder()
                                        .setAggregationTemporality(AGGREGATION_TEMPORALITY_DELTA)
                                        .setIsMonotonic(true)
                                        .addDataPoints(
                                            NumberDataPoint.newBuilder()
                                                .setAsDouble(1.0)
                                                .setTimeUnixNano(toEpochNano(Instant.now()))
                                                .setStartTimeUnixNano(
                                                    toEpochNano(Instant.now().minusSeconds(10)))
                                                .addAttributes(idAttribute(id))
                                                .addAttributes(
                                                    KeyValue.newBuilder()
                                                        .setKey(duplicateKey)
                                                        .setValue(
                                                            AnyValue.newBuilder()
                                                                .setStringValue("data-point-value")
                                                                .build()))
                                                .setAsDouble(1.0)
                                                .build())
                                        .build())
                                .build())
                        .build())
                .build())
        .build();
  }
}
