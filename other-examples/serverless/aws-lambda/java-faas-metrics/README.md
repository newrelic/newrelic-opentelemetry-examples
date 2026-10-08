# FaaS Metrics via Spanmetrics Connector (Java)

This example explores whether the OpenTelemetry Collector's `spanmetricsconnector`
can generate accurate, non-sampled FaaS metrics — specifically the semantic
convention `faas.invoke_duration` histogram — from an instrumented AWS Lambda
function's spans, as an alternative to synthesizing APM metrics from (sampled)
span data. To make that comparison realistic, the collector also simulates a
real deployment's head/probabilistic trace sampling rather than keeping 100%
of spans as trace data — see **Design notes** below.

Tail sampling at the collector is explicitly out of scope here — this
example only proves out the metric-generation half of that story.

**Verification status:** the full pipeline — real AWS Lambda deployment,
real collector, live New Relic NRQL validation — has been run end-to-end
successfully. Real traffic produced real `faas.invocations` and
`faas.invoke_duration` data in New Relic, confirmed via NRQL. Three
non-obvious real-deployment issues were found and fixed along the way (see
**Real-deployment findings** below); none of them show up when only running
locally via `sam local` + docker-compose, which is why they weren't caught
until an actual AWS deployment was tried.

## Prerequisites

* A New Relic account and [license key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#ingest-keys).
* AWS credentials configured locally (SAM needs these to download the real
  AWS-managed Lambda layer content for local emulation, even though nothing
  gets deployed to AWS).
* [SAM CLI](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-sam-cli-install.html)
* Docker and Docker Compose
* A JDK Gradle 8.10 can run on (JDK 21 recommended; JDK 25 will not work —
  Gradle itself fails to start on it with "Unsupported class file major
  version 69"). Point `JAVA_HOME` at it when running `sam build`/`gradlew`.
  `ExampleFunction/build.gradle` also pins a Gradle toolchain to Java 21 for
  the compiled bytecode, independent of whichever JDK launches Gradle
  itself.

## Run

### Locally (no AWS deployment)

```bash
export NEW_RELIC_API_KEY=<your license key here>
./run-local.sh
```

This does everything in one command: looks up the current ADOT Java layer
ARN itself, starts the collector, builds and starts the function under
`sam local`, sends 100 requests, and tears everything down when it exits.
Set `REQUEST_COUNT=N` to send a different number, `AWS_REGION=...` for a
region other than `us-east-1`, or `TRACE_SAMPLING_PERCENTAGE=N` (default
`10`) for a different percentage of spans kept as trace data — see **Design
notes** below. It fails loudly (exit code 1, with the
relevant log tail) rather than silently if anything in the chain doesn't
actually work — see **Troubleshooting** below for a real failure mode this
guards against.

To run the individual steps yourself instead, read `run-local.sh` — it's a
straight-line translation of: look up the layer ARN
(`scripts/get-otel-layer-arn.sh <region>`), `docker compose up -d`,
`sam build`, `sam local start-api --docker-network faas-metrics-net
--parameter-overrides "otelLambdaLayerArn=... otelExporterOtlpEndpoint=http://collector:4318"
--port 3000`, then `./scripts/generate-traffic.sh http://127.0.0.1:3000/ 100 traffic-results.csv`
in another terminal.

### Real AWS deployment

```bash
export AWS_PROFILE=<your AWS CLI profile>
export NEW_RELIC_API_KEY=<your license key here>
./deploy-aws.sh
```

`NEW_RELIC_OTLP_ENDPOINT` defaults to production (`https://otlp.nr-data.net`)
if unset. Override it to send to a different NR region or environment, e.g.
staging:

```bash
AWS_PROFILE=<your AWS CLI profile> NEW_RELIC_API_KEY=<your staging license key> \
  NEW_RELIC_OTLP_ENDPOINT=https://staging-otlp.nr-data.net:4318 ./deploy-aws.sh
```

(A production license key will not authenticate against a staging
endpoint, or vice versa - make sure the key and endpoint match.)

`TRACE_SAMPLING_PERCENTAGE` similarly defaults to `10` and can be overridden
the same way — see **Design notes** below for what it controls.

One command: stands up a throwaway EC2 instance running the same collector
(a real, reachable collector is required - see **Real-deployment findings**
below for why), deploys the function + API Gateway pointed at it, and sends
100 requests. State is saved to `.deploy-state` (gitignored) so you don't
have to track resource IDs yourself. **Security note:** this opens the
collector's port to `0.0.0.0/0` for the duration - fine for a short-lived
example, not something to leave running. When you're done:

```bash
./teardown-aws.sh
```

Both scripts fail fast with a clear message if a required variable isn't
set.

### Troubleshooting

If `run-local.sh` fails with a Docker error like `Credentials store error:
StoreError('Credentials store docker-credential-gcloud exited with "")`,
that's a local Docker credential-helper misconfiguration (commonly from
having Google Cloud's registry helper configured globally), not a problem
with this example - SAM's local Lambda emulation fails to build its
container until that's fixed on your machine.

## Design notes

**Why only FaaS-invocation spans count toward the metric.** Per the FaaS
semantic conventions, a span representing a function invocation is of kind
`SERVER` — but `span.kind == SERVER` alone isn't FaaS-specific. A collector
shared across multiple services (a realistic deployment shape) could see
`SERVER` spans from non-Lambda services too, and those shouldn't count
toward `faas.invocations`/`faas.invoke_duration`. `collector/collector.yaml`'s
`filter/invocation_only` processor narrows this with
`resource.attributes["faas.name"] == nil` — `faas.name` is a *resource*
attribute (not a span attribute, hence the `resource.` OTTL path) at
`Required` level in the FaaS semantic conventions, which makes it a more
reliably-populated marker of "this is actually a FaaS invocation" than
span-level attributes like `faas.trigger`, which aren't yet consistently
implemented across every language's Lambda instrumentation.

**Why trace sampling doesn't affect the metrics.** A real deployment
wouldn't keep 100% of spans as trace data, so `collector/collector.yaml`
applies a `probabilistic_sampler` (`TRACE_SAMPLING_PERCENTAGE`, default
`10`) to simulate that. It's wired into the `traces` pipeline only, never
`traces/spanmetrics` — the `spanmetrics` connector needs to see every span
to produce accurate, non-sampled `faas.invocations`/`faas.invoke_duration`.
Sampling before the connector (or at the SDK level, e.g.
`OTEL_TRACES_SAMPLER=traceidratio`) would make those metrics just as
approximate as the sampled span data they're meant to improve on, which
would defeat the point of this example.

## Validate in New Relic

Wait about a minute (the collector's default `metrics_flush_interval` is
60s), then run in New Relic:

```sql
FROM Metric SELECT sum(faas.invocations) WHERE service.name = 'java-faas-metrics-example' SINCE 10 minutes ago
```
Expected: equals the number of requests you sent (e.g. 100), regardless of
`TRACE_SAMPLING_PERCENTAGE` — the `spanmetrics` connector sits ahead of the
sampler (see **Design notes** above) and always sees every span. Use
`sum()`, not `count()` — `count()` on a Metric data type counts data points
(roughly one per flush per series), not the counter's own value.
`count(faas.invoke_duration)` (counting histogram observations) should
agree with it as a cross-check.

```sql
FROM Span SELECT count(*) WHERE service.name = 'java-faas-metrics-example' AND span.kind = 'server' SINCE 10 minutes ago
```
Expected: roughly `TRACE_SAMPLING_PERCENTAGE`% of the requests you sent
(~10% by default) — this confirms the sampler is actually dropping spans
before they're stored as trace data, the way a real deployment would.

```sql
FROM Metric SELECT percentile(faas.invoke_duration, 50, 95, 99) WHERE service.name = 'java-faas-metrics-example' SINCE 10 minutes ago
```
Compare against:
```sql
FROM Span SELECT percentile(duration.ms, 50, 95, 99) WHERE service.name = 'java-faas-metrics-example' AND span.kind = 'server' SINCE 10 minutes ago
```
(Note: `faas.invoke_duration` is in seconds; multiply by 1000 to compare directly against `duration.ms`.)

**With the default 10% sampling, expect these two percentile sets to
diverge — that's the point.** `faas.invoke_duration` is derived from every
invocation, so it's the ground truth; the `Span`-derived percentiles are now
a noisy estimate from a 10% sample, same as a real customer would be stuck
with if they tried to synthesize APM-style percentiles from sampled trace
data alone. The divergence should shrink as `REQUEST_COUNT` grows. To
reproduce the original, apples-to-apples claim this example set out to
prove — that the metric faithfully reflects the underlying span data when
nothing is sampled away — rerun with `TRACE_SAMPLING_PERCENTAGE=100`; the
two percentile sets should then land within ~5% of each other.

Also confirm the `faas.trigger` and `cloud.resource_id` dimensions appear on
the metric, and that `span.kind`/`status.code` do not (they're excluded in
`collector/collector.yaml`).

## Real-deployment findings

Three issues only showed up once this was actually deployed to AWS — none
of them affect the local `sam local` + docker-compose workflow above, which
is why `template.yaml`/`collector.yaml` need the fixes described here on
top of what local testing alone would suggest.

1. **Spans get sampled out by default.** Lambda always injects an X-Ray
   trace header (`_X_AMZN_TRACE_ID`) on every invocation, which the ADOT
   agent treats as an incoming remote parent context. With the function's
   default `TracingConfig` (`PassThrough`), that header's `Sampled` flag is
   `0`, and OTel's default `ParentBased` sampler correctly honors that by
   dropping every span — this isn't a bug in the sampler, the spans
   genuinely aren't root spans from its point of view. Fix: set
   `Tracing: Active` on the function (`template.yaml`), which makes Lambda
   itself participate in X-Ray's sampling decision instead of passing
   through an always-unsampled stub.
2. **A custom OTLP endpoint needs the signal-specific variable.** Setting
   `OTEL_EXPORTER_OTLP_ENDPOINT` alone has no effect on trace export for
   this ADOT layer version — it only honors a custom destination for
   traces via `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` (full path, including
   `/v1/traces`). Without it, traces silently go to the layer's built-in
   X-Ray UDP exporter instead, with no error logged anywhere.
3. **Delta metrics can appear "back-dated" after idle gaps.** The
   `spanmetrics` connector sets each delta data point's start time to that
   series' *previous* flush. New Relic stores a delta point under its
   start time, not when it was received — so after a quiet gap, a burst of
   traffic can land under a timestamp from well before it actually
   happened, and a `SINCE N minutes ago` query run shortly afterward will
   miss data that's genuinely sitting in NRDB a bit further back. Fixed in
   `collector/collector.yaml` by explicitly setting
   `metrics_flush_interval: 60s` and rewriting each point's start time to
   `time - 60s` via a `transform` statement, so points land close to when
   they actually happened regardless of how long the preceding idle gap
   was.
