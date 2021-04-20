package com.newrelic.app;

import static io.opentelemetry.sdk.metrics.common.InstrumentType.COUNTER;
import static io.opentelemetry.sdk.metrics.common.InstrumentType.SUM_OBSERVER;
import static io.opentelemetry.sdk.metrics.common.InstrumentType.UP_DOWN_COUNTER;
import static io.opentelemetry.sdk.metrics.common.InstrumentType.UP_DOWN_SUM_OBSERVER;
import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.SERVICE_INSTANCE_ID;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.aggregator.AggregatorFactory;
import io.opentelemetry.sdk.metrics.common.InstrumentType;
import io.opentelemetry.sdk.metrics.export.IntervalMetricReader;
import io.opentelemetry.sdk.metrics.processor.LabelsProcessorFactory;
import io.opentelemetry.sdk.metrics.view.InstrumentSelector;
import io.opentelemetry.sdk.metrics.view.View;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import java.util.List;
import java.util.UUID;

public class OpenTelemetryConfig {

  static void configure() {
    var otlpEndpoint = "http://localhost:4317";

    // Configure resource
    var resource =
        Resource.getDefault()
            .merge(
                Resource.create(
                    Attributes.builder()
                        .put(SERVICE_INSTANCE_ID, UUID.randomUUID().toString())
                        .build()));

    // Configure traces
    var openTelemetrySdk =
        OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .setResource(resource)
                    .addSpanProcessor(
                        BatchSpanProcessor.builder(
                                OtlpGrpcSpanExporter.builder()
                                    .setEndpoint(otlpEndpoint)
                                    .addHeader("header-name", "header-value")
                                    .build())
                            .build())
                    .build())
            .build();
    GlobalOpenTelemetry.set(openTelemetrySdk);

    // Configure metrics
    var meterProviderBuilder = SdkMeterProvider.builder().setResource(resource);
    // Change default aggregations to delta variants
    setAggregatorFactory(meterProviderBuilder, COUNTER, AggregatorFactory.sum(false));
    setAggregatorFactory(meterProviderBuilder, UP_DOWN_COUNTER, AggregatorFactory.sum(false));
    setAggregatorFactory(meterProviderBuilder, SUM_OBSERVER, AggregatorFactory.minMaxSumCount());
    setAggregatorFactory(
        meterProviderBuilder, UP_DOWN_SUM_OBSERVER, AggregatorFactory.minMaxSumCount());
    var meterProvider = meterProviderBuilder.buildAndRegisterGlobal();
    IntervalMetricReader.builder()
        .setMetricExporter(
            OtlpGrpcMetricExporter.builder()
                .setEndpoint(otlpEndpoint)
                .addHeader("header-name", "header-value")
                .build())
        .setExportIntervalMillis(5000)
        .setMetricProducers(List.of(meterProvider))
        .build()
        .start();
  }

  private static void setAggregatorFactory(
      SdkMeterProviderBuilder meterProviderBuilder,
      InstrumentType instrumentType,
      AggregatorFactory aggregatorFactory) {
    meterProviderBuilder.registerView(
        InstrumentSelector.builder().setInstrumentType(instrumentType).build(),
        View.builder()
            .setAggregatorFactory(aggregatorFactory)
            .setLabelsProcessorFactory(LabelsProcessorFactory.noop())
            .build());
  }

  private OpenTelemetryConfig() {}
}
