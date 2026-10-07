# FaaS Metrics via Spanmetrics Connector (Java)

This example explores whether the OpenTelemetry Collector's `spanmetricsconnector`
can generate accurate, non-sampled FaaS metrics — specifically the semantic
convention `faas.invoke_duration` histogram — from an instrumented AWS Lambda
function's spans, as an alternative to synthesizing APM metrics from (sampled)
span data.

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
Set `REQUEST_COUNT=N` to send a different number, or `AWS_REGION=...` for a
region other than `us-east-1`. It fails loudly (exit code 1, with the
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

## Validate in New Relic

Wait about a minute (the collector's default `metrics_flush_interval` is
60s), then run in New Relic:

```sql
FROM Metric SELECT sum(faas.invocations) WHERE service.name = 'java-faas-metrics-example' SINCE 10 minutes ago
```
Expected: equals the number of requests you sent (e.g. 100). Use `sum()`,
not `count()` — `count()` on a Metric data type counts data points
(roughly one per flush per series), not the counter's own value.
`count(faas.invoke_duration)` (counting histogram observations) should
agree with it as a cross-check.

```sql
FROM Metric SELECT percentile(faas.invoke_duration, 50, 95, 99) WHERE service.name = 'java-faas-metrics-example' SINCE 10 minutes ago
```
Compare against:
```sql
FROM Span SELECT percentile(duration.ms, 50, 95, 99) WHERE service.name = 'java-faas-metrics-example' AND span.kind = 'server' SINCE 10 minutes ago
```
(Note: `faas.invoke_duration` is in seconds; multiply by 1000 to compare directly against `duration.ms`.)

**Success criteria:** the invocation count matches exactly, and the
`faas.invoke_duration` percentiles are within ~5% of the span-derived
percentiles for the same window — this is the actual proof that the
collector-generated metric faithfully reflects the underlying (unsampled)
span data.

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
