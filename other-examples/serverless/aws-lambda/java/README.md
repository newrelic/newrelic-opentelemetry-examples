# OpenTelemetry Lambda Java Example

This repo contains a simple AWS Lambda Java function instrumented with OpenTelemetry.

## Prerequisites

This example assumes you have the following:

* A New Relic account. If you don't have one, [create one for free](https://newrelic.com/signup).
* An AWS account. If you don't have one, [create one for free](https://aws.amazon.com/).
* A [New Relic license key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#ingest-keys) from your New Relic account.

It also assumes you have the following installed:

* [Java 11](https://www.codejava.net/java-se/download-and-install-java-11-openjdk-and-oracle-jdk)
* [SAM CLI](https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-sam-cli-install.html)

## Run

Set the following environment variable:

```
export NEW_RELIC_LICENSE_KEY=<your license key here>
```

Replacing `<your license key here>` with your New Relic license key.

Then build the example function:

```
sam build
cp collector.yaml .aws-sam/build/function/collector.yaml
```

Then deploy it to your AWS account:

```
sam deploy \
    --capabilities CAPABILITY_NAMED_IAM \
    --parameter-overrides "newRelicLicenseKey=${NEW_RELIC_LICENSE_KEY}" \
    --resolve-s3 \
    --stack-name newrelic-example-opentelemetry-lambda-java
```

The deploy will output an `apiEndpoint` which you can use to invoke the function:

```
curl https://xxxxxxxxxx.execute-api.us-east-1.amazonaws.com/api/
```

## View your data in the New Relic UI

After invoking the function you should see `newrelic-example-opentelemetry-lambda-java` under `Services - OpenTelemetry` in your New Relic account.
