package com.newrelic.app;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.logging.LoggingMetricExporter;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.instrumentation.log4j.appender.v2_17.OpenTelemetryAppender;
import io.opentelemetry.instrumentation.runtimemetrics.java8.BufferPools;
import io.opentelemetry.instrumentation.runtimemetrics.java8.Classes;
import io.opentelemetry.instrumentation.runtimemetrics.java8.Cpu;
import io.opentelemetry.instrumentation.runtimemetrics.java8.GarbageCollector;
import io.opentelemetry.instrumentation.runtimemetrics.java8.MemoryPools;
import io.opentelemetry.instrumentation.runtimemetrics.java8.Threads;
import io.opentelemetry.instrumentation.spring.webmvc.v6_0.SpringWebMvcTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.export.RetryPolicy;
import io.opentelemetry.sdk.logs.LogLimits;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector;
import io.opentelemetry.sdk.metrics.export.DefaultAggregationSelector;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanLimits;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import jakarta.servlet.Filter;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application {

  public static void main(String[] args) {
    // Configure OpenTelemetry as early as possible
    var openTelemetrySdk = openTelemetrySdk();

    // Register runtime metrics instrumentation
    BufferPools.registerObservers(openTelemetrySdk);
    Classes.registerObservers(openTelemetrySdk);
    Cpu.registerObservers(openTelemetrySdk);
    GarbageCollector.registerObservers(openTelemetrySdk);
    MemoryPools.registerObservers(openTelemetrySdk);
    Threads.registerObservers(openTelemetrySdk);

    SpringApplication.run(Application.class, args);

    // Setup log4j OpenTelemetryAppender
    // Normally this is done before the framework (Spring) is initialized. However, spring boot
    // erases any programmatic log configuration so we must initialize after Spring. Unfortunately,
    // this means that Spring startup logs do not make it to the OpenTelemetry.
    // See this issue for tracking: https://github.com/spring-projects/spring-boot/issues/25847
    OpenTelemetryAppender.install(openTelemetrySdk);
  }

  private static OpenTelemetrySdk openTelemetrySdk() {
    var logExporterEnabled = getEnvOrDefault("LOG_EXPORTER_ENABLED", Boolean::valueOf, false);
    var newrelicApiOrLicenseKey =
        getEnvOrDefault(
            "NEW_RELIC_API_KEY",
            Function.identity(),
            getEnvOrDefault("NEW_RELIC_LICENSE_KEY", Function.identity(), ""));
    var newrelicOtlpEndpoint =
        getEnvOrDefault("OTLP_HOST", Function.identity(), "https://otlp.nr-data.net:4317");

    // Configure resource
    var resource =
        Resource.getDefault().toBuilder()
            .put(ResourceAttributes.SERVICE_NAME, "sdk-nr-config")
            .put(ResourceAttributes.SERVICE_INSTANCE_ID, UUID.randomUUID().toString())
            .build();

    // Configure tracer provider
    var sdkTracerProviderBuilder =
        SdkTracerProvider.builder()
            .setResource(resource)
            // New Relic's max attribute length is 4095 characters
            .setSpanLimits(
                SpanLimits.getDefault().toBuilder().setMaxAttributeValueLength(4095).build())
            // Add batch processor with otlp span exporter
            .addSpanProcessor(
                BatchSpanProcessor.builder(
                        OtlpGrpcSpanExporter.builder()
                            .setEndpoint(newrelicOtlpEndpoint)
                            .setCompression("gzip")
                            .addHeader("api-key", newrelicApiOrLicenseKey)
                            .setRetryPolicy(RetryPolicy.getDefault())
                            .build())
                    .build());
    // Maybe add log exporter
    if (logExporterEnabled) {
      sdkTracerProviderBuilder.addSpanProcessor(
          SimpleSpanProcessor.create(LoggingSpanExporter.create()));
    }

    // Configure meter provider
    var sdkMeterProviderBuilder =
        SdkMeterProvider.builder()
            .setResource(resource)
            // Add periodic metric reader with otlp metric exporter
            .registerMetricReader(
                PeriodicMetricReader.builder(
                        OtlpGrpcMetricExporter.builder()
                            .setEndpoint(newrelicOtlpEndpoint)
                            .setCompression("gzip")
                            .addHeader("api-key", newrelicApiOrLicenseKey)
                            // IMPORTANT: New Relic requires metrics to be delta temporality
                            .setAggregationTemporalitySelector(
                                AggregationTemporalitySelector.deltaPreferred())
                            // Use exponential histogram aggregation for histogram instruments to
                            // produce better data and compression
                            .setDefaultAggregationSelector(
                                DefaultAggregationSelector.getDefault()
                                    .with(
                                        InstrumentType.HISTOGRAM,
                                        Aggregation.base2ExponentialBucketHistogram()))
                            .setRetryPolicy(RetryPolicy.getDefault())
                            .build())
                    .build());
    // Maybe add log exporter
    if (logExporterEnabled) {
      sdkMeterProviderBuilder.registerMetricReader(
          PeriodicMetricReader.builder(LoggingMetricExporter.create(AggregationTemporality.DELTA))
              .build());
    }

    // Configure logger provider
    var sdkLoggerProvider =
        SdkLoggerProvider.builder()
            // New Relic's max attribute length is 4095 characters
            .setLogLimits(
                () -> LogLimits.getDefault().toBuilder().setMaxAttributeValueLength(4095).build())
            .setResource(resource)
            // Add batch processor with otlp log record exporter
            .addLogRecordProcessor(
                BatchLogRecordProcessor.builder(
                        OtlpGrpcLogRecordExporter.builder()
                            .setEndpoint(newrelicOtlpEndpoint)
                            .setCompression("gzip")
                            .addHeader("api-key", newrelicApiOrLicenseKey)
                            .build())
                    .build());

    // Bring it all together
    return OpenTelemetrySdk.builder()
        .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
        .setTracerProvider(sdkTracerProviderBuilder.build())
        .setMeterProvider(sdkMeterProviderBuilder.build())
        .setLoggerProvider(sdkLoggerProvider.build())
        .buildAndRegisterGlobal();
  }

  private static <T> T getEnvOrDefault(
      String key, Function<String, T> transformer, T defaultValue) {
    return Optional.ofNullable(System.getenv(key))
        .filter(s -> !s.isBlank())
        .or(() -> Optional.ofNullable(System.getProperty(key)))
        .filter(s -> !s.isBlank())
        .map(transformer)
        .orElse(defaultValue);
  }

  /** Add Spring WebMVC instrumentation by registering tracing filter. */
  @Bean
  public Filter webMvcTracingFilter() {
    return SpringWebMvcTelemetry.create(GlobalOpenTelemetry.get()).createServletFilter();
  }
}
