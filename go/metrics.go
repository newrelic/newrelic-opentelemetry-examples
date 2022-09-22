package main

import (
	"context"
	"log"
	"sync"
	"time"

	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/exporters/otlp/otlpmetric/otlpmetricgrpc"
	"go.opentelemetry.io/otel/metric/global"
	"go.opentelemetry.io/otel/metric/instrument"
	"go.opentelemetry.io/otel/sdk/metric"
	"go.opentelemetry.io/otel/sdk/metric/metricdata"
	"go.opentelemetry.io/otel/sdk/metric/view"
	"go.opentelemetry.io/otel/sdk/resource"
)

var (
	lemonsKey = attribute.Key("ex.com/lemons")
)

func NewRelicTemporalitySelector(kind view.InstrumentKind) metricdata.Temporality {
	if kind == view.SyncUpDownCounter || kind == view.AsyncUpDownCounter {
		return metricdata.CumulativeTemporality
	}
	return metricdata.DeltaTemporality
}

func initMeter(ctx context.Context, res *resource.Resource) {
	exporter, err := otlpmetricgrpc.New(ctx)
	if err != nil {
		log.Fatalf("%s: %v", "failed to create metric exporter", err)
	}

	reader := metric.NewPeriodicReader(
		exporter,
		metric.WithTemporalitySelector(NewRelicTemporalitySelector),
		metric.WithInterval(2*time.Second),
	)

	meterProvider := metric.NewMeterProvider(
		metric.WithResource(res),
		metric.WithReader(reader),
	)
	global.SetMeterProvider(meterProvider)
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
