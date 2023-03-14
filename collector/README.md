# OpenTelemetry Collector with OTLP Export to New Relic

This example demonstrates how to run the OpenTelemetry Collector and configure it to export telemetry to New Relic. The `docker-compose.yaml` file configures the collector via `otel-config.yaml`.

## Run

Set the following environment variables:
* `NEW_RELIC_API_KEY=<your_license_key>`
    * Replace `<your_license_key>` with your [Account License Key](https://one.newrelic.com/launcher/api-keys-ui.launcher).

Then run:
```shell
docker-compose -f docker-compose.yaml up
```

## Collector configuration

The collector is configured with the following components.

### Receivers
* The [OTLP receiver](https://github.com/open-telemetry/opentelemetry-collector/tree/main/receiver/otlpreceiver). This receiver is configured to accept OTLP data over gRPC on port `4317`. Configure applications to export over OTLP to `http://localhost:4317`.
* The [Fluent Forward receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/receiver/fluentforwardreceiver). This receiver listens on port `8006`. You can forward application logs to it using the [Fluentd logging driver](https://docs.docker.com/config/containers/logging/fluentd/).

### Processors
* The [Batch processor](https://github.com/open-telemetry/opentelemetry-collector/tree/main/processor/batchprocessor). This processor helps limit the number of outgoing requests. See our [best practices](https://docs.newrelic.com/docs/more-integrations/open-source-telemetry-integrations/opentelemetry/best-practices/opentelemetry-best-practices-batching/) regarding batching data.
* The [Cumulative to Delta processor](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/processor/cumulativetodeltaprocessor). This processor converts histograms and monotonic sums received in cumulative temporality to delta temporality. New Relic currently rejects cumulative histograms and monotonic sums, so this processor is necessary. However, New Relic [cumulative metric support](https://docs.newrelic.com/docs/data-apis/understand-data/metric-data/cumulative-metrics/) is currently in preview. Contact your account representative to participate in the preview. For more information on how New Relic ingests metrics, take a look at our [best practices](https://docs.newrelic.com/docs/more-integrations/open-source-telemetry-integrations/opentelemetry/best-practices/opentelemetry-best-practices-metrics).
* The [Transform processor](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/processor/transformprocessor). This processor is configured to truncate attributes on spans and logs to adhere to New Relic's attribute length limits. Attributes over the limit will cause data to be dropped. Note that the transform process has not been configured to truncate attributes on metrics. Long metric attributes should be unusual. Truncating metric attributes can be complex and may require metrics to be reaggregated. The transform processor does not perform any reaggregation.

### Exporters
* The [OTLP gRPC exporter](https://github.com/open-telemetry/opentelemetry-collector/tree/main/exporter/otlpexporter). This exporter is configured to send data to New Relic. See [documentation](https://docs.newrelic.com/docs/integrations/open-source-telemetry-integrations/opentelemetry/introduction-opentelemetry-new-relic/#how-it-works) for more information about New Relic OTLP support.
* The [Logging exporter](https://github.com/open-telemetry/opentelemetry-collector/tree/main/exporter/loggingexporter). This exporter logs data the collector processes to standard out. It can be useful for troubleshooting.
