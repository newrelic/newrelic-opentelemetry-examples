package com.newrelic.otel.initializer;

import static io.opentelemetry.sdk.metrics.common.InstrumentType.COUNTER;
import static io.opentelemetry.sdk.metrics.common.InstrumentType.OBSERVABLE_SUM;
import static io.opentelemetry.sdk.metrics.common.InstrumentType.OBSERVABLE_UP_DOWN_SUM;
import static io.opentelemetry.sdk.metrics.common.InstrumentType.UP_DOWN_COUNTER;

import io.opentelemetry.sdk.autoconfigure.spi.SdkMeterProviderConfigurer;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.aggregator.AggregatorFactory;
import io.opentelemetry.sdk.metrics.common.InstrumentType;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.processor.LabelsProcessorFactory;
import io.opentelemetry.sdk.metrics.view.InstrumentSelector;
import io.opentelemetry.sdk.metrics.view.View;

public class MetricConfigurer implements SdkMeterProviderConfigurer {

  /**
   * Override the default instrument aggregators defined in {@link
   * io.opentelemetry.sdk.metrics.internal.view.ViewRegistry}. NOTE: This class is referenced in
   * {@code
   * /resources/META-INF/services/io.opentelemetry.sdk.autoconfigure.spi.SdkMeterProviderConfigurer}.
   *
   * @param builder the builder
   */
  @Override
  public void configure(SdkMeterProviderBuilder builder) {
    setDeltaSumAggregatorFactory(builder, COUNTER);
    setDeltaSumAggregatorFactory(builder, UP_DOWN_COUNTER);
    setDeltaSumAggregatorFactory(builder, OBSERVABLE_SUM);
    setDeltaSumAggregatorFactory(builder, OBSERVABLE_UP_DOWN_SUM);
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
}
