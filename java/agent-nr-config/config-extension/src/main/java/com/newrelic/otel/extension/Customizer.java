package com.newrelic.otel.extension;

import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.SERVICE_INSTANCE_ID;

import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.resources.Resource;
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
                Resource.builder().put(SERVICE_INSTANCE_ID, UUID.randomUUID().toString()).build()));
  }
}
