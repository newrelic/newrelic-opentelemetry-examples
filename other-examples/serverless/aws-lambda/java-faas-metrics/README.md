# FaaS Metrics via Spanmetrics Connector (Java)

This example explores whether the OpenTelemetry Collector's `spanmetricsconnector`
can generate accurate, non-sampled FaaS metrics — specifically the semantic
convention `faas.invoke_duration` histogram — from an instrumented AWS Lambda
function's spans, as an alternative to synthesizing APM metrics from (sampled)
span data. See the design spec for full context:
`docs/superpowers/specs/2026-10-02-faas-spanmetrics-lambda-example-design.md`.

Tail sampling at the collector is explicitly out of scope here — this
example only proves out the metric-generation half of that story.

**Verification status:** the collector pipeline (filter → spanmetrics →
rename, including the `0.0.0.0` receiver binding and delta temporality
fixes) has been verified end-to-end by sending real OTLP spans directly to
the collector. The AWS Lambda layer and the live New Relic NRQL validation
below have *not* been run end-to-end — both require real AWS credentials
and a real New Relic account that weren't available while building this
example. Run the steps below yourself before treating the full pipeline,
Lambda included, as proven.

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

1. Look up the current `AWSOpenTelemetryDistroJava` layer ARN for your
   region from the **Java** tab at
   https://aws-otel.github.io/docs/getting-started/lambda#adot-lambda-layer-arns.

2. Start the collector:

   ```bash
   export NEW_RELIC_LICENSE_KEY=<your license key here>
   docker compose up -d
   ```

3. Build and start the function on the collector's Docker network:

   ```bash
   JAVA_HOME=/path/to/your/jdk-21 sam build
   sam local start-api \
     --docker-network faas-metrics-net \
     --parameter-overrides "otelLambdaLayerArn=<the ARN from step 1> otelExporterOtlpEndpoint=http://collector:4318" \
     --port 3000
   ```

4. In another terminal, generate traffic:

   ```bash
   ./scripts/generate-traffic.sh http://127.0.0.1:3000/ 100 traffic-results.csv
   ```

   This fires 100 requests with randomized `sleepMs` values (10-800ms) and
   records each call's client-observed duration to `traffic-results.csv` —
   this is the independent "ground truth" used in validation below.

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
