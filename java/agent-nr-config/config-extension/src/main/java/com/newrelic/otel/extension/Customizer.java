package com.newrelic.otel.extension;

import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.SERVICE_INSTANCE_ID;

import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.metrics.common.InstrumentType;
import io.opentelemetry.sdk.metrics.view.Aggregation;
import io.opentelemetry.sdk.metrics.view.InstrumentSelector;
import io.opentelemetry.sdk.metrics.view.View;
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

    // Aggregate OBSERVABLE_UP_DOWN_COUNTER as gauge instead of sum. This allows
    // OBSERVABLE_UP_DOWN_COUNTER
    // data to still be useful when aggregation temporality is set to DELTA.
    autoConfiguration.addMeterProviderCustomizer(
        (meterProviderBuilder, configProperties) ->
            meterProviderBuilder.registerView(
                InstrumentSelector.builder()
                    .setType(InstrumentType.OBSERVABLE_UP_DOWN_COUNTER)
                    .build(),
                View.builder().setAggregation(Aggregation.lastValue()).build()));
  }
}
