# Trace Sampling

Examples backing the New Relic Trace Sampling Reference Architecture. They build on the [`operator`](../operator) example (OpenTelemetry Operator, `Instrumentation` and `OpenTelemetryCollector` custom resources, the `getting-started-guides` Java and Python apps) and add a trace sampling strategy on top of it.

Trace sampling is applied at the tier that has the information needed to decide:

* The SDK head sampler only sees the root span.
* A Collector agent sees individual spans, so it must not drop spans in a way that splits a trace.
* Only a gateway running the `tail_sampling` processor sees the complete trace.

| Example | Decision made by | Needs | Use when |
| --- | --- | --- | --- |
| [`head-sampling`](./head-sampling) | SDK, at the root span (`parentbased_traceidratio`) | Operator + one Collector Gateway | Controlling volume with no extra infrastructure; keeping a fixed percentage of all traces is acceptable. Diagram: OpenTelemetry Core - Head Sampling. |
| [`newrelic-head-sampling`](./newrelic-head-sampling) | Root service on a New Relic agent (ratio sampler); OTel SDK downstream | Operator, no Collector | Mixed New Relic agent and OpenTelemetry estate exporting straight to New Relic. Diagram: OpenTelemetry + New Relic - Head Sampling. |
| [`tail-sampling`](./tail-sampling) | Collector Gateway, on the complete trace (`tail_sampling`) | Operator + local collector, load balancer and gateway tiers with trace-ID affinity | 100% of error and latency-outlier traces must be kept. Diagram: OpenTelemetry Core - Tail Sampling. |

All examples follow the same rules:

* W3C `traceparent` propagation must work across every hop.
* Use the same sampler and ratio for every service in a trace path.
* Sample after enrichment, so policies can use the attributes the enrichment adds.
* Derive throughput and error-rate SLIs from metrics, not from sampled span counts.

Each example is self-contained and uses its own kind cluster, so they can run side by side. See the README in each directory for steps.
