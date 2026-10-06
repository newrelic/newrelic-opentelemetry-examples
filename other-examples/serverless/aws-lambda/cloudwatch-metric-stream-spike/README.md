# CloudWatch Metric Streams Spike (New Relic Staging)

Spike, not a polished example: exploring what metrics/entity type New
Relic's CloudWatch Metric Streams integration produces for a Lambda
function, as a point of comparison against both the spanmetrics-connector
approach in `../java-faas-metrics` and New Relic's classic API Polling
integration. No separate spec/plan was written for this one (classified
as a spike, not an architectural change).

**Internal-only content warning:** `terraform/` embeds New Relic's internal
staging URLs (`staging-api.newrelic.com`, `staging-aws-api.newrelic.com`)
discovered via internal Slack/Confluence search, not public docs. Do not
push this directory to a public remote.

## Layout

- `function/`, `template.yaml` — a plain (non-OTel) Python Lambda, deployed
  via SAM as a CloudFormation stack. Not deployed by Terraform; see
  **Run** below for the SAM deploy step.
- `terraform/` — links an AWS account to a New Relic **staging** account
  and stands up a CloudWatch Metric Stream → Kinesis Firehose → New Relic
  pipeline for `AWS/Lambda` metrics, PUSH mode only (no API polling, no
  auto-discovery). No account IDs, profile names, or credentials are
  hardcoded anywhere in this directory — see **Configuration** below.

## Configuration

Nothing in this directory names a specific AWS account, AWS CLI profile,
or New Relic account. Before running anything under `terraform/`, export:

```bash
export AWS_PROFILE=<your AWS CLI profile for the account you're deploying into>
export TF_VAR_newrelic_account_id=<your New Relic staging account ID>
export NEW_RELIC_USER_API_KEY=<a NerdGraph user API key for that account>
export NEW_RELIC_LICENSE_KEY=<an ingest license key for that account>
```

The Lambda function's AWS region must match `terraform/variables.tf`'s
`aws_region` (default `us-east-1`) — CloudWatch Metric Streams are
regional and can't see metrics from a function running in a different
region than the stream.

## Why Terraform, and why not the New Relic Console wizard

The CloudFormation path the NR wizard generates is hardcoded to prod
US/EU/JP NerdGraph endpoints (`GraphqlAPIUrlMap` in the template) and
cannot link an AWS account to a staging NR account — confirmed as a known
limitation by an NR engineer internally (Slack, #help-cloud-monitoring,
2026-09-23): "We don't support Linking of accounts with Cloud formation
template in NR staging... You can link account by using the other two
methods - 'Manually integrate your AWS account' and 'Automate with
Terraform'."

Terraform works here because the `newrelic` provider has an explicit,
tested `nerdgraph_api_url` override (confirmed via the provider's own
integration tests, which use it to point at EU/JP endpoints) that
redirects every NerdGraph-backed resource — including the account-link
call that fails in the CFN path — to staging instead. See
`terraform/providers.tf`.

## Key facts and their sources

- Staging NerdGraph endpoint `https://staging-api.newrelic.com/graphql`:
  confirmed in an internal Confluence doc on NerdGraph auth
  (unrelated to this task — found via internal search, not guessed).
- Staging metrics ingest endpoint
  `https://staging-aws-api.newrelic.com/cloudwatch-metrics/v1`: confirmed
  via another engineer's real, working staging Firehose config pasted in
  an internal Slack thread.
- IAM trust principal `754728514883` (New Relic's AWS account for
  assuming the integration role): confirmed for **production** via the
  public `terraform-provider-newrelic` example module. **Not**
  independently confirmed for staging — an internal Slack thread implied
  staging reuses the same AWS-side trust relationship as prod (the
  unsupported part is specifically the NerdGraph linking *method* via
  CloudFormation, not a different trust principal), but if
  `newrelic_cloud_aws_link_account` fails on a trust/assume-role-shaped
  error specifically, re-check this first.
