<a href="https://opensource.newrelic.com/oss-category/#example-code"><picture><source media="(prefers-color-scheme: dark)" srcset="https://github.com/newrelic/opensource-website/raw/main/src/images/categories/dark/Example_Code.png"><source media="(prefers-color-scheme: light)" srcset="https://github.com/newrelic/opensource-website/raw/main/src/images/categories/Example_Code.png"><img alt="New Relic Open Source example project banner." src="https://github.com/newrelic/opensource-website/raw/main/src/images/categories/Example_Code.png"></picture></a>

# New Relic OpenTelemetry Examples

This project contains examples illustrating usage of OpenTelemetry with New Relic. The examples aim to demonstrate the most common configurations we expect users to encounter, but isn't an exhaustive set of the ways you can use OpenTelemetry with New Relic. See [getting started guides](#getting-started-guides) and [other examples](#other-examples) for an index of available examples.

## Getting Started Guides

The [Getting Started Guides](./getting-started-guides/README.md) demonstrate how to get started with OpenTelemetry and New Relic. Each of the languages listed illustrates how to add OpenTelemetry instrumentation to a simple web application, and configure OpenTelemetry to export data to New Relic.

* .NET ([uninstrumented](./getting-started-guides/dotnet/Uninstrumented) / [instrumented](./getting-started-guides/dotnet/Instrumented))
* Go ([uninstrumented](./getting-started-guides/go/uninstrumented) / [instrumented](./getting-started-guides/go/instrumented))
* Java ([uninstrumented](./getting-started-guides/java/uninstrumented) / [instrumented](./getting-started-guides/java/instrumented))
* Javascript ([uninstrumented](./getting-started-guides/javascript/uninstrumented) / [instrumented](./getting-started-guides/javascript/instrumented))
* Python ([uninstrumented](./getting-started-guides/python/Uninstrumented) / [instrumented](./getting-started-guides/python/Instrumented))
* Ruby ([uninstrumented](./getting-started-guides/ruby/uninstrumented) / [instrumented](./getting-started-guides/ruby/instrumented))

## Other Examples

OpenTelemetry is a big ecosystem and everything doesn't fit into the goals of the [getting started guides](#getting-started-guides). These "other examples" demonstrate how other areas of OpenTelemetry fit in with New Relic. 

* Collector
  * [OpenTelemetry Collector with OTLP Export to New Relic](./other-examples/collector)
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

We encourage your contributions to improve `newrelic-opentelemetry-examples`! Keep in mind that when you submit your pull request, you'll need to sign the CLA via the click-through using CLA-Assistant. You only have to sign the CLA one time per project.

Generally, we want to focus on the [getting started guides](#getting-started-guides). We're open to additional examples being added which are aligned with the [demo app specification](./getting-started-guides/demo-app-specification.md) and which have a volunteer [codeowner](#codeowners).

We're more selective about additions to [other examples](#other-examples). We use the following criteria to evaluate additions:

* Does the example demonstrate a very popular use case or recurring pain point?
* Has someone has volunteered to be a [codeowner](#codeowners)?
* Is there documentation - either in the readme or [docs.newrelic.com](https://docs.newrelic.com/) - which describes how to use the data produced by the example in New Relic?
* Is there continuous integration (i.e. [github action](.github/workflows/pull_request.yml)) ensuring that the example code functions?

If the answer is yes to all those questions, we'll likely accept the contribution.

If you have any questions, or to execute our corporate CLA (which is required if your contribution is on behalf of a company), drop us an email at opensource@newrelic.com.

### Codeowners

Codeowners for each example are defined in [codeowner](.github/CODEOWNERS). Each codeowner is responsible for:

* Keeping dependencies (relatively) up to date.
* Responding to issues related to the example.

Examples without a codeowner may be deleted.

## Vulnerabilities

As noted in our [security policy](https://github.com/newrelic/newrelic-opentelemetry-examples/security/policy), New Relic is committed to the privacy and security of our customers and their data. We believe that providing coordinated disclosure by security researchers and engaging with the security community are important means to achieve our security goals.

If you believe you have found a security vulnerability in this project or any of New Relic's products or websites, we welcome and greatly appreciate you reporting it to New Relic through [HackerOne](https://hackerone.com/newrelic).

If you would like to contribute to this project, review [these guidelines](./CONTRIBUTING.md).

To all contributors, we thank you!  Without your contribution, this project would not be what it is today.

## License
`newrelic-opentelemetry-examples` is licensed under the [Apache 2.0](http://apache.org/licenses/LICENSE-2.0.txt) License.

`newrelic-opentelemetry-examples` also uses source code from third-party libraries. You can find full details on which libraries are used and the terms under which they are licensed in the third-party notices document.
