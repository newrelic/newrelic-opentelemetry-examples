package com.newrelic.otel.initializer;

import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.SERVICE_INSTANCE_ID;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.autoconfigure.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;
import java.util.UUID;

public class ResourceConfigurer implements ResourceProvider {

  /**
   * Add additional resource attributes programmatically. NOTE: This class is referenced in {@code
   * /resources/META-INF/services/io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider}.
   *
   * @param config the config
   */
  @Override
  public Resource createResource(ConfigProperties config) {
    return Resource.create(
        Attributes.builder().put(SERVICE_INSTANCE_ID, UUID.randomUUID().toString()).build());
  }
}
