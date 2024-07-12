<a href="https://opensource.newrelic.com/oss-category/#example-code"><picture><source media="(prefers-color-scheme: dark)" srcset="https://github.com/newrelic/opensource-website/raw/main/src/images/categories/dark/Example_Code.png"><source media="(prefers-color-scheme: light)" srcset="https://github.com/newrelic/opensource-website/raw/main/src/images/categories/Example_Code.png"><img alt="New Relic Open Source example project banner." src="https://github.com/newrelic/opensource-website/raw/main/src/images/categories/Example_Code.png"></picture></a>

# New Relic OpenTelemetry Examples

This project contains examples illustrating usage of OpenTelemetry with New
Relic. The examples aim to demonstrate the most common configurations we expect
users to encounter, but isn't an exhaustive set of the ways you can use
OpenTelemetry with New Relic.

See [OpenTelemetry APM monitoring](#opentelemetry-apm-monitoring), [OpenTelemetry infrastructure monitoring](#opentelemetry-infrastructure-monitoring)
and [OpenTelemetry other examples](#opentelemetry-other-examples) for an index
of available examples.

## OpenTelemetry APM monitoring

The [getting started guides](./getting-started-guides/README.md) demonstrate APM
monitoring with OpenTelemetry and New Relic. Each of the languages listed
illustrates how to add OpenTelemetry instrumentation to a simple web
application, and configure OpenTelemetry to export data to New Relic.

* [.NET](./getting-started-guides/dotnet)
* [Go](./getting-started-guides/go)
* [Java](./getting-started-guides/java)
* [JavaScript (Node.js)](./getting-started-guides/javascript)
* [Python](./getting-started-guides/python)
* [Ruby](./getting-started-guides/ruby)

See [OpenTelemetry APM monitoring](https://docs.newrelic.com/docs/opentelemetry/get-started/apm-monitoring/opentelemetry-apm-intro/)
for more information.

## OpenTelemetry infrastructure monitoring

These examples demonstrate how to monitor various infrastructure components with
OpenTelemetry and New Relic.

* [Monitor Confluent Cloud Kafka with Collector](./other-examples/collector/confluentcloud)
* [Monitor Docker with Collector](./other-examples/collector/docker)
* [Monitor HCP Consul with Collector](./other-examples/collector/hcp-consul)
* [Monitor HiveMQ with Collector](./other-examples/collector/hivemq)
* [Monitor Hosts with Collector](./other-examples/collector/host-monitoring)
* [Monitor Prometheus target with Collector](./other-examples/collector/prometheus)
* [Monitor Redis with Collector](./other-examples/collector/redis)
* [Monitor Singlestore with Collector](./other-examples/collector/singlestore)
* [Monitor Squid cache manager with Collector](./other-examples/collector/squid)
* [Monitor StatsD with Collector](./other-examples/collector/statsd)

## OpenTelemetry other examples

OpenTelemetry is a big ecosystem and everything doesn't fit into the goals of
the [getting started guides](#getting-started-guides). These "other examples"
demonstrate how other areas of OpenTelemetry fit in with New Relic.

* Collector
  * [Collector for data processing](./other-examples/collector/nr-config)
* Java
  * [OpenTelemetry Agent New Relic Config](./other-examples/java/agent-nr-config)
  * [Micrometer Shim with OTLP Export](./other-examples/java/micrometer-shim)
  * [Logs In Context with Log4j2 and Log Forwarding](./other-examples/java/logs-in-context-log4j2)
* .NET
  * [OpenTelemetry Agent With New Relic Config](./other-examples/dotnet/agent-nr-config)
* Serverless
  * AWS Lambda
    * [OpenTelemetry Lambda .NET New Relic Config](./other-examples/serverless/aws-lambda/dotnet)
    * [OpenTelemetry Lambda Java New Relic Config](./other-examples/serverless/aws-lambda/java)
  * Azure Functions
    * [OpenTelemetry Azure Functions Node New Relic Config](./other-examples/serverless/azure-functions/node/http-trigger-app)

## How To Use

1. Clone this repo.
2. Follow the directions in the README of the example that you are interested in.

## Contribute

We encourage your contributions to improve `newrelic-opentelemetry-examples`!

Trivial changes, wording changes, spelling/grammar
corrections, etc. can be made directly via pull requests and do not require
an associated issue.

Keep in mind that when you submit your pull request, you'll need to sign the
CLA via the click-through using CLA-Assistant. You only have to sign the CLA
one time per project. If you have any questions, or to execute our corporate
CLA (which is required if your contribution is on behalf of a company), drop us
an email at
opensource@newrelic.com.

This repository has a few areas of emphasis, each with their own criteria for
additions:

* [OpenTelemetry APM monitoring criteria](#opentelemetry-apm-monitoring-criteria)
* [OpenTelemetry infrastructure monitoring criteria](#opentelemetry-infrastructure-monitoring-criteria)
* [OpenTelemetry other examples criteria](#opentelemetry-other-examples-criteria)

We do not accept examples demonstrating workflows which are not related to New
Relic. These should be contributed upstream to the appropriate OpenTelemetry
project.

### OpenTelemetry APM monitoring criteria

Examples demonstrating APM monitoring based on OpenTelemetry instrumentation
based in [getting started guides](./getting-started-guides), and supporting
this [documentation](https://docs.newrelic.com/docs/opentelemetry/get-started/apm-monitoring/opentelemetry-apm-intro/).

We are open to extending the guides to other languages in the OpenTelemetry
ecosystem as long as they follow the uniform application structure defined in
the [demo app specification](./getting-started-guides/demo-app-specification.md).

### OpenTelemetry infrastructure monitoring criteria

Examples demonstrating infrastructure monitoring with the OpenTelemetry
collector based in [other-examples/collector](./other-examples/collector), and
supporting
this [documentation](https://docs.newrelic.com/docs/opentelemetry/get-started/collector-infra-monitoring/opentelemetry-collector-infra-intro/).

While there are many different infrastructure components which can be monitored
with the collector, we only accept examples which meet the following criteria:

* Examples must follow a common format.
  See [host monitoring](./other-examples/collector/host-monitoring) for an
  example.
  * Demonstrate configuration in kubernetes.
  * Include a `README.md` explaining what the example does, how to run, and how
    to view data in New Relic.
  * Generate load, for example by running an instance of whatever infrastructure
    component is being monitored.
* Examples produce data with a corresponding workflow in New Relic. This might
  be a dashboard the user installs or a curated experience and participation in
  entity synthesis.
* Contributors must work with the maintainers to ensure the example can be
  maintained going forward. This may include some automated verification,
  volunteering to maintain it going forward as a [codeowner](#codeowners), or
  some other arrangement. If the example integrates with a vendor, credentials
  must be supplied so the workflow can be verified on an ongoing basis.

### OpenTelemetry other examples criteria

These are examples that demonstrate important integrations which do not fall
into the core areas of emphasis, e.g. for historical reasons.

If you wish to contribute an example like this, please reach out to us or open
an issue. To avoid unnecessary work, please make sure the issue is accepted
before opening a PR.

### Codeowners

Codeowners for each example are defined in [codeowner](.github/CODEOWNERS). Each
codeowner is responsible for:

* Keeping dependencies (relatively) up to date.
* Responding to issues related to the example.

Codeowners are added as collaborators individually and given "write" permissions
to the repository.

Examples without a codeowner may be deleted.

## Vulnerabilities

As noted in
our [security policy](https://github.com/newrelic/newrelic-opentelemetry-examples/security/policy),
New Relic is committed to the privacy and security of our customers and their
data. We believe that providing coordinated disclosure by security researchers
and engaging with the security community are important means to achieve our
security goals.

If you believe you have found a security vulnerability in this project or any of
New Relic's products or websites, we welcome and greatly appreciate you
reporting it to New Relic through [HackerOne](https://hackerone.com/newrelic).

If you would like to contribute to this project,
review [these guidelines](./CONTRIBUTING.md).

To all contributors, we thank you!  Without your contribution, this project
would not be what it is today.

## License

`newrelic-opentelemetry-examples` is licensed under
the [Apache 2.0](http://apache.org/licenses/LICENSE-2.0.txt) License.

`newrelic-opentelemetry-examples` also uses source code from third-party
libraries. You can find full details on which libraries are used and the terms
under which they are licensed in the third-party notices document.
