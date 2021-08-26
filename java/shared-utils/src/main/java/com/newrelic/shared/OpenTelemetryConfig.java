package com.newrelic.shared;

import static com.newrelic.shared.EnvUtils.getEnvOrDefault;
import static io.opentelemetry.sdk.metrics.common.InstrumentType.COUNTER;
import static io.opentelemetry.sdk.metrics.common.InstrumentType.OBSERVABLE_SUM;
import static io.opentelemetry.sdk.metrics.common.InstrumentType.OBSERVABLE_UP_DOWN_SUM;
import static io.opentelemetry.sdk.metrics.common.InstrumentType.UP_DOWN_COUNTER;
import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.SERVICE_INSTANCE_ID;
import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.SERVICE_NAME;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.logging.LoggingMetricExporter;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.aggregator.AggregatorFactory;
import io.opentelemetry.sdk.metrics.common.InstrumentType;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.export.IntervalMetricReader;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import io.opentelemetry.sdk.metrics.processor.LabelsProcessorFactory;
import io.opentelemetry.sdk.metrics.view.InstrumentSelector;
import io.opentelemetry.sdk.metrics.view.View;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

public class OpenTelemetryConfig {

  private static final Supplier<String> OTLP_HOST_SUPPLIER =
      getEnvOrDefault("OTLP_HOST", Function.identity(), "http://localhost:4317");
  private static final Supplier<String> NEW_RELIC_API_KEY_SUPPLIER =
      getEnvOrDefault("NEW_RELIC_API_KEY", Function.identity(), "");
  private static final Supplier<Boolean> LOG_EXPORTER_ENABLED =
      getEnvOrDefault("LOG_EXPORTER_ENABLED", Boolean::valueOf, false);

  public static void configureGlobal(String defaultServiceName) {
    var serviceName =
        getEnvOrDefault("SERVICE_NAME", Function.identity(), defaultServiceName).get();
    // Configure traces
    GlobalOpenTelemetry.set(openTelemetrySdk(serviceName));

    // Configure metrics
    SdkMeterProvider sdkMeterProvider = meterProvider(serviceName);
    GlobalMeterProvider.set(sdkMeterProvider);
    intervalMetricReader(sdkMeterProvider, otlpMetricExporter()).start();
    if (LOG_EXPORTER_ENABLED.get()) {
      intervalMetricReader(sdkMeterProvider, new LoggingMetricExporter()).start();
    }
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

  public static OpenTelemetrySdk openTelemetrySdk(String serviceName) {
    return OpenTelemetrySdk.builder()
        .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
        .setTracerProvider(tracerProvider(serviceName))
        .build();
  }

  public static SdkTracerProvider tracerProvider(String serviceName) {
    var sdkTracerProviderBuilder =
        SdkTracerProvider.builder()
            .setResource(resource(serviceName))
            .addSpanProcessor(
                BatchSpanProcessor.builder(
                        OtlpGrpcSpanExporter.builder()
                            .setChannel(
                                OtlpUtil.managedChannel(
                                    OTLP_HOST_SUPPLIER.get(), NEW_RELIC_API_KEY_SUPPLIER.get()))
                            .build())
                    .build());

    if (LOG_EXPORTER_ENABLED.get()) {
      sdkTracerProviderBuilder.addSpanProcessor(
          BatchSpanProcessor.builder(new LoggingSpanExporter()).build());
    }

    var sdkTracerProvider = sdkTracerProviderBuilder.build();

    Runtime.getRuntime().addShutdownHook(new Thread(sdkTracerProvider::close));

    return sdkTracerProvider;
  }

  public static SdkMeterProvider meterProvider(String serviceName) {
    var meterProviderBuilder = SdkMeterProvider.builder().setResource(resource(serviceName));
    // Override default cumulative sum aggregator with delta sum aggregator
    setDeltaSumAggregatorFactory(meterProviderBuilder, COUNTER);
    setDeltaSumAggregatorFactory(meterProviderBuilder, UP_DOWN_COUNTER);
    setDeltaSumAggregatorFactory(meterProviderBuilder, OBSERVABLE_SUM);
    setDeltaSumAggregatorFactory(meterProviderBuilder, OBSERVABLE_UP_DOWN_SUM);
    return meterProviderBuilder.build();
  }

  private static void setDeltaSumAggregatorFactory(
      SdkMeterProviderBuilder meterProviderBuilder, InstrumentType instrumentType) {
    meterProviderBuilder.registerView(
        InstrumentSelector.builder().setInstrumentType(instrumentType).build(),
        View.builder()
            .setAggregatorFactory(AggregatorFactory.sum(AggregationTemporality.DELTA))
            .setLabelsProcessorFactory(LabelsProcessorFactory.noop())
            .build());
  }

  public static OtlpGrpcMetricExporter otlpMetricExporter() {
    return OtlpGrpcMetricExporter.builder()
        .setChannel(
            OtlpUtil.managedChannel(OTLP_HOST_SUPPLIER.get(), NEW_RELIC_API_KEY_SUPPLIER.get()))
        .build();
  }

  public static IntervalMetricReader intervalMetricReader(
      SdkMeterProvider meterProvider, MetricExporter metricExporter) {
    var reader =
        IntervalMetricReader.builder()
            .setMetricExporter(metricExporter)
            .setExportIntervalMillis(5000)
            .setMetricProducers(List.of(meterProvider))
            .build();

    Runtime.getRuntime().addShutdownHook(new Thread(reader::shutdown));

    return reader;
  }

  private OpenTelemetryConfig() {}
}
