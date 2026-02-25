# Monitoring NGINX with OpenTelemetry Collector

This example demonstrates monitoring NGINX with the [OpenTelemetry Collector](https://opentelemetry.io/docs/collector/), using the [nginx receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/receiver/nginxreceiver) to collect performance metrics and sending the data to New Relic via OTLP.

The OpenTelemetry Collector automatically collects key NGINX performance metrics from the [stub_status module](https://nginx.org/en/docs/http/ngx_http_stub_status_module.html), including connection statistics, request counts, and server health indicators.

## Requirements

* Kubernetes cluster with kubectl configured. Docker Desktop [includes a standalone Kubernetes server and client](https://docs.docker.com/desktop/kubernetes/) which is useful for local testing.
* [New Relic account](https://one.newrelic.com/)
* [New Relic license key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key)

## Running the example

1. Create your secrets file from the template and update the values:
    ```shell
    cp k8s/secrets.yaml.template k8s/secrets.yaml
    # Edit k8s/secrets.yaml with your New Relic license key
    ```
    See the [New Relic docs](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key) for how to obtain a license key.

    * If your account is based in the EU, update the `NEW_RELIC_OTLP_ENDPOINT` value in [collector.yaml](./k8s/collector.yaml) to the endpoint: [https://otlp.eu01.nr-data.net](https://otlp.eu01.nr-data.net)

    ```yaml
    # ...omitted for brevity
   env:
     # The default US endpoint is set here. You can change the endpoint and port based on your requirements if needed.
     # docs: https://docs.newrelic.com/docs/more-integrations/open-source-telemetry-integrations/opentelemetry/best-practices/opentelemetry-otlp/#configure-endpoint-port-protocol
     - name: NEW_RELIC_OTLP_ENDPOINT
       value: https://otlp.eu01.nr-data.net
    ```

2. Run the application with the following command.

    ```shell
    kubectl apply -f k8s/
    ```

   * When finished, cleanup resources with the following command. This is also useful to reset if modifying configuration.

   ```shell
   kubectl delete -f k8s/
   ```

## Viewing your data

Once your setup is complete and data is flowing, you can view your NGINX metrics in New Relic:

### Access the NGINX dashboard

**Method 1: Through Integrations & Agents**
1. Go to [one.newrelic.com](https://one.newrelic.com) > **Integrations & Agents**
2. Click **Dashboards**
3. Search for and click **NGINX OTel overview dashboard**
4. Select your account and click **View dashboard**

**Method 2: Through All Entities**
1. Navigate to **New Relic > All Entities > NGINX servers**
2. Click on the instance with name "nginx" to view the instance summary
3. Use **Metric explorer** to view all metrics associated with the NGINX instance

### Query your data with NRQL

You can use [NRQL](https://docs.newrelic.com/docs/query-your-data/nrql-new-relic-query-language/get-started/introduction-nrql-new-relics-query-language/) to perform custom analysis:

**List all NGINX metrics:**
```sql
FROM Metric SELECT uniques(metricName)
WHERE otel.library.name = 'otelcol/nginxreceiver'
LIMIT MAX
```

**View request rate over time:**
```sql
FROM Metric SELECT rate(sum(nginx.requests), 1 minute)
WHERE server.address = 'nginx'
TIMESERIES
```

**Check connection states:**
```sql
FROM Metric SELECT latest(nginx.connections_current)
WHERE server.address = 'nginx'
FACET state
```

**Verify data is arriving:**
```sql
FROM Metric SELECT *
WHERE metricName LIKE 'nginx.%'
AND instrumentation.provider = 'opentelemetry'
SINCE 10 minutes ago
```

See [get started with querying](https://docs.newrelic.com/docs/query-your-data/explore-query-data/get-started/introduction-querying-new-relic-data/) for additional details on querying data in New Relic.

## Metrics collected

The nginx receiver collects metrics from the NGINX [stub_status module](https://nginx.org/en/docs/http/ngx_http_stub_status_module.html), which provides the following metrics:

| Metric | Description | Type |
|--------|-------------|------|
| `nginx.connections_accepted` | Total number of accepted client connections | Sum |
| `nginx.connections_handled` | Total number of handled connections (same as accepted unless resource limits reached) | Sum |
| `nginx.connections_current` | Current number of connections by state (active, reading, writing, waiting) | Sum |
| `nginx.requests` | Total number of client requests | Sum |

**Connection states:**
- **active** - Currently active connections
- **reading** - Connections reading request headers
- **writing** - Connections writing responses to clients
- **waiting** - Idle keep-alive connections waiting for next request

For complete metrics details, see [NGINX OpenTelemetry metrics reference](https://docs.newrelic.com/docs/opentelemetry/integrations/nginx/nginx-otel-metrics-reference/).

## Additional notes

**This is a demo/development configuration** - This example monitors an NGINX instance defined in [nginx.yaml](./k8s/nginx.yaml) with the `stub_status` module enabled. This configuration is suitable for testing only.

**For production deployments:**

1. **Enable stub_status** - Ensure your NGINX instance has the `stub_status` module enabled and accessible (included in open-source NGINX)
2. Modify the `.receivers.nginx.endpoint` value in [collector.yaml](k8s/collector.yaml) ConfigMap to point to your NGINX stub_status endpoint
3. Update the `server.address` and `server.port` resource attributes in `attributes/nginx_metrics` to reflect your NGINX instance
4. **Secure the stub_status endpoint** - Restrict access using IP allowlists or authentication in your NGINX configuration
5. Consider adding resource limits and health checks to collector and NGINX pods
6. For more detailed metrics, consider NGINX Plus with the extended status module
7. For automatic pod discovery in production Kubernetes environments, consider using the Helm-based deployment with `receiver_creator` as documented in [Monitor NGINX on Kubernetes with OpenTelemetry](https://docs.newrelic.com/docs/opentelemetry/integrations/nginx/nginx-otel-kubernetes/)

## Learn more

- [NGINX OpenTelemetry overview](https://docs.newrelic.com/docs/opentelemetry/integrations/nginx/nginx-otel-overview/) - Understand use cases and benefits
- [NGINX OpenTelemetry metrics reference](https://docs.newrelic.com/docs/opentelemetry/integrations/nginx/nginx-otel-metrics-reference/) - Complete metrics and attributes reference
- [Find and query your NGINX data](https://docs.newrelic.com/docs/opentelemetry/integrations/nginx/find-and-query-your-data/) - Dashboards, queries, and alerts
- [NGINX receiver documentation](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/main/receiver/nginxreceiver/documentation.md) - Technical details and advanced configuration
- [NGINX OpenTelemetry quickstart](https://newrelic.com/instant-observability/nginx-opentelemetry) - Pre-built dashboard and alerts
