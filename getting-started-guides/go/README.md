# Getting Started Guide - Go

Welcome to the Open Telemetry / New Relic Getting Started Guide!

## Tutorial 1

Run the [pre-instrumented demo app](./instrumented/): This is the fastest way to send some demo data to New Relic and see how it is displayed in the UI. In this tutorial, the demo app has pre-loaded instrumentation and SDK configurations that follow our best practices to generate and export metrics and traces. You can inspect our code and apply relevant sections to your own apps.

## Tutorial 2:

Set up the [demo app manually](./uninstrumented/): In this track, you'll roll up your sleeves and tinker with the engine of the car. This is the approach to take if you want to have the most control over what telemetry is reported and want to see details about how it's done. You'll manually insert instrumentation into our demo app to capture telemetry and you'll configure the SDK to export that data to New Relic.

## Teaser

### Summary Page - Metric View

![`summary_metric_view`](./media/summary_metric_view.png)

### Summary Page - Trace View

![`summary_trace_view`](./media/summary_trace_view.png)

### Distributed Tracing Page - Span Waterfall View

![`span_waterfall_view`](./media/span_waterfall_view.png)
