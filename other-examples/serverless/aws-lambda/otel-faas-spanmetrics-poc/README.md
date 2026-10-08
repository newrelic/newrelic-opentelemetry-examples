# OTel FaaS Spanmetrics + CloudWatch Metric Streams POC

A single AWS Lambda function, `otel-faas-spanmetrics-poc`, reporting to New
Relic through two independent paths at once, under constant load:

1. **OpenTelemetry**: the function is instrumented with the ADOT Java layer
   and sends spans to an OTel Collector. The collector's `spanmetrics`
   connector derives `faas.invocations` and `faas.invoke_duration` from
   every invocation span, while a `probabilistic_sampler` keeps only a
   fraction of spans as trace data.
2. **CloudWatch Metric Streams**: the function's `AWS/Lambda` CloudWatch
   metrics stream through Kinesis Firehose to New Relic, with the AWS
   account linked in PUSH mode only (no API polling).

This combines two earlier examples on the `alanwest/lambda-experiment`
branch: [`java-faas-metrics`](https://github.com/newrelic/newrelic-opentelemetry-examples/tree/alanwest/lambda-experiment/other-examples/serverless/aws-lambda/java-faas-metrics) (path 1) and
[`cloudwatch-metric-stream-spike`](https://github.com/newrelic/newrelic-opentelemetry-examples/tree/alanwest/lambda-experiment/other-examples/serverless/aws-lambda/cloudwatch-metric-stream-spike) (path 2). Their READMEs
hold the original findings this example builds on; they're linked below
rather than repeated.

## Architecture

```
  ┌─ default VPC ───────────────────────────────────────────────────────────────────────────────────┐
  │ EC2 instance                                                                                     │
  │ ┌──────────────────────────┐   GET /?sleepMs=…   ┌──────────┐      ┌─────────────────────────────┐ │
  │ │ loadgen (systemd)        │ ──── (public) ────► │ HTTP API │ ───► │ Lambda (VPC-attached)       │ │
  │ │                          │                     └──────────┘      │ otel-faas-spanmetrics-poc   │ │
  │ │ OTel Collector (docker)  │ ◄──── OTLP/HTTP :4318, private IP ─── │ (ADOT Java layer)           │ │
  │ └────────────┬─────────────┘                                       └──────────────┬──────────────┘ │
  └──────────────┼────────────────────────────────────────────────────────────────────┼────────────────┘
                 │ sampled spans + faas.* metrics                                     │ AWS/Lambda metrics
                 ▼                                                                    ▼
             New Relic ◄────────── Kinesis Firehose ◄──────────────────── CloudWatch Metric Stream
```

Everything is one Terraform configuration in `terraform/`:

| File | What it creates |
| --- | --- |
| `lambda.tf` | The function, its IAM role, log group and security group, and an HTTP API in front of it |
| `collector.tf` | The EC2 instance running the collector and load generator, its security group and instance role, and the SSM parameter holding the API URL |
| `cloudwatch-metric-stream.tf` | The New Relic AWS account link (PUSH), Metric Stream, Firehose, and failed-delivery S3 bucket |
| `collector.yaml` | The collector config, unchanged from `java-faas-metrics` except for debug verbosity |
| `user-data.sh.tftpl` | Instance bootstrap: installs Docker, starts the collector, installs the load generator service |

## Prerequisites

* AWS CLI configured with a profile for the target account, which must have
  a default VPC in the target region.
* Terraform 1.3+.
* A JDK 21. JDK 25 doesn't work (Gradle 8.10 fails to start on it).
  `deploy.sh` finds one via `JAVA_HOME`, `~/.sdkman/candidates/java/21*`, or
  `/usr/libexec/java_home -v 21`.
* A New Relic account, its ingest license key, and a User API key.

## Configuration

| Variable | Required | Default | Purpose |
| --- | --- | --- | --- |
| `AWS_PROFILE` | yes | | AWS CLI profile to deploy into |
| `NEW_RELIC_ACCOUNT_ID` | yes | | New Relic account to link the AWS account to |
| `NEW_RELIC_USER_API_KEY` | yes | | NerdGraph key, used to link the AWS account |
| `NEW_RELIC_API_KEY` | yes | | Ingest license key, used by both the collector and Firehose |
| `NEW_RELIC_REGION` | no | `US` | `US`, `EU`, `JP`, `GOV`, `FEDRAMP` or `Staging`, case-insensitive. Selects NerdGraph, OTLP, and CloudWatch-metrics endpoints together |
| `NEW_RELIC_OTLP_ENDPOINT` | no | per region | Required for `JP`, `GOV`, `FEDRAMP` (no default OTLP endpoint here) |
| `NEW_RELIC_METRICS_INGEST_URL` | no | per region | Required for `GOV`, `FEDRAMP` |
| `AWS_REGION` | no | `us-east-1` | Region for everything |
| `TRACE_SAMPLING_PERCENTAGE` | no | `10` | % of spans kept as trace data. Doesn't affect `faas.*` metrics |
| `LOAD_INTERVAL_SECONDS` | no | `1` | Pause between load generator requests. See [Load rate](#load-rate) before lowering it |

All keys must belong to the same environment as `NEW_RELIC_REGION`; a
production key won't authenticate against staging or vice versa.

Example, against staging:

```bash
export AWS_PROFILE=<profile>
export NEW_RELIC_ACCOUNT_ID=<account id>
export NEW_RELIC_USER_API_KEY=<user key>
export NEW_RELIC_API_KEY=<license key>
export NEW_RELIC_REGION=Staging
```

## Run

```bash
./deploy.sh
```

This builds and unit-tests the function, looks up the current ADOT Java
layer ARN, runs `terraform apply`, then waits until the collector is
reachable and the function returns 200 through the API. From then on the
load generator calls the function continuously. Re-running `deploy.sh`
applies any changes in place; changing collector config or load settings
replaces the EC2 instance.

To stop the load and remove everything:

```bash
./teardown.sh
```

Both scripts fail fast if a required variable is missing.

**Teardown is slow, typically 10–40 minutes**, almost all of it spent
deleting the function's security group. A VPC-attached function uses
Lambda-managed network interfaces that AWS releases asynchronously after
the function is deleted, and the security group can't be deleted until
they're gone. `terraform destroy` waits this out; let it finish rather than
interrupting it, or the security groups are left behind.

**While it's running:**

```bash
# Load generator output (one line per request) and collector logs
aws ssm start-session --target <collector_instance_id>
  journalctl -u loadgen -f
  sudo docker logs -f collector

# Function logs
aws logs tail /aws/lambda/otel-faas-spanmetrics-poc --follow
```

`deploy.sh` prints the instance ID; `terraform -chdir=terraform output`
shows it and the other outputs again.

## Validate in New Relic

OTel data shows up within a couple of minutes (the collector flushes
spanmetrics every 60s). CloudWatch data takes longer: a new Metric Stream
can take ~10 minutes to start forwarding, and never backfills what it
missed while warming up.

**Spanmetrics (OTel path):**

```sql
FROM Metric SELECT sum(faas.invocations) WHERE service.name = 'otel-faas-spanmetrics-poc' TIMESERIES SINCE 30 minutes ago
```

```sql
FROM Metric SELECT percentile(faas.invoke_duration, 50, 95, 99) WHERE service.name = 'otel-faas-spanmetrics-poc' SINCE 30 minutes ago
```

Use `sum()`, not `count()`, on `faas.invocations`: `count()` counts data
points, not invocations. `faas.invoke_duration` is in seconds.

**Sampled spans (OTel path):**

```sql
FROM Span SELECT count(*), percentile(duration.ms, 50, 95, 99) WHERE service.name = 'otel-faas-spanmetrics-poc' AND span.kind = 'server' SINCE 30 minutes ago
```

The count should be roughly `TRACE_SAMPLING_PERCENTAGE`% of
`sum(faas.invocations)`, and the percentiles a noisier estimate of the
`faas.invoke_duration` ones. That gap is what this POC demonstrates: the
metrics see every invocation, the stored trace data doesn't. Deploy with
`TRACE_SAMPLING_PERCENTAGE=100` to see the two converge. See
[`java-faas-metrics`](https://github.com/newrelic/newrelic-opentelemetry-examples/tree/alanwest/lambda-experiment/other-examples/serverless/aws-lambda/java-faas-metrics)'s README, "Design notes", for why the sampler is
kept out of the `spanmetrics` pipeline.

**CloudWatch (Metric Streams path):**

```sql
FROM Metric SELECT sum(aws.lambda.Invocations.byFunction) WHERE aws.lambda.FunctionName = 'otel-faas-spanmetrics-poc' TIMESERIES SINCE 30 minutes ago
```

```sql
FROM ServerlessSample SELECT sum(provider.invocations.Sum) WHERE provider = 'LambdaFunction' SINCE 30 minutes ago
```

The most recent few minutes always read low here: CloudWatch publishes
metrics a few minutes after the invocations, and Firehose buffers for up to
60s on top of that. The `ServerlessSample` query
works without real `ServerlessSample` events because of New Relic's
server-side rewrite onto Metric Stream data; see
[`cloudwatch-metric-stream-spike`](https://github.com/newrelic/newrelic-opentelemetry-examples/tree/alanwest/lambda-experiment/other-examples/serverless/aws-lambda/cloudwatch-metric-stream-spike)'s README, "Findings", for that and for
why `count(*)` on `ServerlessSample` always reads 0 here.

**Cross-check:** the two paths count invocations independently, so
`sum(faas.invocations)` and CloudWatch's invocation count should agree over
the same window (allowing for CloudWatch's extra latency). A persistent
shortfall on the OTel side usually means spans are being dropped before
they reach the collector; see [Load rate](#load-rate).

## Design notes

### Load rate

The function uses Active X-Ray tracing, which is required: without it,
Lambda hands the OTel SDK an unsampled parent context and every span is
dropped ([`java-faas-metrics`](https://github.com/newrelic/newrelic-opentelemetry-examples/tree/alanwest/lambda-experiment/other-examples/serverless/aws-lambda/java-faas-metrics)'s README, "Real-deployment findings" #1).
The catch is that X-Ray's default sampling rule keeps 1 request per second
plus 5% of the rest, and a span X-Ray doesn't sample never reaches the
collector, so `spanmetrics` can't count it.

The load generator therefore runs one request at a time with a 1s pause,
landing at roughly 0.5–0.9 requests/second (each request also sleeps
10–800ms in the function). Lowering `LOAD_INTERVAL_SECONDS` makes
`faas.invocations` undercount relative to CloudWatch.

### Why the load generator runs on the collector instance

It needs to keep running until teardown regardless of what happens on your
laptop, and the collector instance already exists. Running it there means
`terraform destroy` stops the load as a side effect of removing the
instance, with no extra resource.

### Why the function is in a VPC

So the collector's OTLP port is never exposed to the internet. A function
outside a VPC has no fixed source address, so the collector would have to
accept OTLP from `0.0.0.0/0`, and anyone who found it could send data into
your New Relic account under your license key. Here the function runs in
the default VPC and exports to the collector's private IP, and the
collector's security group accepts port 4318 only from the function's
security group. The function needs no other network access, so there's no
NAT gateway.

The cost is the slow teardown described under [Run](#run).

### Why the load generator reads the API URL from SSM

The function's environment needs the collector's private IP, which only
exists once the instance does, so the instance can't also take the API URL
in its user data without a dependency cycle. Terraform writes the URL to
the `/otel-faas-spanmetrics-poc/api-url` SSM parameter once the API can
invoke the function, and the load generator polls for it at boot.

### Security and cost

* The collector has no inbound access from the internet (see above).
* The license key is passed through EC2 user data, readable by anyone with
  `ec2:DescribeInstanceAttribute` in the account. Those principals can
  generally read it elsewhere too (Firehose config, Terraform state),
  so this was left as is; Parameter Store or Secrets Manager would remove
  it from user data.
* Everything runs until teardown: a t3.small, ~2–3k Lambda
  invocations/hour, API Gateway requests, X-Ray traces, Firehose, and
  CloudWatch Metric Stream updates for every Lambda function in the region
  (Metric Stream filters select by namespace, not by function).

Fine for a POC you tear down; not something to leave running indefinitely.

## Differences from the original examples

* **One tool.** The function is deployed by Terraform instead of SAM, so
  one `terraform apply`/`destroy` manages everything and there's no
  `.deploy-state` file. Gradle's `buildZip` task replaces `sam build`'s
  packaging.
* **VPC-attached function, private collector.** `java-faas-metrics`'s
  collector accepted OTLP from the internet.
* **HTTP API instead of REST API**, with payload format 1.0 so the handler
  still receives an `APIGatewayProxyRequestEvent`.
* **Continuous load instead of a fixed batch**, which also removes the
  CloudWatch spike's Metric Stream warm-up probe: traffic sent during
  warm-up isn't needed.
* **Collector debug exporter at `basic` verbosity**, since `detailed` under
  constant load would fill the instance's disk. Collector container logs
  are also size-capped.
* **Firehose role can write its S3 backup bucket.** The spike's role had no
  S3 permissions, so failed deliveries couldn't actually be backed up.
* **`NEW_RELIC_ACCOUNT_ID`** replaces the spike's `TF_VAR_newrelic_account_id`,
  and `NEW_RELIC_REGION` drives both the OTLP and CloudWatch endpoints.
* No local (`sam local` + docker-compose) mode; use `java-faas-metrics`
  for that.

If either original example is deployed against the same AWS and New Relic
accounts, tear it down first. Resource names don't collide, but the
CloudWatch spike's account link and Metric Stream would duplicate this
one's.
