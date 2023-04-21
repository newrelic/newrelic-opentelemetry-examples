# Ruby application instrumented with OpenTelemetry

This repo contains a simple Sinatra app instrumented with [OpenTelemetry Ruby](https://github.com/open-telemetry/opentelemetry-ruby) that you can configure to export traces to your New Relic account.

It has examples for the following OpenTelemetry features:
* **Automatic instrumentation:** `MyApp` uses [Sinatra](https://github.com/open-telemetry/opentelemetry-ruby-contrib/tree/main/instrumentation/sinatra) and [Rack](https://github.com/open-telemetry/opentelemetry-ruby-contrib/tree/main/instrumentation/rack) auto-instrumentation to generate spans.
* **Manual instrumentation:**The `Fibonacci` module uses manual instrumentation to generate a nested span with custom attributes.
* **Error reporting:** The `Fibonacci::RangeError` class sets the current span's status as an error and records the exception.
* **Environment variable configuration:** The app leverages the [`dotenv` gem](https://github.com/bkeepers/dotenv) to group environment variables that enable the app to export data to New Relic.
* **File configuration:** The `OpenTelemetry::SDK.configure` method is called in a Ruby file loaded before the Sinatra app to add more configuration, like service name and the instrumentation to install.
* **Attribute length limits:**  This example takes advantage of the `OTEL_ATTRIBUTE_VALUE_LENGTH_LIMIT` environment variable to ensure attribute values are never longer than New Relic's limits.

## Prerequisites

- Sign up for a [free New Relic account](https://newrelic.com/signup).

- Copy your New Relic [account ingest license key](https://one.newrelic.com/launcher/api-keys-ui.launcher).

- This assumes you have `ruby` and `bundler` installed on your machine.

## Run

1. Run `bundle install`

2. Update the `.env` file to include your license key:
  ```
    OTEL_EXPORTER_OTLP_HEADERS=api-key=<your_license_key_here>
  ```
  * Replace `<your_license_key_here>` with your New Relic account ingest license key.

3. Run the application:
  ```
    bundle exec rackup
  ```

  * This exposes an endpoint: `http://localhost:9292/fibonacci` that optionally accepts a query parameter, `n` to be an Integer between `1` and `90`.
  * If you pass an unexpected value, an error will be raised. This error will be reported on the span that raised it.
  * If you do not pass a query parameter, an integer between `1` and `90` will be selected for you.
  * Example successful URL with query parameter: `http://localhost:9292/fibonacci?n=1`
  * Example error URL: `http://localhost:9292/fibonacci?n=fish`

4. Visit New Relic
  * Look for your new service with the name 'MyApp OpenTelemetry Ruby' in New Relic.
