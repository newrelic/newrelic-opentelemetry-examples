package main

import (
	"context"
	"fmt"
	"log"
	"sync"
	"time"

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

var (
	lemonsKey = attribute.Key("ex.com/lemons")

	// This configures temporality to work correctly with New Relic for most metric types.
	// Measurements recorded with UpDownCounter instruments are the exception.
	// UpDownCounters should be aggregated with cumulative temporality.
	// TODO: Update this example with a custom temporality selector that correctly handles UpDownCounters.
	temporalitySelector = aggregation.DeltaTemporalitySelector()
)

func initMeter() {
	ctx := context.Background()

	res, err := resource.New(ctx,
		resource.WithAttributes(
			attribute.String("service.name", "OpenTelemetry-Go-Example"),
		),
	)
	if err != nil {
		log.Fatalf("%s: %v", "failed to create resource", err)
	}

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

func main() {
	initMeter()

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

	commonLabels := []attribute.KeyValue{lemonsKey.Int(10), attribute.String("A", "1"), attribute.String("B", "2"), attribute.String("C", "3")}
	notSoCommonLabels := []attribute.KeyValue{lemonsKey.Int(13)}

	ctx := context.Background()

	(*observerLock).Lock()
	*observerValueToReport = 1.0
	*observerLabelsToReport = commonLabels
	(*observerLock).Unlock()

	histogram.Record(ctx, 2.0, commonLabels...)
	counter.Add(ctx, 12.0, commonLabels...)

	time.Sleep(5 * time.Second)

	(*observerLock).Lock()
	*observerValueToReport = 1.0
	*observerLabelsToReport = notSoCommonLabels
	(*observerLock).Unlock()
	histogram.Record(ctx, 2.0, notSoCommonLabels...)
	counter.Add(ctx, 22.0, notSoCommonLabels...)

	time.Sleep(5 * time.Second)

	(*observerLock).Lock()
	*observerValueToReport = 13.0
	*observerLabelsToReport = commonLabels
	(*observerLock).Unlock()
	histogram.Record(ctx, 12.0, commonLabels...)
	counter.Add(ctx, 13.0, commonLabels...)

	fmt.Println("Metric recording complete. Ctrl-C to exit.")

	select {}
}
