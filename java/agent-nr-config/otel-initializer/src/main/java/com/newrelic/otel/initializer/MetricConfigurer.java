package com.newrelic.otel.initializer;

import io.opentelemetry.sdk.autoconfigure.spi.SdkMeterProviderConfigurer;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.aggregator.AggregatorFactory;
import io.opentelemetry.sdk.metrics.common.InstrumentType;
import io.opentelemetry.sdk.metrics.processor.LabelsProcessorFactory;
import io.opentelemetry.sdk.metrics.view.InstrumentSelector;
import io.opentelemetry.sdk.metrics.view.View;

public class MetricConfigurer implements SdkMeterProviderConfigurer {

  /**
   * Override the default instrument aggregators defined in {@link
   * io.opentelemetry.sdk.metrics.ViewRegistry}. NOTE: This class is referenced in {@code
   * /resources/META-INF/services/io.opentelemetry.sdk.autoconfigure.spi.SdkMeterProviderConfigurer}.
   *
   * @param builder the builder
   */
  @Override
  public void configure(SdkMeterProviderBuilder builder) {
    setAggregatorFactory(builder, InstrumentType.COUNTER, AggregatorFactory.sum(false));
    setAggregatorFactory(builder, InstrumentType.UP_DOWN_COUNTER, AggregatorFactory.sum(false));
    setAggregatorFactory(builder, InstrumentType.SUM_OBSERVER, AggregatorFactory.minMaxSumCount());
    setAggregatorFactory(
        builder, InstrumentType.UP_DOWN_SUM_OBSERVER, AggregatorFactory.minMaxSumCount());
  }

  private static void setAggregatorFactory(
      SdkMeterProviderBuilder builder,
      InstrumentType instrumentType,
      AggregatorFactory aggregatorFactory) {
    builder.registerView(
        InstrumentSelector.builder().setInstrumentType(instrumentType).build(),
        View.builder()
            .setLabelsProcessorFactory(LabelsProcessorFactory.noop())
            .setAggregatorFactory(aggregatorFactory)
            .build());
  }
}
