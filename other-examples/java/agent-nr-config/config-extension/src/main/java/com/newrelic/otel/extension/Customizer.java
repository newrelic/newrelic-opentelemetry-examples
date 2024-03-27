package com.newrelic.otel.extension;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.contrib.sampler.RuleBasedRoutingSampler;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.incubating.ServiceIncubatingAttributes;
import java.util.UUID;

/**
 * Note this class is wired into SPI via {@code
 * resources/META-INF/services/io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider}
 */
public class Customizer implements AutoConfigurationCustomizerProvider {

  @Override
  public void customize(AutoConfigurationCustomizer autoConfiguration) {
    // Add additional resource attributes programmatically
    autoConfiguration.addResourceCustomizer(
        (resource, configProperties) ->
            resource.merge(
                Resource.builder()
                    .put(
                        ServiceIncubatingAttributes.SERVICE_INSTANCE_ID,
                        UUID.randomUUID().toString())
                    .build()));

    // Set the sampler to be the default parentbased_always_on, but drop calls to spring
    // boot actuator endpoints
    autoConfiguration.addTracerProviderCustomizer(
        (sdkTracerProviderBuilder, configProperties) ->
            sdkTracerProviderBuilder.setSampler(
                Sampler.parentBased(
                    RuleBasedRoutingSampler.builder(SpanKind.SERVER, Sampler.alwaysOn())
                        // TODO: Update to url.path when semconv 1.22.0 is published and 2.0 version
                        // of otel java agent available
                        .drop(HttpAttributes.HTTP_ROUTE, "/actuator.*")
                        .build())));
  }
}
