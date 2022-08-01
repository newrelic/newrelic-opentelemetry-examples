package main

import (
	"context"
	"log"
	"sync"
	"time"

	"go.opentelemetry.io/otel/sdk/metric/sdkapi"

	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/exporters/otlp/otlpmetric"
	"go.opentelemetry.io/otel/exporters/otlp/otlpmetric/otlpmetricgrpc"
	"go.opentelemetry.io/otel/metric/global"
	"go.opentelemetry.io/otel/metric/instrument"
	"go.opentelemetry.io/otel/sdk/metric/aggregator/histogram"
	controller "go.opentelemetry.io/otel/sdk/metric/controller/basic"
	"go.opentelemetry.io/otel/sdk/metric/export/aggregation"
	processor "go.opentelemetry.io/otel/sdk/metric/processor/basic"
	selector "go.opentelemetry.io/otel/sdk/metric/selector/simple"
	"go.opentelemetry.io/otel/sdk/resource"
)

type (
	newRelicTemporalitySelector struct{}
)

func (s newRelicTemporalitySelector) TemporalityFor(desc *sdkapi.Descriptor, kind aggregation.Kind) aggregation.Temporality {
	if desc.InstrumentKind() == sdkapi.CounterInstrumentKind ||
		// The Go SDK doesn't support Async Observers with Delta temporality yet.
		// To avoid errors, use cumulative for Async Counters, which NR will interpret as gauges.
		// desc.InstrumentKind() == sdkapi.CounterObserverInstrumentKind ||
		desc.InstrumentKind() == sdkapi.HistogramInstrumentKind {
		return aggregation.DeltaTemporality
	}
	return aggregation.CumulativeTemporality
}

func NewRelicTemporalitySelector() aggregation.TemporalitySelector {
	return newRelicTemporalitySelector{}
}

var (
	lemonsKey = attribute.Key("ex.com/lemons")

	// This configures temporality to work correctly with New Relic for all metric types.
	// Counters and Histograms instruments use delta, everything else uses cumulative.
	temporalitySelector = NewRelicTemporalitySelector()
)

func initMeter(ctx context.Context, res *resource.Resource) {
	exporter, err := otlpmetric.New(
		ctx,
		otlpmetricgrpc.NewClient(),
		otlpmetric.WithMetricAggregationTemporalitySelector(temporalitySelector))
	if err != nil {
		log.Fatalf("%s: %v", "failed to create metric exporter", err)
	}

	cont := controller.New(
		processor.NewFactory(
			selector.NewWithHistogramDistribution(
				histogram.WithExplicitBoundaries([]float64{1, 2, 5, 10, 20, 50}),
			),
			temporalitySelector,
		),
		controller.WithResource(res),
		controller.WithExporter(exporter),
		controller.WithCollectPeriod(2*time.Second),
	)

	err = cont.Start(ctx)
	if err != nil {
		log.Fatalf("%s: %v", "failed to start controller", err)
	}

	global.SetMeterProvider(cont)
}

func generateMetrics() {
	meter := global.Meter("ex.com/basic")

	observerLock := new(sync.RWMutex)
	observerValueToReport := new(float64)
	observerLabelsToReport := new([]attribute.KeyValue)

	gaugeObserver, err := meter.AsyncFloat64().Gauge("ex.com.one")
	if err != nil {
		log.Panicf("failed to initialize instrument: %v", err)
	}
	_ = meter.RegisterCallback([]instrument.Asynchronous{gaugeObserver}, func(ctx context.Context) {
		(*observerLock).RLock()
		value := *observerValueToReport
		labels := *observerLabelsToReport
		(*observerLock).RUnlock()
		gaugeObserver.Observe(ctx, value, labels...)
	})

	histogram, err := meter.SyncFloat64().Histogram("ex.com.two")
	if err != nil {
		log.Panicf("failed to initialize instrument: %v", err)
	}
	counter, err := meter.SyncFloat64().Counter("ex.com.three")
	if err != nil {
		log.Panicf("failed to initialize instrument: %v", err)
	}
	upDownCounter, err := meter.SyncFloat64().UpDownCounter("ex.com.four")
	if err != nil {
		log.Panicf("failed to initialize instrument: %v", err)
	}

	commonLabels := []attribute.KeyValue{lemonsKey.Int(10), attribute.String("A", "1"), attribute.String("B", "2"), attribute.String("C", "3")}
	notSoCommonLabels := []attribute.KeyValue{lemonsKey.Int(13)}

	ctx := context.Background()

	(*observerLock).Lock()
	*observerValueToReport = 1.0
	*observerLabelsToReport = commonLabels
	(*observerLock).Unlock()

	histogram.Record(ctx, 2.0, commonLabels...)
	counter.Add(ctx, 12.0, commonLabels...)
	upDownCounter.Add(ctx, 40.0, commonLabels...)

	time.Sleep(5 * time.Second)

	(*observerLock).Lock()
	*observerValueToReport = 1.0
	*observerLabelsToReport = notSoCommonLabels
	(*observerLock).Unlock()
	histogram.Record(ctx, 2.0, notSoCommonLabels...)
	counter.Add(ctx, 22.0, notSoCommonLabels...)
	upDownCounter.Add(ctx, 20.0, notSoCommonLabels...)

	time.Sleep(5 * time.Second)

	(*observerLock).Lock()
	*observerValueToReport = 13.0
	*observerLabelsToReport = commonLabels
	(*observerLock).Unlock()
	histogram.Record(ctx, 12.0, commonLabels...)
	counter.Add(ctx, 13.0, commonLabels...)
	upDownCounter.Add(ctx, 50.0, commonLabels...)
}
