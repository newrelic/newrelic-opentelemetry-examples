[![Example Code header](https://github.com/newrelic/opensource-website/raw/develop/src/images/categories/Example_Code.png)](https://opensource.newrelic.com/oss-category/#example-code)

# New Relic OpenTelemetry Examples

This project contains examples illustrating usage of OpenTelemetry with New Relic. The examples aim to demonstrate the most common configurations we expect users to encounter, but isn't an exhaustive set of the ways you can use OpenTelemetry with New Relic. The examples generally adhere to the following principles:

* Use the latest version of each language's OpenTelemetry API / SDK.
* Demonstrate using simple web service applications using a popular library / framework for each language.
* Demonstrate manual configuration of OpenTelemetry SDK to send data to New Relic for traces, metrics, and logs.
  * Export data to New Relic over OTLP using protocol most idiomatic for the language, and defaulting to `http/protobuf` when there is no clear preference.
  * Configure metrics to export using [delta](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/sdk_exporters/otlp.md) temporality preference.
  * Configure attribute limits to adhere to New Relic's limits on attribute length and count.
* Demonstrate auto instrumentation / agent configuration if offered by a language. 
  * Use environment variables / system properties to configure OTLP export, delta metrics export, and attribute limits aligned with manual configuration of SDK.

The following table details features currently demonstrated by the example.

`+` means the feature is covered, `-` means it is not covered, and  `N/A` means feature is not available yet.

|                                                             | Java | .NET | JS  | Go  | Python | Collector[1] |
|-------------------------------------------------------------|------|------|-----|-----|--------|--------------|
| Configure SDK for New Relic                                 |      |      |     |     |        |              |
| &nbsp;&nbsp;&nbsp;&nbsp;Collect traces                      | +    | +    | +   | +   | +      | +            |
| &nbsp;&nbsp;&nbsp;&nbsp;Collect metrics                     | +    | +    | +   | +   | +      | +            |
| &nbsp;&nbsp;&nbsp;&nbsp;Collect logs                        | +    | +    | +   | N/A | N/A    | +            |
| &nbsp;&nbsp;&nbsp;&nbsp;Export over OTLP                    | +    | +    | +   | +   | +      | +            |
| &nbsp;&nbsp;&nbsp;&nbsp;Export metrics w/ delta temporality | +    | +    | N/A | +   | +      | +            |
| &nbsp;&nbsp;&nbsp;&nbsp;Adhere to attribute limits          | +    | +    | +   | +   | +      | +            |
| Configure auto instrumentation / agent for New Relic        | +    | +    | N/A | N/A | N/A    | N/A          |


**[1]** The collector has a different architecture and serves a different purpose than the language SDKs. Therefore, an apples-to-apples comparison to SDKs doesn't make sense. The table roughly indicates whether the feature is illustrated in the example collector configuration, even though the feature may not mean exactly the same thing as it does in the context of an SDK.

See [Contribute](#contribute) for how to request OpenTelemetry/New Relic interactions be covered across all supported languages, or how to request a new language be added.

## Getting Started

1. Clone this repo.
2. Follow the directions in the README of the example that you are interested in.

## Examples Index

- Collector
  - [OpenTelemetry Collector with OTLP Export to New Relic](./collector): Run the OpenTelemetry Collector with OTLP gRPC export to New Relic.
- Java
  - [OpenTelemetry Agent New Relic Config](./java/agent-nr-config): A Java application with the OpenTelemetry Agent configured for New Relic.
  - [Logs In Context With Log4j2](./java/logs-in-context-log4j2): A Java application configured to include OpenTelemetry trace context on Log4j2 structured logs.
  - [OpenTelemetry SDK New Relic Config](./java/sdk-nr-config): A Java application with OpenTelemetry standalone library instrumentation as well as custom instrumentation, configured for New Relic via the SDK. Demonstrates usage of spans, metrics, and logs.
  - [OTLP New Relic Mapping](./java/otlp-nr-mapping): Contains a variety of test cases demonstrating how OTLP payloads are mapped to records in NRDB upon ingest.
- .NET
  - [OpenTelemetry SDK New Relic Config](./dotnet/aspnetcore/) Simple ASP.NET Core application demonstrating OpenTelemetry instrumentation.
- JavaScript
  - [OpenTelemetry SDK New Relic Config - OTLP/HTTP (PROTO)](./javascript/simple-nodejs-app-http-exp): An express application demonstrating OpenTelemetry auto-instrumentation and exporting with OTLP/HTTP (PROTO), configured for New Relic via the SDK.
- Python
  - [OpenTelemetry SDK New Relic Config](./python): Two simple Python application demonstrating OpenTelemetry instrumentation, one with Flask auto-instrumentation and one without Flask. Configured for New Relic via the SDK.
- Go
  - [OpenTelemetry SDK New Relic Config](./go): Simple Go applications demonstrating OpenTelemetry instrumentation.
- AWS Lambda
  - [OpenTelemetry Lambda .NET New Relic Config](./aws-lambda/dotnet): An example AWS .NET Lambda function instrumented with OpenTelemetry.
  - [OpenTelemetry Lambda Java New Relic Config](./aws-lambda/java): An example AWS Java Lambda function instrumented with OpenTelemetry.

## Contribute

We encourage your contributions to improve `newrelic-opentelemetry-examples`! Keep in mind that when you submit your pull request, you'll need to sign the CLA via the click-through using CLA-Assistant. You only have to sign the CLA one time per project.

Any contributions made outside the scope of the example matrix will not be considered. If your contribution is not for an existing OpenTelemetry/New Relic interaction and language, please first open an issue so the additional scope can be discussed.  Any new OpenTelemetry/New Relic interaction will need to be covered by all supported languages.  Any new language will need to cover all OpenTelemetry/New Relic interactions. If the scope is accepted, then the example matrix can be updated, and the new contribution will be reviewed.

If you have any questions, or to execute our corporate CLA (which is required if your contribution is on behalf of a company), drop us an email at opensource@newrelic.com.

**A note about vulnerabilities**

As noted in our [security policy](../../security/policy), New Relic is committed to the privacy and security of our customers and their data. We believe that providing coordinated disclosure by security researchers and engaging with the security community are important means to achieve our security goals.

If you believe you have found a security vulnerability in this project or any of New Relic's products or websites, we welcome and greatly appreciate you reporting it to New Relic through [HackerOne](https://hackerone.com/newrelic).

If you would like to contribute to this project, review [these guidelines](./CONTRIBUTING.md).

To all contributors, we thank you!  Without your contribution, this project would not be what it is today.

## License
`newrelic-opentelemetry-examples` is licensed under the [Apache 2.0](http://apache.org/licenses/LICENSE-2.0.txt) License.

`newrelic-opentelemetry-examples` also uses source code from third-party libraries. You can find full details on which libraries are used and the terms under which they are licensed in the third-party notices document.
