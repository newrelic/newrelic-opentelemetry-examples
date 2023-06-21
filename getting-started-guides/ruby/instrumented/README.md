# Getting Started Guide - Ruuby

This is the solution (completely instrumented with OpenTelemetry) for the Ruby demo application used in the Getting Started Guide - Ruby doc.

Requires:

* Ruby 3.2.2
* Bundler
* [A New Relic account](https://one.newrelic.com/)

To run this demo app via the CLI:

1. Switch to the `getting-started-guides/ruby/instrumented` directory
2. Set these two environment variables to send data to your New Relic account:
```
export OTEL_EXPORTER_OTLP_HEADERS=api-key=<your_license_key>
export OTEL_EXPORTER_OTLP_ENDPOINT=https://otlp.nr-data.net:4317
```
* Make sure to use your [ingest license key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key)
* If your account is based in the EU, set the endpoint to: https://otlp.eu01.nr-data.net:4317

3. Set this environment variable to name the demo app:
```
export OTEL_SERVICE_NAME=getting-started-ruby
```

4. Install your gems using Bundler

```shell
bundle install
```

5. Run the following command

```shell
bundle exec rackup
```

4. To generate traffic, in a new terminal tab run the following command
```shell
./load-generator.sh
```

5. To shut down the program, run the following in both shells or terminal tabs: `ctrl + c`.
