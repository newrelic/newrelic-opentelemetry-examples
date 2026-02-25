# Monitoring RabbitMQ with OpenTelemetry Collector

This example demonstrates monitoring RabbitMQ with the [OpenTelemetry collector](https://opentelemetry.io/docs/collector/), using the [rabbitmq receiver](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/receiver/rabbitmqreceiver) and sending the data to New Relic via OTLP.

## How the integration works

The OpenTelemetry Collector connects to the RabbitMQ management API to collect comprehensive metrics about your message broker's health and performance. The integration provides visibility into:

- **Queue metrics** - Message counts, consumer activity, and queue-level throughput
- **Node metrics** - Memory, disk, file descriptors, and Erlang VM statistics
- **Message metrics** - Publishing, delivery, and acknowledgment rates
- **I/O metrics** - Disk read/write operations and latency
- **Connection metrics** - Connection and channel lifecycle events

The collector scrapes these metrics at regular intervals and exports them to New Relic, where they are automatically organized into entities for easy monitoring and alerting.

## Requirements

### Kubernetes cluster requirements

- **Kubernetes version**: 1.19 or higher
- **kubectl access**: Configured to communicate with your cluster. Docker desktop [includes a standalone Kubernetes server and client](https://docs.docker.com/desktop/kubernetes/) which is useful for local testing.

### RabbitMQ requirements

- **Management plugin**: Must be enabled on all RabbitMQ pods (included by default in `rabbitmq:*-management*` images)
- **Service exposure**: RabbitMQ Service must expose the management API port (default: 15672)
- **Credentials**: User with permissions to access the management API

### New Relic requirements

- [A New Relic account](https://one.newrelic.com/)
- [A New Relic license key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key)

## Running the example

1. Create your secrets file from the template and update the values:
    ```shell
    cp k8s/secrets.yaml.template k8s/secrets.yaml
    # Edit k8s/secrets.yaml with your New Relic license key
    ```
    See the [New Relic docs](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key) for how to obtain a license key.

    If your account is based in the EU, update the `NEW_RELIC_OTLP_ENDPOINT` value in [collector.yaml](./k8s/collector.yaml) to the endpoint: [https://otlp.eu01.nr-data.net](https://otlp.eu01.nr-data.net)

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

   When finished, cleanup resources with the following command. This is also useful to reset if modifying configuration.

   ```shell
   kubectl delete -f k8s/
   ```

## Viewing your data

Your RabbitMQ metrics appear in several places across the New Relic platform:

### 1. Entity Explorer

Best for quick health checks and entity relationships:

1. Go to **[one.newrelic.com](https://one.newrelic.com) > All capabilities > On host integrations**
2. View automatically created RabbitMQ entities from your metrics

### 2. Pre-built Dashboards

Best for comprehensive monitoring and visualization:

1. Go to **[one.newrelic.com](https://one.newrelic.com) > Dashboards > Recommended dashboards (View all)**
2. Search for **"OpenTelemetry RabbitMQ"**

### 3. Third-party Services

Best for integration-focused monitoring:

1. Go to **[one.newrelic.com](https://one.newrelic.com) > All capabilities > Infrastructure > Third-party services**
2. Search for **"RabbitMQ"** to quickly find your monitored instances

### Query your data

All RabbitMQ metrics include the `instrumentation.provider = 'opentelemetry'` attribute. Use the [query builder](https://docs.newrelic.com/docs/query-your-data/explore-query-data/query-builder/introduction-query-builder/) to run NRQL queries.

**List all available metrics:**

```sql
SELECT count(*)
FROM Metric
WHERE metricName LIKE 'rabbitmq.%'
  AND instrumentation.provider = 'opentelemetry'
FACET metricName
SINCE 10 minutes ago
```

**Monitor queue depths:**

```sql
SELECT latest(rabbitmq.message.current)
FROM Metric
WHERE instrumentation.provider = 'opentelemetry'
  AND state = 'ready'
FACET rabbitmq.queue.name
TIMESERIES
```

**Track message throughput:**

```sql
SELECT rate(sum(rabbitmq.message.published), 1 minute) AS 'Published/min',
       rate(sum(rabbitmq.message.delivered), 1 minute) AS 'Delivered/min'
FROM Metric
WHERE instrumentation.provider = 'opentelemetry'
FACET rabbitmq.queue.name
TIMESERIES
```

**Monitor node memory usage:**

```sql
SELECT (latest(rabbitmq.node.mem_used) / latest(rabbitmq.node.mem_limit)) * 100 AS 'Memory %'
FROM Metric
WHERE instrumentation.provider = 'opentelemetry'
FACET host.name
TIMESERIES
```

See [get started with querying](https://docs.newrelic.com/docs/query-your-data/explore-query-data/get-started/introduction-querying-new-relic-data/) for additional details on querying data in New Relic.

## Available metrics

The RabbitMQ receiver collects comprehensive metrics across several categories:

### Queue metrics
- `rabbitmq.consumer.count` - Number of consumers per queue
- `rabbitmq.message.current` - Messages in queue (by state: ready, unacknowledged)
- `rabbitmq.message.published` - Total messages published
- `rabbitmq.message.delivered` - Total messages delivered
- `rabbitmq.message.acknowledged` - Total messages acknowledged
- `rabbitmq.message.dropped` - Messages dropped (rejected/expired)

### Node resource metrics
- `rabbitmq.node.mem_used` / `rabbitmq.node.mem_limit` / `rabbitmq.node.mem_alarm` - Memory usage and alarms
- `rabbitmq.node.disk_free` / `rabbitmq.node.disk_free_limit` / `rabbitmq.node.disk_free_alarm` - Disk usage and alarms
- `rabbitmq.node.fd_used` / `rabbitmq.node.fd_total` - File descriptor usage
- `rabbitmq.node.sockets_used` / `rabbitmq.node.sockets_total` - Socket usage
- `rabbitmq.node.proc_used` / `rabbitmq.node.proc_total` - Erlang process usage
- `rabbitmq.node.uptime` - Node uptime

### I/O and performance metrics
- `rabbitmq.node.io_read_*` / `rabbitmq.node.io_write_*` - Disk I/O operations and throughput
- `rabbitmq.node.msg_store_read_count_details.rate` - Message store read rate
- `rabbitmq.node.msg_store_write_count_details.rate` - Message store write rate
- `rabbitmq.node.gc_num_details.rate` - Garbage collection frequency
- `rabbitmq.node.context_switches_details.rate` - Context switch rate

### Connection and lifecycle metrics
- `rabbitmq.node.connection_created_details.rate` / `rabbitmq.node.connection_closed_details.rate` - Connection lifecycle
- `rabbitmq.node.channel_created_details.rate` / `rabbitmq.node.channel_closed_details.rate` - Channel lifecycle
- `rabbitmq.node.queue_declared_details.rate` / `rabbitmq.node.queue_created_details.rate` - Queue lifecycle

For complete metric details with NRQL examples, see the [New Relic RabbitMQ metrics reference](https://docs.newrelic.com/docs/opentelemetry/integrations/rabbitmq/metrics-reference/).

## Troubleshooting

### No data appearing in New Relic

If metrics aren't appearing after deployment:

1. **Check collector pod is running:**
   ```shell
   kubectl get pods -n nr-rabbitmq
   ```

2. **View collector logs:**
   ```shell
   kubectl logs -n nr-rabbitmq collector --tail=50
   ```
   Look for connection errors, authentication failures, or parsing errors.

3. **Verify RabbitMQ management plugin is enabled:**
   ```shell
   kubectl exec -n nr-rabbitmq rabbitmq -- rabbitmq-plugins list | grep management
   ```
   You should see `[E*] rabbitmq_management` indicating it's enabled.

4. **Test management API accessibility:**
   ```shell
   kubectl exec -n nr-rabbitmq rabbitmq -- curl -I -u guest:guest http://localhost:15672/api/overview
   ```
   Should return `HTTP/1.1 200 OK`.

5. **Verify New Relic credentials:**
   Check that your license key is correct in the secrets file.

### Authentication failures

If you see `401 Unauthorized` errors in collector logs:

- Verify the username and password in `k8s/secrets.yaml` match your RabbitMQ configuration
- Ensure the RabbitMQ user has administrator privileges
- Check that credentials don't contain special characters that need escaping

### High memory usage

If the collector is consuming excessive memory:

- Increase the `collection_interval` to reduce metric collection frequency
- Disable non-essential metrics in the receiver configuration
- Increase batch processor timeout to reduce export frequency
- Add resource limits to the collector pod

## What's next?

Now that you have RabbitMQ monitoring set up, enhance your observability:

**Learn more:**
- [New Relic RabbitMQ metrics reference](https://docs.newrelic.com/docs/opentelemetry/integrations/rabbitmq/metrics-reference/) - Complete metrics list with detailed NRQL examples
- [OpenTelemetry RabbitMQ receiver documentation](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/receiver/rabbitmqreceiver) - Detailed receiver configuration options

**Enhance monitoring:**
- [Create alerts](https://docs.newrelic.com/docs/alerts-applied-intelligence/new-relic-alerts/get-started/introduction-alerts/) - Set up alerts for queue depths, memory usage, and disk space
- [Build dashboards](https://docs.newrelic.com/docs/query-your-data/explore-query-data/dashboards/introduction-dashboards/) - Create custom dashboards to visualize your RabbitMQ metrics
- [Configure SLIs/SLOs](https://docs.newrelic.com/docs/service-level-management/intro-slm/) - Define service level objectives for message processing

## Additional notes

**This is a demo/development configuration** - This example monitors a RabbitMQ instance defined in [rabbitmq.yaml](./k8s/rabbitmq.yaml) using default guest/guest credentials. **WARNING: These default credentials are insecure and should never be used in production.** This configuration is suitable for testing only.

**Important port information:**
- **Port 5672** - AMQP protocol port for client connections
- **Port 15672** - Management API port (HTTP) used by the OTel receiver

**For production deployments:**

1. **Change default credentials** - Replace guest/guest with secure credentials and store them in Kubernetes Secrets
2. **Enable TLS** - Configure TLS for the management API if exposed externally
3. **Add resource limits** - Configure proper resource requests and limits for both collector and RabbitMQ pods
4. **Configure RBAC** - Use service accounts with minimal required permissions
5. **Optimize collection** - Adjust `collection_interval` and enable only necessary metrics based on your monitoring needs
6. **Use StatefulSets** - For production RabbitMQ, use StatefulSets instead of Pods for proper volume management
7. **Enable persistence** - Configure persistent volumes for RabbitMQ data
8. **High availability** - Deploy multiple RabbitMQ nodes in a cluster for redundancy
