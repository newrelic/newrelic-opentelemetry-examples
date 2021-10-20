package com.newrelic.otel.initializer;

import static io.opentelemetry.sdk.metrics.common.InstrumentType.COUNTER;
import static io.opentelemetry.sdk.metrics.common.InstrumentType.HISTOGRAM;
import static io.opentelemetry.sdk.metrics.common.InstrumentType.OBSERVABLE_SUM;
import static io.opentelemetry.sdk.metrics.common.InstrumentType.OBSERVABLE_UP_DOWN_SUM;
import static io.opentelemetry.sdk.metrics.common.InstrumentType.UP_DOWN_COUNTER;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.metrics.SdkMeterProviderConfigurer;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.common.InstrumentType;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.view.Aggregation;
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
   * @param config the config
   */
  @Override
  public void configure(SdkMeterProviderBuilder builder, ConfigProperties config) {
    setAggregation(builder, COUNTER, Aggregation.sum(AggregationTemporality.DELTA));
    setAggregation(builder, UP_DOWN_COUNTER, Aggregation.sum(AggregationTemporality.DELTA));
    setAggregation(builder, OBSERVABLE_SUM, Aggregation.sum(AggregationTemporality.DELTA));
    setAggregation(builder, OBSERVABLE_UP_DOWN_SUM, Aggregation.sum(AggregationTemporality.DELTA));
    setAggregation(
        builder, HISTOGRAM, Aggregation.explicitBucketHistogram(AggregationTemporality.DELTA));
  }

  private static void setAggregation(
      SdkMeterProviderBuilder meterProviderBuilder,
      InstrumentType instrumentType,
      Aggregation aggregation) {
    meterProviderBuilder.registerView(
        InstrumentSelector.builder().setInstrumentType(instrumentType).build(),
        View.builder().setAggregation(aggregation).build());
  }
}
