package com.newrelic.shared;

import static com.newrelic.shared.EnvUtils.getEnvOrDefault;
import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.SERVICE_INSTANCE_ID;
import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.SERVICE_NAME;

import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.logging.LoggingMetricExporter;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLogEmitterProvider;
import io.opentelemetry.sdk.logs.export.BatchLogProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
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

  public static void configureGlobal(String defaultServiceName) {
    var resource = configureResource(defaultServiceName);

    // Configure traces
    var sdkTracerProviderBuilder =
        SdkTracerProvider.builder()
            .setResource(resource)
            .addSpanProcessor(
                BatchSpanProcessor.builder(
                        OtlpGrpcSpanExporter.builder()
                            .setChannel(
                                OtlpUtil.managedChannel(
                                    OTLP_HOST_SUPPLIER.get(), newRelicApiOrLicenseKey()))
                            .build())
                    .build());
    if (LOG_EXPORTER_ENABLED.get()) {
      sdkTracerProviderBuilder.addSpanProcessor(
          BatchSpanProcessor.builder(new LoggingSpanExporter()).build());
    }
    OpenTelemetrySdk.builder()
        .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
        .setTracerProvider(sdkTracerProviderBuilder.build())
        .buildAndRegisterGlobal();

    // Configure metrics
    var meterProviderBuilder = SdkMeterProvider.builder().setResource(resource);

    meterProviderBuilder.registerMetricReader(
        PeriodicMetricReader.builder(
                OtlpGrpcMetricExporter.builder()
                    .setPreferredTemporality(AggregationTemporality.DELTA)
                    .setChannel(
                        OtlpUtil.managedChannel(
                            OTLP_HOST_SUPPLIER.get(), newRelicApiOrLicenseKey()))
                    .build())
            .setInterval(Duration.ofSeconds(5))
            .newMetricReaderFactory());

    if (LOG_EXPORTER_ENABLED.get()) {
      meterProviderBuilder.registerMetricReader(
          PeriodicMetricReader.builder(new LoggingMetricExporter())
              .setInterval(Duration.ofSeconds(5))
              .newMetricReaderFactory());
    }

    GlobalMeterProvider.set(meterProviderBuilder.build());
  }

  private static Resource configureResource(String defaultServiceName) {
    return Resource.getDefault()
        .merge(
            Resource.builder()
                .put(
                    SERVICE_NAME,
                    getEnvOrDefault("SERVICE_NAME", Function.identity(), defaultServiceName).get())
                .put(SERVICE_INSTANCE_ID, UUID.randomUUID().toString())
                .build());
  }

  public static SdkLogEmitterProvider configureLogSdk(String defaultServiceName) {
    return SdkLogEmitterProvider.builder()
        .setResource(configureResource(defaultServiceName))
        .addLogProcessor(
            BatchLogProcessor.builder(
                    OtlpGrpcLogExporter.builder()
                        .setEndpoint(OTLP_HOST_SUPPLIER.get())
                        .addHeader("api-key", newRelicApiOrLicenseKey())
                        .build())
                .build())
        .build();
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
