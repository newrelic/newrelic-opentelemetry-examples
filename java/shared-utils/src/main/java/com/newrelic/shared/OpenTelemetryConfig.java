package com.newrelic.shared;

import static com.newrelic.shared.EnvUtils.getEnvOrDefault;
import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.SERVICE_INSTANCE_ID;
import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.SERVICE_NAME;

import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.logging.LoggingMetricExporter;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.otlp.internal.retry.RetryPolicy;
import io.opentelemetry.exporter.otlp.internal.retry.RetryUtil;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLogEmitterProvider;
import io.opentelemetry.sdk.logs.export.BatchLogProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.common.InstrumentType;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.metrics.view.Aggregation;
import io.opentelemetry.sdk.metrics.view.InstrumentSelector;
import io.opentelemetry.sdk.metrics.view.View;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

public class OpenTelemetryConfig {

  private static final Supplier<String> OTLP_HOST_SUPPLIER =
      getEnvOrDefault("OTLP_HOST", Function.identity(), "https://otlp.nr-data.net:4317");
  private static final Supplier<String> NEW_RELIC_API_KEY_SUPPLIER =
      getEnvOrDefault("NEW_RELIC_API_KEY", Function.identity(), "");
  private static final Supplier<String> NEW_RELIC_LICENSE_KEY_SUPPLIER =
      getEnvOrDefault("NEW_RELIC_LICENSE_KEY", Function.identity(), "");
  private static final Supplier<Boolean> LOG_EXPORTER_ENABLED =
      getEnvOrDefault("LOG_EXPORTER_ENABLED", Boolean::valueOf, true);

  public static OpenTelemetrySdk configureGlobal(String defaultServiceName) {
    // Configure resource
    var resource =
        Resource.getDefault()
            .merge(
                Resource.builder()
                    .put(
                        SERVICE_NAME,
                        getEnvOrDefault("SERVICE_NAME", Function.identity(), defaultServiceName)
                            .get())
                    .put(SERVICE_INSTANCE_ID, UUID.randomUUID().toString())
                    .build());

    // Configure traces
    var spanExporterBuilder =
        OtlpGrpcSpanExporter.builder()
            .setEndpoint(OTLP_HOST_SUPPLIER.get())
            .addHeader("api-key", newRelicApiOrLicenseKey());

    // Enable retry policy via unstable API
    RetryUtil.setRetryPolicyOnDelegate(spanExporterBuilder, RetryPolicy.getDefault());

    var sdkTracerProviderBuilder =
        SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporterBuilder.build()).build());
    if (LOG_EXPORTER_ENABLED.get()) {
      sdkTracerProviderBuilder.addSpanProcessor(
          BatchSpanProcessor.builder(LoggingSpanExporter.create()).build());
    }

    // Configure metrics
    var meterProviderBuilder =
        SdkMeterProvider.builder()
            .setResource(resource)
            // Aggregate OBSERVABLE_UP_DOWN_SUM as gauge instead of sum. This allows OBSERVABLE_UP_DOWN_SUM
            // data to still be useful when aggregation temporality is set to DELTA.
            .registerView(
                InstrumentSelector.builder()
                    .setInstrumentType(InstrumentType.OBSERVABLE_UP_DOWN_SUM)
                    .build(),
                View.builder().setAggregation(Aggregation.lastValue()).build());

    var metricExporterBuilder =
        OtlpGrpcMetricExporter.builder()
            .setPreferredTemporality(AggregationTemporality.DELTA)
            .setEndpoint(OTLP_HOST_SUPPLIER.get())
            .addHeader("api-key", newRelicApiOrLicenseKey());

    // Enable retry policy via unstable API
    RetryUtil.setRetryPolicyOnDelegate(metricExporterBuilder, RetryPolicy.getDefault());

    meterProviderBuilder.registerMetricReader(
        PeriodicMetricReader.builder(metricExporterBuilder.build())
            .setInterval(Duration.ofSeconds(5))
            .newMetricReaderFactory());

    if (LOG_EXPORTER_ENABLED.get()) {
      meterProviderBuilder.registerMetricReader(
          PeriodicMetricReader.builder(LoggingMetricExporter.create())
              .setInterval(Duration.ofSeconds(5))
              .newMetricReaderFactory());
    }

    // Configure logs
    var logExporterBuilder =
        OtlpGrpcLogExporter.builder()
            .setEndpoint(OTLP_HOST_SUPPLIER.get())
            .addHeader("api-key", newRelicApiOrLicenseKey());

    // Enable retry policy via unstable API
    RetryUtil.setRetryPolicyOnDelegate(logExporterBuilder, RetryPolicy.getDefault());

    var logEmitterProvider =
        SdkLogEmitterProvider.builder()
            .setResource(resource)
            .addLogProcessor(BatchLogProcessor.builder(logExporterBuilder.build()).build())
            .build();

    return OpenTelemetrySdk.builder()
        .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
        .setTracerProvider(sdkTracerProviderBuilder.build())
        .setMeterProvider(meterProviderBuilder.build())
        .setLogEmitterProvider(logEmitterProvider)
        .buildAndRegisterGlobal();
  }

  private static String newRelicApiOrLicenseKey() {
    var apiKey = NEW_RELIC_API_KEY_SUPPLIER.get();
    if (!apiKey.isEmpty()) {
      return apiKey;
    }
    return NEW_RELIC_LICENSE_KEY_SUPPLIER.get();
  }

  private OpenTelemetryConfig() {}
}
