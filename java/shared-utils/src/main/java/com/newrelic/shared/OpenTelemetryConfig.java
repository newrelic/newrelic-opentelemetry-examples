package com.newrelic.shared;

import static io.opentelemetry.sdk.metrics.common.InstrumentType.COUNTER;
import static io.opentelemetry.sdk.metrics.common.InstrumentType.SUM_OBSERVER;
import static io.opentelemetry.sdk.metrics.common.InstrumentType.UP_DOWN_COUNTER;
import static io.opentelemetry.sdk.metrics.common.InstrumentType.UP_DOWN_SUM_OBSERVER;
import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.SERVICE_INSTANCE_ID;
import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.SERVICE_NAME;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
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

  public static void configureGlobal(String serviceName) {
    // Configure traces
    GlobalOpenTelemetry.set(openTelemetrySdk(serviceName));

    // Configure metrics
    SdkMeterProvider sdkMeterProvider = meterProvider(serviceName);
    GlobalMeterProvider.set(sdkMeterProvider);
    intervalMetricReader(sdkMeterProvider).start();
  }

  public static Resource resource(String serviceName) {
    return Resource.getDefault()
        .merge(
            Resource.create(
                Attributes.builder()
                    .put(SERVICE_NAME, serviceName)
                    .put(SERVICE_INSTANCE_ID, UUID.randomUUID().toString())
                    .build()));
  }

  public static String otlpEndpoint() {
    return "http://localhost:4317";
  }

  public static OpenTelemetrySdk openTelemetrySdk(String serviceName) {
    return OpenTelemetrySdk.builder()
        .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
        .setTracerProvider(tracerProvider(serviceName))
        .build();
  }

  public static SdkTracerProvider tracerProvider(String serviceName) {
    return SdkTracerProvider.builder()
        .setResource(resource(serviceName))
        .addSpanProcessor(
            BatchSpanProcessor.builder(
                    OtlpGrpcSpanExporter.builder()
                        .setEndpoint(otlpEndpoint())
                        .addHeader("header-name", "header-value")
                        .build())
                .build())
        .build();
  }

  public static SdkMeterProvider meterProvider(String serviceName) {
    var meterProviderBuilder = SdkMeterProvider.builder().setResource(resource(serviceName));
    setAggregatorFactory(meterProviderBuilder, COUNTER, AggregatorFactory.sum(false));
    setAggregatorFactory(meterProviderBuilder, UP_DOWN_COUNTER, AggregatorFactory.sum(false));
    setAggregatorFactory(meterProviderBuilder, SUM_OBSERVER, AggregatorFactory.minMaxSumCount());
    setAggregatorFactory(
        meterProviderBuilder, UP_DOWN_SUM_OBSERVER, AggregatorFactory.minMaxSumCount());
    return meterProviderBuilder.build();
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

  public static IntervalMetricReader intervalMetricReader(SdkMeterProvider meterProvider) {
    return IntervalMetricReader.builder()
        .setMetricExporter(
            OtlpGrpcMetricExporter.builder()
                .setEndpoint(otlpEndpoint())
                .addHeader("header-name", "header-value")
                .build())
        .setExportIntervalMillis(5000)
        .setMetricProducers(List.of(meterProvider))
        .build();
  }

  private OpenTelemetryConfig() {}
}