- Both CloudWatch Metric Stream output formats (JSON aside) —
  OpenTelemetry 0.7.0 and 1.0.0 — encode identical metric content; the
  only difference AWS documents is attribute wire-encoding
  (`StringKeyValue` vs `KeyValue`). Every metric streamed this way arrives
  as an OTel `Summary` type (count/sum/quantiles), not a Histogram or
  ExponentialHistogram — structurally different from the spanmetrics
  connector's true ExponentialHistogram output in `../java-faas-metrics`.

## Run

1. Deploy the Lambda function (not managed by Terraform):

   ```bash
   sam build
   sam deploy --guided
   ```

   Guided mode prompts for stack name, region, and IAM capability
   confirmation (needed because SAM creates the function's execution
   role), and saves your answers to `samconfig.toml` for later deploys.
   The region you pick here must match `terraform/variables.tf`'s
   `aws_region` (default `us-east-1`) — see **Configuration**. After it
   finishes, note the `apiEndpoint` value in the deploy output; `curl`-ing
   it a few times is the easiest way to generate CloudWatch invocation
   data once the pipeline below is live.

2. Stand up the metric-stream pipeline:

   ```bash
   cd terraform
   terraform init
   ```

   Then, with the environment variables from **Configuration** exported:

   ```bash
   ./run-plan.sh    # terraform plan
   ./run-apply.sh   # terraform apply -auto-approve
   ```

   Both scripts fail immediately with a clear message if any required
   variable isn't set, rather than silently defaulting.

## Findings: does CloudWatch Metric Streams alone populate New Relic's Lambda UI?

Short answer: **yes**, with one important caveat about entity-attribution
timing (below). This was tested by linking the AWS account with *only*
the PUSH/Metric-Streams integration (no API Polling), invoking the
monitored Lambda function, and checking whether New Relic's legacy
"Lambda metrics" nerdlet (built originally against the `ServerlessSample`
event type) renders real data.

- **It does.** New Relic has a server-side NRQL rewrite layer (internally,
  `dirac-nrql`'s "data mapping" transforms, config at
  `infra-aws-lambda.yaml`, owning team BEYOND) that transparently
  rewrites `ServerlessSample`+`provider.*`-attribute queries — exactly the
  shape the legacy nerdlet uses — onto the equivalent dimensional `Metric`
  data (e.g. `provider.invocations.Minimum` → `aws.lambda.Invocations.byFunction`),
  under a `dataSelectionPolicy: SelectBoth`. So the "legacy" UI doesn't
  actually need real `ServerlessSample` events to exist; it transparently
  rides on Metric Streams data for any attribute it recognizes. This was
  confirmed with real NRQL evidence (the transform appears in query
  response metadata) against both a production account and this staging
  account.
- **A `count(*)` query against the literal `ServerlessSample` event type is
  *not* a reliable way to tell whether Metric Streams data exists**,
  because of the above — it only tells you whether real polling-sourced
  events exist, not whether the UI has data to show.
- **Classic API Polling is a different mechanism entirely.** It actively
  discovers every Lambda function in the account/region via `ListFunctions`
  on a timer, independent of whether anything was ever invoked, and creates
  real `ServerlessSample` events (with null metric values on an idle
  function) just from that discovery. Metric Streams, by contrast, only
  ever reflects usage CloudWatch actually publishes — a function with zero
  invocations produces nothing for Metric Streams to carry, and New Relic
  never learns the function exists at all via that path.
- **Entity attribution (`entity.guid`) is assigned at ingest time, not
  backfilled.** Tested directly: a brand-new Lambda alias (never seen by
  New Relic before) produced metrics with `entity.type` correctly
  recognized but `entity.guid: null` for several minutes. Once the entity
  finished registering on New Relic's backend, *subsequently*-ingested
  data for that same alias came through fully attributed — but the
  earlier, already-ingested records stayed permanently `null`, even
  checked much later. This is why a freshly-deployed or newly-invoked
  Lambda resource can look "empty" in the UI for a while even though its
  Metric Streams pipeline is working correctly end-to-end: it's an
  entity-registration race, not a data or pipeline problem, and it does
  not self-heal for data that already landed before registration finished.
