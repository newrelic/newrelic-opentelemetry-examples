# Comparing ways to get FaaS metrics from AWS Lambda with OpenTelemetry

OpenTelemetry defines standard metrics for serverless functions, such as
`faas.invoke_duration`, a histogram of how long each invocation took. Its
count gives you throughput and its distribution gives you response time, for
every invocation, unlike sampled trace data. OpenTelemetry's Lambda
instrumentation doesn't record these metrics itself, so they have to come
from somewhere else. There are two practical options:

* **The Telemetry API receiver.** The [OpenTelemetry Lambda collector layer](https://github.com/open-telemetry/opentelemetry-lambda/tree/main/collector)
  runs inside your function as a Lambda extension. Its
  [`telemetryapi` receiver](https://github.com/open-telemetry/opentelemetry-lambda/tree/main/collector/receiver/telemetryapireceiver)
  turns the events Lambda publishes about every invocation into FaaS
  metrics.
* **The spanmetrics connector.** An OpenTelemetry Collector running outside
  your function receives the function's spans, and its
  [`spanmetrics` connector](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/connector/spanmetricsconnector)
  turns each invocation span into FaaS metrics.

This example deploys both side by side, so you can see how they compare on
your own AWS account before choosing one. It creates two copies of the same
Java function, one for each approach, sends both the same constant load,
including a small share of failing requests, and also streams the functions'
CloudWatch metrics to New Relic as an independent reference.

## The two approaches at a glance

| | Telemetry API receiver | spanmetrics connector |
| --- | --- | --- |
| Where it runs | Inside the function, as an extension | In a Collector outside the function |
| What it counts from | Lambda's own record of each invocation | The function's invocation spans |
| Counts every invocation | Yes | Only if every invocation span reaches the Collector |
| Lets you sample spans in the function | Yes | No, sampling has to happen after the connector |
| Reports failures | Timeouts, in `faas.timeouts`. Not a handler that throws (see [Errors](#errors)) | `status.code` on the duration histogram |
| Cost inside the function | The extension's start-up time, run time, and memory | None beyond exporting every span |
| Also reports | Cold starts, init duration, memory use | Nothing else |

See [Choosing between them](#choosing-between-them) once you've seen the data.

## What gets deployed

```
  ┌─ default VPC ─────────────────────────────────────────────────────────────────────────────────────────┐
  │ EC2 instance                                                                                           │
  │ ┌──────────────────────────┐   GET /<function>?…   ┌──────────┐      ┌───────────────────────────────┐ │
  │ │ load generator           │ ───── (public) ─────► │ HTTP API │ ───► │ <name>-telemetry-api          │ │
  │ │                          │                       │          │      │   ADOT Java layer: spans      │ │
  │ │                          │                       │          │      │   Collector layer: faas.*     │ │
  │ │                          │                       │          │ ───► │ <name>-spanmetrics            │ │
  │ │                          │                       └──────────┘      │   ADOT Java layer: spans      │ │
  │ │ OTel Collector           │ ◄────── OTLP/HTTP :4318, private IP ─── │                               │ │
  │ │   samples spans          │                                         └───────────────┬───────────────┘ │
  │ │   spanmetrics: faas.*    │                                                         │                 │
  │ └────────────┬─────────────┘                                                         │                 │
  └──────────────┼─────────────────────────────────────────────────────────────────────────┼─────────────────┘
                 │ spans and faas.* metrics                                                │ AWS/Lambda metrics
                 ▼                                                                         ▼
             New Relic ◄───────────── Kinesis Data Firehose ◄─────────────────── CloudWatch Metric Stream
```

* **Two Lambda functions**, `<name>-telemetry-api` and `<name>-spanmetrics`,
  built from the same Java code and instrumented with AWS's ADOT Java layer.
  Only the first has the Collector layer. Both export every span.
* **An OpenTelemetry Collector** on a small EC2 instance. It forwards the
  telemetry-api function's metrics, derives metrics from the spanmetrics
  function's invocation spans, keeps 10% of all spans as trace data, and
  sends everything to New Relic.
* **A load generator** on the same instance. Once a second it sends the same
  request to both functions. Each request asks the function to sleep for 10
  to 800 ms, and 5% ask it to fail.
* **A CloudWatch Metric Stream** that sends the `AWS/Lambda` namespace to New
  Relic through Kinesis Data Firehose, and a link between your AWS account
  and your New Relic account.

Everything is one Terraform configuration in `terraform/`, run by
`deploy.sh` and removed by `teardown.sh`.

## Prerequisites

* An AWS account, and the AWS CLI configured with a profile for it. The
  target region must have a default VPC.
* [Terraform](https://developer.hashicorp.com/terraform/install) 1.3 or later.
* A JDK 21. `deploy.sh` finds one through `JAVA_HOME`, SDKMAN, or
  `/usr/libexec/java_home` on macOS.
* A New Relic account, with its
  [license key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key)
  and a [user key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#user-key).

## Deploy

Set these environment variables:

```bash
export AWS_PROFILE=<your AWS CLI profile>
export NEW_RELIC_ACCOUNT_ID=<your New Relic account ID>
export NEW_RELIC_API_KEY=<your New Relic license key>
export NEW_RELIC_USER_API_KEY=<your New Relic user key>
```

Then run:

```bash
./deploy.sh
```

It builds and tests the function, looks up the current ADOT and Collector
layer versions for your region, runs `terraform apply`, and waits until both
functions respond. The load generator then runs until you tear everything
down. Running `deploy.sh` again applies any changes in place.

These optional variables change the defaults:

| Variable | Default | Purpose |
| --- | --- | --- |
| `AWS_REGION` | `us-east-1` | Region for everything |
| `NEW_RELIC_REGION` | `US` | `US`, `EU`, `JP`, `GOV`, or `FEDRAMP`. All keys must belong to this region |
| `NEW_RELIC_OTLP_ENDPOINT` | Per region | Required for `JP`, `GOV`, and `FEDRAMP` |
| `NEW_RELIC_METRICS_INGEST_URL` | Per region | Required for `GOV` and `FEDRAMP` |
| `TRACE_SAMPLING_PERCENTAGE` | `10` | Share of spans kept as trace data. It doesn't affect either approach's metrics |
| `LOAD_INTERVAL_SECONDS` | `1` | Pause between rounds of requests. Each round calls both functions |
| `FAIL_PERCENTAGE` | `5` | Share of rounds that ask both functions to fail |

## Compare the approaches in New Relic

Spans and the faas.* metrics arrive within a couple of minutes. CloudWatch
data takes longer. A new Metric Stream can take about 10 minutes to start,
and CloudWatch data always arrives a few minutes after the invocations, so
leave the last 10 minutes out when comparing against it. The queries below
use the default function names. If you set a different `name` in
`terraform/variables.tf`, change them to match.

### Invocation counts

Each approach's count of invocations, next to CloudWatch's:

```sql
FROM Metric SELECT count(faas.invoke_duration)
WHERE service.name IN ('lambda-faas-metrics-telemetry-api', 'lambda-faas-metrics-spanmetrics')
FACET service.name SINCE 1 hour ago UNTIL 10 minutes ago
```

```sql
FROM Metric SELECT sum(aws.lambda.Invocations.byFunction)
WHERE aws.lambda.FunctionName IN ('lambda-faas-metrics-telemetry-api', 'lambda-faas-metrics-spanmetrics')
FACET aws.lambda.FunctionName SINCE 1 hour ago UNTIL 10 minutes ago
```

Both functions get the same requests, so all four numbers should be close.
Small differences over short windows come from how each source dates an
invocation. CloudWatch files it under the minute it started, and each
approach files a minute's worth of invocations under the start of the window
it exported.

Use `count(faas.invoke_duration)` for throughput. The Telemetry API
receiver's `faas.invocations` counts only successful invocations, as the
semantic conventions define it. The spanmetrics connector's own call counter
is dropped in this example, for the same reason.

### Response time

```sql
FROM Metric SELECT average(faas.invoke_duration), percentile(faas.invoke_duration, 50, 95, 99)
WHERE service.name IN ('lambda-faas-metrics-telemetry-api', 'lambda-faas-metrics-spanmetrics')
FACET service.name SINCE 1 hour ago
```

`faas.invoke_duration` is in seconds. The two approaches measure slightly
different things. The Telemetry API receiver reports Lambda's own measure of
the function's run time. The spanmetrics connector reports the invocation
span's duration, which is recorded by the instrumentation inside the
function.

To compare with CloudWatch, which reports milliseconds and has no
percentiles here:

```sql
FROM Metric SELECT average(aws.lambda.Duration.byFunction), average(aws.lambda.PostRuntimeExtensionsDuration)
WHERE aws.lambda.FunctionName IN ('lambda-faas-metrics-telemetry-api', 'lambda-faas-metrics-spanmetrics')
FACET aws.lambda.FunctionName SINCE 1 hour ago UNTIL 10 minutes ago
```

CloudWatch's `Duration` for the telemetry-api function includes the time
the Collector extension spends after the function returns, which CloudWatch
also reports on its own as `PostRuntimeExtensionsDuration`.

### Errors

```sql
FROM Metric SELECT sum(faas.errors), sum(faas.timeouts)
WHERE service.name = 'lambda-faas-metrics-telemetry-api'
SINCE 1 hour ago UNTIL 10 minutes ago
```

```sql
FROM Metric SELECT filter(count(faas.invoke_duration), WHERE status.code = 'STATUS_CODE_ERROR')
WHERE service.name = 'lambda-faas-metrics-spanmetrics'
SINCE 1 hour ago UNTIL 10 minutes ago
```

```sql
FROM Metric SELECT sum(aws.lambda.Errors.byFunction)
WHERE aws.lambda.FunctionName IN ('lambda-faas-metrics-telemetry-api', 'lambda-faas-metrics-spanmetrics')
FACET aws.lambda.FunctionName SINCE 1 hour ago UNTIL 10 minutes ago
```

In this example, CloudWatch and the spanmetrics connector each count the
same failed invocations, but the Telemetry API receiver's `faas.errors`
stays at 0. Over one 30-minute window, CloudWatch counted 32 errors for each
function and the spanmetrics connector counted 32, while the receiver
counted none and reported all 581 invocations as successful.

That's because of what Lambda tells the receiver. The receiver counts an
invocation as an error only when Lambda's Telemetry API reports its status as
`failure` or `error`. When the function's handler throws an exception, the
runtime catches it and reports it to Lambda, and the Telemetry API still
reports the invocation's status as `success`. You can see this by setting the
Collector layer's log level to `debug` in `terraform/lambda-collector.yaml`.
The function's logs then show each `platform.runtimeDone` event with its
status. Those statuses are presumably reserved for failures of the runtime
itself, such as a crash, though this example doesn't exercise that.

So for errors raised by your code, the receiver isn't enough on its own. The
spanmetrics connector marks them, because the instrumentation sets the
invocation span's status to error, and CloudWatch counts them. The receiver
does count timeouts, in `faas.timeouts`, and its duration histogram still
counts every invocation, failed or not. The histogram doesn't say which
ones failed, though.

### The cost of the extension

Both functions export every span, so the difference between them is the
Collector extension. Lambda records each invocation's duration, its cold
start time, and its peak memory in a `REPORT` log line. Run this in
[CloudWatch Logs Insights](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/AnalyzingLogData.html),
selecting both functions' log groups:

```
filter @type = "REPORT"
| stats avg(@duration) as avg_duration_ms,
        avg(@initDuration) as avg_init_ms,
        count(@initDuration) as cold_starts,
        max(@maxMemoryUsed / 1000 / 1000) as max_memory_mb
  by @log
```

In our testing at 512 MB, the extension added about 450 ms to each cold
start, about 27 ms to each invocation, and about 49 MB of memory. Your
function's numbers will differ with its runtime, memory size, and code.

### Sampling doesn't affect either approach

```sql
FROM Span SELECT count(*)
WHERE service.name IN ('lambda-faas-metrics-telemetry-api', 'lambda-faas-metrics-spanmetrics') AND span.kind = 'server'
FACET service.name SINCE 1 hour ago UNTIL 10 minutes ago
```

The Collector keeps 10% of spans as trace data, so these counts should be
about a tenth of the invocation counts above. Both approaches' metrics still
count every invocation.

## Choosing between them

**The Telemetry API receiver** is the more robust choice for counts. It
counts every invocation from Lambda's own records, whatever happens to your
spans, and it reports timeouts and cold starts too. It doesn't count errors
raised by your code, though, as [Errors](#errors) shows, so you need another
source for those, such as your spans or CloudWatch. You can sample spans
however you like, even in the function. Its costs are the extension's: a
slower cold start, a little extra billed time on every invocation, and some
of the function's memory. It's opt-in. The Collector layer's default
configuration doesn't enable it, and AWS's ADOT layers don't include it.

**The spanmetrics connector** adds nothing inside the function, which can
matter for functions sensitive to cold starts or memory. Its counts are only
as complete as the spans the connector sees, so every invocation span has to
reach it:

* The function's sampler has to export every span. With the default
  parent-based sampler and X-Ray active tracing, invocations that X-Ray
  doesn't sample produce no spans at all. This example sets
  `OTEL_TRACES_SAMPLER=always_on` on both functions.
* Any sampling to reduce stored trace data has to happen after the
  connector, in the Collector.
* Nothing can be lost between the function and the Collector.
* A timed-out invocation may not produce a span at all, since Lambda stops the
  function before the instrumentation can finish it. If so, spanmetrics
  undercounts timeouts.

It suits functions that already send every span to a Collector you run. It's
also the one of the two that reports errors raised by your code.

Either way, `faas.invoke_duration` is the metric to build on. Both
approaches send it under the same name.

## Tear down

```bash
./teardown.sh
```

This removes everything, including the account link and the Metric Stream,
and stops the load. **It's slow, typically 10 to 40 minutes**, almost all of
it spent deleting the functions' security group. A function attached to a
VPC uses network interfaces that AWS releases some time after the function
is deleted. Let it finish rather than interrupting it, or the security
groups are left behind.

## How it works

### The functions

`ExampleFunction/` is a small Java 21 handler for API Gateway requests. It
sleeps for the requested number of milliseconds inside a child span named
`do-work`, then returns. With `fail=true` it throws an exception, so that
Lambda records the invocation as failed. Both functions run this same code.

Their configuration is in `terraform/lambda.tf`. The settings that matter:

* `AWS_LAMBDA_EXEC_WRAPPER=/opt/otel-instrument` loads the ADOT Java agent.
* `OTEL_TRACES_SAMPLER=always_on` exports every span, regardless of X-Ray.
* `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` sends spans to the EC2 Collector.
  The ADOT layer ignores the generic endpoint for traces and falls back to
  X-Ray without this one.

### The Telemetry API receiver

The telemetry-api function's Collector layer reads its configuration from
`terraform/lambda-collector.yaml`, passed in through the
`OTEL_LAMBDA_COLLECTOR_CONFIG` environment variable, so nothing extra is
packaged with the function. It:

* runs the `telemetryapi` receiver with delta temporality, exporting every
  60 seconds,
* adds the function's ARN to the metrics as `cloud.resource_id`, which the
  receiver doesn't set itself and which matches `aws.Arn` on the CloudWatch
  metrics,
* sends the metrics to the EC2 Collector, so the license key stays out of
  the function's configuration.

The receiver exports on a timer that only runs while the function's
execution environment is active. A rarely invoked function can hold its
metrics until its next invocation, or until the environment shuts down.

### The spanmetrics connector

The EC2 Collector's configuration is `terraform/collector.yaml`. Its
spanmetrics pipeline:

* keeps only the spanmetrics function's invocation spans, which are the
  `SERVER` spans whose resource carries `faas.name`, so that child spans like
  `do-work` don't count as invocations,
* records them in a histogram with the `faas` namespace, keeping
  `status.code`, `faas.trigger`, and `cloud.resource_id` as attributes,
* renames the histogram from `faas.duration` to `faas.invoke_duration`, and
  drops the call counter,
* sets each delta data point's start time to one flush interval before its
  end. The connector otherwise starts a delta at the series' previous flush,
  which after an idle gap would date the data too early.

The trace pipeline samples spans separately, after the connector has seen
them.

### The network

The functions run in your default VPC and send OTLP to the Collector's
private IP. The Collector accepts port 4318 only from the functions'
security group, so it's never exposed to the internet. The functions need no
other network access, so there's no NAT gateway. The load generator reads
the API's URL from an SSM parameter, because the instance is created before
the API exists.

## Security and cost

* The Collector accepts no traffic from the internet.
* The license key is passed to the EC2 instance through its user data,
  which anyone with `ec2:DescribeInstanceAttribute` in the account can read.
  For anything longer-lived, move it to Parameter Store or Secrets Manager.
* Everything runs until you tear it down: a t3.small instance, about 4,000
  Lambda invocations an hour across both functions, the API Gateway
  requests, Firehose, and the Metric Stream. The Metric Stream covers every
  Lambda function in the region, because AWS filters streams by namespace and
  metric name, not by function.

## Troubleshooting

* **Shell into the instance** with `aws ssm start-session --target <instance ID>`.
  `deploy.sh` prints the ID, and `terraform -chdir=terraform output` shows it
  again. Then `journalctl -u loadgen -f` shows each request, and
  `sudo docker logs -f collector` shows the Collector's logs.
* **Read the functions' logs** with
  `aws logs tail /aws/lambda/lambda-faas-metrics-telemetry-api --follow`, or
  the same for `-spanmetrics`. The Collector layer logs there too.
* **No faas.* metrics from the telemetry-api function** usually means the
  Collector layer can't reach the EC2 Collector. Its errors appear in the
  function's logs.
* **No faas.* metrics from the spanmetrics function** usually means its spans
  aren't reaching the Collector. Check the Collector's logs.
* **No CloudWatch data** after 15 minutes: check the Firehose delivery
  stream's metrics in the AWS console, and its backup bucket for failed
  deliveries.
