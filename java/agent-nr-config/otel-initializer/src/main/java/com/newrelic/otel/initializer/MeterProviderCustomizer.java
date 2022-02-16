package com.newrelic.otel.initializer;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.metrics.SdkMeterProviderConfigurer;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.common.InstrumentType;
import io.opentelemetry.sdk.metrics.view.Aggregation;
import io.opentelemetry.sdk.metrics.view.InstrumentSelector;
import io.opentelemetry.sdk.metrics.view.View;

/**
 * Note this class is wired into SPI via {@code
 * resources/META-INF/services/com.newrelic.otel.initializer.SdkMeterProviderConfigurer}
 */
public class MeterProviderCustomizer implements SdkMeterProviderConfigurer {

  @Override
  public void configure(SdkMeterProviderBuilder meterProviderBuilder, ConfigProperties config) {
    // Aggregate OBSERVABLE_UP_DOWN_SUM as gauge instead of sum. This allows OBSERVABLE_UP_DOWN_SUM
    // data to still be useful when aggregation temporality is set to DELTA.
    meterProviderBuilder.registerView(
        InstrumentSelector.builder()
            .setInstrumentType(InstrumentType.OBSERVABLE_UP_DOWN_SUM)
            .build(),
        View.builder().setAggregation(Aggregation.lastValue()).build());
  }
}
