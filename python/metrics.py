from typing import Iterable

from opentelemetry.exporter.otlp.proto.grpc.metric_exporter import (
    OTLPMetricExporter,
)
from opentelemetry.metrics import (
    CallbackOptions,
    Observation,
    get_meter_provider,
    set_meter_provider,
)
from opentelemetry.sdk.metrics import MeterProvider
from opentelemetry.sdk.metrics._internal.aggregation import AggregationTemporality
from opentelemetry.sdk.metrics._internal.instrument import (
    Counter,
    ObservableCounter,
    UpDownCounter,
    ObservableUpDownCounter,
    Histogram,
)
from opentelemetry.sdk.metrics.export import PeriodicExportingMetricReader
from opentelemetry.sdk.resources import Resource


exporter = OTLPMetricExporter(insecure=True)
reader = PeriodicExportingMetricReader(exporter, preferred_temporality={
    Counter: AggregationTemporality.DELTA,
    ObservableCounter: AggregationTemporality.DELTA,
    UpDownCounter: AggregationTemporality.CUMULATIVE,
    ObservableUpDownCounter: AggregationTemporality.CUMULATIVE,
    Histogram: AggregationTemporality.DELTA,
})
provider = MeterProvider(metric_readers=[reader], resource=Resource.create({"service.name": "python-app"}))
set_meter_provider(provider)


def observable_counter_func(options: CallbackOptions) -> Iterable[Observation]:
    yield Observation(1, {})


def observable_up_down_counter_func(
    options: CallbackOptions,
) -> Iterable[Observation]:
    yield Observation(-10, {})


def observable_gauge_func(options: CallbackOptions) -> Iterable[Observation]:
    yield Observation(9, {})



def generate_metrics():
    meter = get_meter_provider().get_meter("getting-started", "0.1.2")

    # Counter
    counter = meter.create_counter("counter")
    counter.add(1)

    # Async Counter
    observable_counter = meter.create_observable_counter(
        "observable_counter",
        [observable_counter_func],
    )

    # UpDownCounter
    updown_counter = meter.create_up_down_counter("updown_counter")
    updown_counter.add(1)
    updown_counter.add(-5)

    # Async UpDownCounter
    observable_updown_counter = meter.create_observable_up_down_counter(
        "observable_updown_counter", [observable_up_down_counter_func]
    )

    # Histogram
    histogram = meter.create_histogram("histogram")
    histogram.record(99.9)

    # Async Gauge
    gauge = meter.create_observable_gauge("gauge", [observable_gauge_func])
