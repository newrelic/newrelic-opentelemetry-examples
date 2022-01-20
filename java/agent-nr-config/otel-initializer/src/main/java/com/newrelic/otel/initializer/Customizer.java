package com.newrelic.otel.initializer;

import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.SERVICE_INSTANCE_ID;

import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.resources.Resource;
import java.util.UUID;

public class Customizer implements AutoConfigurationCustomizerProvider {

  /**
   * Add additional resource attributes programmatically. NOTE: This class is referenced in {@code
   * /resources/META-INF/services/io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider}.
   */
  @Override
  public void customize(AutoConfigurationCustomizer autoConfiguration) {
    autoConfiguration.addResourceCustomizer(
        (resource, configProperties) ->
            resource.merge(
                Resource.builder().put(SERVICE_INSTANCE_ID, UUID.randomUUID().toString()).build()));
  }
}
