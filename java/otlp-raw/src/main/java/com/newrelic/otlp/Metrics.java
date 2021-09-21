package com.newrelic.otlp;

import static com.newrelic.otlp.Common.allTheAttributes;
import static com.newrelic.otlp.Common.idAttribute;
import static com.newrelic.otlp.Common.obfuscateKeyValues;
import static com.newrelic.otlp.Common.obfuscateResource;
import static com.newrelic.otlp.Common.obfuscateStringKeyValues;
import static com.newrelic.otlp.Common.toEpochNano;
import static com.newrelic.otlp.TestCase.of;
import static io.opentelemetry.proto.metrics.v1.AggregationTemporality.AGGREGATION_TEMPORALITY_CUMULATIVE;
import static io.opentelemetry.proto.metrics.v1.AggregationTemporality.AGGREGATION_TEMPORALITY_DELTA;

import io.grpc.ManagedChannel;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.MetricsServiceGrpc;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
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
    testCases.add(of("gauge with starttime", id -> gauge("my_gauge", id, true)));
    testCases.add(of("gauge without starttime", id -> gauge("my_gauge", id, false)));
    // int gauge
    testCases.add(of("intgauge with starttime", id -> intGauge("my_int_gauge", id, true)));
    testCases.add(of("intgauge without starttime", id -> intGauge("my_int_gauge", id, false)));

    // summary
    testCases.add(of("summary with starttime", id -> summary("my_summary", id, true)));
    testCases.add(of("summary without starttime", id -> summary("my_summary", id, false)));

    // sum
    testCases.add(
        of(
            "sum monotonic cumulative with starttime",
            id -> sum("my_sum", id, true, AGGREGATION_TEMPORALITY_CUMULATIVE, true)));
    testCases.add(
        of(
            "sum monotonic cumulative without starttime",
            id -> sum("my_sum", id, true, AGGREGATION_TEMPORALITY_CUMULATIVE, false)));
    testCases.add(
        of(
            "sum monotonic delta with starttime",
            id -> sum("my_sum", id, true, AGGREGATION_TEMPORALITY_DELTA, true)));
    testCases.add(
        of(
            "sum monotonic delta without starttime",
            id -> sum("my_sum", id, true, AGGREGATION_TEMPORALITY_DELTA, false)));
    testCases.add(
        of(
            "sum non monotonic cumulative with starttime",
            id -> sum("my_sum", id, false, AGGREGATION_TEMPORALITY_CUMULATIVE, true)));
    testCases.add(
        of(
            "sum non monotonic cumulative without starttime",
            id -> sum("my_sum", id, false, AGGREGATION_TEMPORALITY_CUMULATIVE, false)));
    testCases.add(
        of(
            "sum non monotonic delta with starttime",
            id -> sum("my_sum", id, false, AGGREGATION_TEMPORALITY_DELTA, true)));
    testCases.add(
        of(
            "sum non monotonic delta without starttime",
            id -> sum("my_sum", id, false, AGGREGATION_TEMPORALITY_DELTA, false)));
    // int sum
    testCases.add(
        of(
            "intsum monotonic cumulative with starttime",
            id -> intSum("my_int_sum", id, true, AGGREGATION_TEMPORALITY_CUMULATIVE, true)));
    testCases.add(
        of(
            "intsum monotonic cumulative without starttime",
            id -> intSum("my_int_sum", id, true, AGGREGATION_TEMPORALITY_CUMULATIVE, false)));
    testCases.add(
        of(
            "intsum monotonic delta with starttime",
            id -> intSum("my_int_sum", id, true, AGGREGATION_TEMPORALITY_DELTA, true)));
    testCases.add(
        of(
            "intsum monotonic delta without starttime",
            id -> intSum("my_int_sum", id, true, AGGREGATION_TEMPORALITY_DELTA, false)));
    testCases.add(
        of(
            "intsum non monotonic cumulative with starttime",
            id -> intSum("my_int_sum", id, false, AGGREGATION_TEMPORALITY_CUMULATIVE, true)));
    testCases.add(
        of(
            "intsum non monotonic cumulative without starttime",
            id -> intSum("my_int_sum", id, false, AGGREGATION_TEMPORALITY_CUMULATIVE, false)));
    testCases.add(
        of(
            "intsum non monotonic delta with starttime",
            id -> intSum("my_int_sum", id, false, AGGREGATION_TEMPORALITY_DELTA, true)));
    testCases.add(
        of(
            "intsum non monotonic delta without starttime",
            id -> intSum("my_int_sum", id, false, AGGREGATION_TEMPORALITY_DELTA, false)));

    // histogram
    testCases.add(
        of(
            "histogram cumulative with starttime",
            id -> histogram("my_histogram", id, AGGREGATION_TEMPORALITY_CUMULATIVE, true)));
    testCases.add(
        of(
            "histogram cumulative without starttime",
            id -> histogram("my_histogram", id, AGGREGATION_TEMPORALITY_CUMULATIVE, false)));
    testCases.add(
        of(
            "histogram delta with starttime",
            id -> histogram("my_histogram", id, AGGREGATION_TEMPORALITY_DELTA, true)));
    testCases.add(
        of(
            "histogram delta without starttime",
            id -> histogram("my_histogram", id, AGGREGATION_TEMPORALITY_DELTA, false)));
    // int histogram
    testCases.add(
        of(
            "inthistgoram cumulative with starttime",
            id -> intHistogram("my_int_histogram", id, AGGREGATION_TEMPORALITY_CUMULATIVE, true)));
    testCases.add(
        of(
            "inthistgoram cumulative without starttime",
            id -> intHistogram("my_int_histogram", id, AGGREGATION_TEMPORALITY_CUMULATIVE, false)));
    testCases.add(
        of(
            "inthistgoram delta with starttime",
            id -> intHistogram("my_int_histogram", id, AGGREGATION_TEMPORALITY_DELTA, true)));
    testCases.add(
        of(
            "inthistgoram delta without starttime",
            id -> intHistogram("my_int_histogram", id, AGGREGATION_TEMPORALITY_DELTA, false)));

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
      rMetricBuilder.clearInstrumentationLibraryMetrics();
      for (var ilMetric : rMetric.getInstrumentationLibraryMetricsList()) {
        var ilMetricBuilder = ilMetric.toBuilder();
        ilMetricBuilder.clearMetrics();
        for (var metric : ilMetric.getMetricsList()) {
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
          if (metricBuilder.hasIntGauge()) {
            metricBuilder.clearIntGauge();
            var intGaugeBuilder = metricBuilder.getIntGaugeBuilder();
            intGaugeBuilder.clearDataPoints();
            for (var datapoint : metric.getIntGauge().getDataPointsList()) {
              intGaugeBuilder.addDataPoints(
                  datapoint.toBuilder()
                      .clearLabels()
                      .addAllLabels(
                          obfuscateStringKeyValues(datapoint.getLabelsList(), attributeKeys))
                      .build());
            }
            metricBuilder.setIntGauge(intGaugeBuilder.build());
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
          if (metricBuilder.hasIntSum()) {
            metricBuilder.clearIntSum();
            var intSumBuilder = metricBuilder.getIntSumBuilder();
            intSumBuilder.setIsMonotonic(metric.getIntSum().getIsMonotonic());
            intSumBuilder.setAggregationTemporality(metric.getIntSum().getAggregationTemporality());
            intSumBuilder.clearDataPoints();
            for (var datapoint : metric.getIntSum().getDataPointsList()) {
              intSumBuilder.addDataPoints(
                  datapoint.toBuilder()
                      .clearLabels()
                      .addAllLabels(
                          obfuscateStringKeyValues(datapoint.getLabelsList(), attributeKeys))
                      .build());
            }
            metricBuilder.setIntSum(intSumBuilder.build());
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
          if (metricBuilder.hasIntHistogram()) {
            metricBuilder.clearIntHistogram();
            var intHistogramBuilder = metricBuilder.getIntHistogramBuilder();
            intHistogramBuilder.setAggregationTemporality(
                metric.getIntHistogram().getAggregationTemporality());
            intHistogramBuilder.clearDataPoints();
            for (var datapoint : metric.getIntHistogram().getDataPointsList()) {
              intHistogramBuilder.addDataPoints(
                  datapoint.toBuilder()
                      .clearLabels()
                      .addAllLabels(
                          obfuscateStringKeyValues(datapoint.getLabelsList(), attributeKeys))
                      .build());
            }
            metricBuilder.setIntHistogram(intHistogramBuilder.build());
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
                      .clearLabels()
                      .addAllLabels(
                          obfuscateStringKeyValues(datapoint.getLabelsList(), attributeKeys))
                      .build());
            }
            metricBuilder.setSummary(summaryBuilder.build());
          }
          ilMetricBuilder.addMetrics(metricBuilder.build());
        }
        rMetricBuilder.addInstrumentationLibraryMetrics(ilMetricBuilder.build());
      }
      requestBuilder.addResourceMetrics(rMetricBuilder.build());
    }
    return requestBuilder.build();
  }

  private static ExportMetricsServiceRequest gauge(
      String name, String id, boolean includeStartTime) {
    return metricsRequest(
        metricBuilder(name)
            .setGauge(
                Gauge.newBuilder()
                    .addDataPoints(numberDataPoint(name, id, includeStartTime))
                    .build())
            .build());
  }

  private static ExportMetricsServiceRequest intGauge(
      String name, String id, boolean includeStartTime) {
    return metricsRequest(
        metricBuilder(name)
            .setIntGauge(
                IntGauge.newBuilder().addDataPoints(intDataPoint(id, includeStartTime)).build())
            .build());
  }

  private static ExportMetricsServiceRequest summary(
      String name, String id, boolean includeStartTime) {
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
            .addAllAttributes(allTheAttributes(name + "_"));
    if (includeStartTime) {
      summaryDataPointBuilder.setStartTimeUnixNano(toEpochNano(Instant.now().minusSeconds(10)));
    }
    return metricsRequest(
        metricBuilder(name)
            .setSummary(Summary.newBuilder().addDataPoints(summaryDataPointBuilder.build()).build())
            .build());
  }

  private static ExportMetricsServiceRequest sum(
      String name,
      String id,
      boolean isMonotonic,
      AggregationTemporality aggregationTemporality,
      boolean includeStartTime) {
    return metricsRequest(
        metricBuilder(name)
            .setSum(
                Sum.newBuilder()
                    .setIsMonotonic(isMonotonic)
                    .setAggregationTemporality(aggregationTemporality)
                    .addDataPoints(numberDataPoint(name, id, includeStartTime)))
            .build());
  }

  private static ExportMetricsServiceRequest intSum(
      String name,
      String id,
      boolean isMonotonic,
      AggregationTemporality aggregationTemporality,
      boolean includeStartTime) {
    return metricsRequest(
        metricBuilder(name)
            .setIntSum(
                IntSum.newBuilder()
                    .setIsMonotonic(isMonotonic)
                    .setAggregationTemporality(aggregationTemporality)
                    .addDataPoints(intDataPoint(id, includeStartTime)))
            .build());
  }

  private static ExportMetricsServiceRequest histogram(
      String name,
      String id,
      AggregationTemporality aggregationTemporality,
      boolean includeStartTime) {
    var histogramDataPointBuilder =
        HistogramDataPoint.newBuilder()
            .setTimeUnixNano(toEpochNano(Instant.now()))
            .setCount(11)
            .setSum(100)
            .addAllExplicitBounds(List.of(1.0, 2.0, 3.0))
            .addAllBucketCounts(List.of(5L, 4L, 1L, 1L))
            .addAttributes(idAttribute(id))
            .addAllAttributes(allTheAttributes(name + "_"));
    if (includeStartTime) {
      histogramDataPointBuilder.setStartTimeUnixNano(toEpochNano(Instant.now().minusSeconds(10)));
    }
    return metricsRequest(
        metricBuilder(name)
            .setHistogram(
                Histogram.newBuilder()
                    .setAggregationTemporality(aggregationTemporality)
                    .addDataPoints(histogramDataPointBuilder)
                    .build())
            .build());
  }

  private static ExportMetricsServiceRequest intHistogram(
      String name,
      String id,
      AggregationTemporality aggregationTemporality,
      boolean includeStartTime) {
    var intHistogramDataPointBuilder =
        IntHistogramDataPoint.newBuilder()
            .setTimeUnixNano(toEpochNano(Instant.now()))
            .setCount(11)
            .setSum(100)
            .addAllExplicitBounds(List.of(1.0, 2.0, 3.0))
            .addAllBucketCounts(List.of(5L, 4L, 1L, 1L))
            .addLabels(idStringKeyValue(id))
            .addAllLabels(
                List.of(StringKeyValue.newBuilder().setKey("skey").setValue("value").build()));
    if (includeStartTime) {
      intHistogramDataPointBuilder.setStartTimeUnixNano(
          toEpochNano(Instant.now().minusSeconds(10)));
    }
    return metricsRequest(
        metricBuilder(name)
            .setIntHistogram(
                IntHistogram.newBuilder()
                    .setAggregationTemporality(aggregationTemporality)
                    .addDataPoints(intHistogramDataPointBuilder)
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

  private static NumberDataPoint numberDataPoint(
      String metricName, String id, boolean includeStartTime) {
    var numberDataPointBuilder =
        NumberDataPoint.newBuilder()
            .setTimeUnixNano(toEpochNano(Instant.now()))
            .setAsDouble(1.0)
            .addAttributes(idAttribute(id))
            .addAllAttributes(allTheAttributes(metricName + "_"));
    if (includeStartTime) {
      numberDataPointBuilder.setStartTimeUnixNano(toEpochNano(Instant.now().minusSeconds(10)));
    }
    return numberDataPointBuilder.build();
  }

  private static IntDataPoint intDataPoint(String id, boolean includeStartTime) {
    var intDataPointBuilder =
        IntDataPoint.newBuilder()
            .setStartTimeUnixNano(toEpochNano(Instant.now().minusSeconds(10)))
            .setTimeUnixNano(toEpochNano(Instant.now()))
            .addLabels(idStringKeyValue(id))
            .setValue(100);
    if (includeStartTime) {
      intDataPointBuilder.setStartTimeUnixNano(toEpochNano(Instant.now().minusSeconds(10)));
    }
    return intDataPointBuilder.build();
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
                .addInstrumentationLibraryMetrics(
                    InstrumentationLibraryMetrics.newBuilder()
                        .setInstrumentationLibrary(Common.instrumentationLibrary())
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
                .addInstrumentationLibraryMetrics(
                    InstrumentationLibraryMetrics.newBuilder()
                        .setInstrumentationLibrary(Common.instrumentationLibrary())
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
