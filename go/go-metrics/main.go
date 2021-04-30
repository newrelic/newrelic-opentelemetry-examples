package main

import (
	"context"
	"log"
	"sync"
	"time"

	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/exporters/otlp"
	"go.opentelemetry.io/otel/exporters/otlp/otlpgrpc"
	"go.opentelemetry.io/otel/metric"
	"go.opentelemetry.io/otel/metric/global"
	metricsdk "go.opentelemetry.io/otel/sdk/export/metric"
	controller "go.opentelemetry.io/otel/sdk/metric/controller/basic"
	processor "go.opentelemetry.io/otel/sdk/metric/processor/basic"
	"go.opentelemetry.io/otel/sdk/metric/selector/simple"
	"go.opentelemetry.io/otel/sdk/resource"
	"go.opentelemetry.io/otel/semconv"
)

func initProvider() func() {
	ctx := context.Background()

	driver := otlpgrpc.NewDriver(
		otlpgrpc.WithInsecure(),
		otlpgrpc.WithEndpoint("localhost:4317"),
	)
	exp, err := otlp.NewExporter(ctx, driver, otlp.WithMetricExportKindSelector(metricsdk.DeltaExportKindSelector()))
	handleErr(err, "failed to create exporter")

	res, err := resource.New(ctx,
		resource.WithAttributes(
			semconv.ServiceNameKey.String("go-metrics"),
		),
	)
	handleErr(err, "failed to create resource")

	cont := controller.New(
		processor.New(
			simple.NewWithExactDistribution(),
			exp,
		),
		controller.WithResource(res),
		controller.WithExporter(exp),
		controller.WithCollectPeriod(2*time.Second),
	)

	global.SetMeterProvider(cont.MeterProvider())
	handleErr(cont.Start(context.Background()), "failed to start controller")

	return func() {
		handleErr(cont.Stop(context.Background()), "failed to stop controller")
	}
}

func main() {
	shutdown := initProvider()
	defer shutdown()

	meter := global.Meter("example-meter")

	labels := []attribute.KeyValue{
		attribute.String("label1", "one"),
		attribute.String("label2", "two"),
		attribute.String("label3", "three"),
	}

	counter := metric.Must(meter).
		NewFloat64Counter(
			"my_counter",
			metric.WithDescription("A Counter"),
		)

	valuerecorder := metric.Must(meter).
		NewFloat64ValueRecorder(
			"my_valuerecorder",
			metric.WithDescription("A ValueRecorder"),
		)

	updowncounter := metric.Must(meter).
		NewFloat64UpDownCounter(
			"my_updowncounter",
			metric.WithDescription("An UpDownCounter"),
		)

	observerLock := new(sync.RWMutex)
	observerValueToReport := new(float64)
	observerLabelsToReport := new([]attribute.KeyValue)
	cb := func(_ context.Context, result metric.Float64ObserverResult) {
		(*observerLock).RLock()
		value := *observerValueToReport
		labels := *observerLabelsToReport
		(*observerLock).RUnlock()
		log.Printf("Observing a value")
		result.Observe(value, labels...)
	}

	(*observerLock).Lock()
	*observerValueToReport = 1.0
	*observerLabelsToReport = labels
	(*observerLock).Unlock()

	_ = metric.Must(meter).NewFloat64ValueObserver("my_valueobserver", cb,
		metric.WithDescription("A ValueObserver"),
	)

	_ = metric.Must(meter).NewFloat64SumObserver("my_sumobserver", cb,
		metric.WithDescription("A SumObserver"),
	)

	ctx := context.Background()
	for i := 0; i < 10; i++ {
		log.Printf("Recording some metrics (%d / 10)\n", i+1)

		meter.RecordBatch(
			ctx,
			labels,
			counter.Measurement(1.0),
			valuerecorder.Measurement(2.0),
			updowncounter.Measurement(3.0),
		)

		<-time.After(time.Second)
	}

	log.Printf("Done!")
}

func handleErr(err error, message string) {
	if err != nil {
		log.Fatalf("%s: %v", message, err)
	}
}
