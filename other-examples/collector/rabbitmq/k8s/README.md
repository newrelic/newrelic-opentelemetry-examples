# Monitor RabbitMQ on Kubernetes with OpenTelemetry and New Relic

This example demonstrates how to monitor RabbitMQ running in Kubernetes using the OpenTelemetry Collector with automatic pod discovery and New Relic's recommended best practices.

## Architecture Overview

This deployment provides automatic discovery and monitoring of RabbitMQ pods:

**Key Components:**
- **Kubernetes Observer**: Continuously watches for RabbitMQ pods based on label selectors
- **Receiver Creator**: Dynamically creates RabbitMQ receivers for each discovered pod
- **Resource Attribution**: Automatically enriches metrics with Kubernetes metadata
- **Health Monitoring**: Built-in health checks for collector reliability

**Deployment Model:**
- OpenTelemetry Collector runs as a Deployment with RBAC permissions
- Automatic discovery of RabbitMQ pods across namespaces
- Comprehensive metric collection (50+ metrics)
- Production-ready resource limits and health probes

## Features

- Automatic pod discovery using Kubernetes observer
- Comprehensive metric collection across 4 categories:
  - Queue metrics (message flow, backlog, consumer counts)
  - Node health metrics (memory, disk, file descriptors)
  - I/O metrics (disk and network activity)
  - Lifecycle metrics (connections, channels, queues)
- Proper resource attributes for New Relic entity synthesis
- Production-grade security with secrets management
- Health checks and liveness/readiness probes
- Optimized batch processing and compression

## Prerequisites

### Kubernetes Cluster Requirements
- Kubernetes version: 1.19 or higher
- kubectl access with appropriate RBAC permissions
- Ability to create ClusterRoles, ClusterRoleBindings, and ServiceAccounts

### RabbitMQ Requirements
- Management plugin enabled (included in `rabbitmq:*-management-alpine` images)
- Management API exposed on port 15672
- Admin user credentials for management API access
- Pods labeled with `app.kubernetes.io/name=rabbitmq` (configurable)

### New Relic Requirements
- Active New Relic account
- [New Relic License Key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key)

## Quick Start

### 1. Create Kubernetes Secret

First, create a secret with your credentials:

```bash
# Copy the template
cp secrets.yaml.template secrets.yaml

# Edit the file and replace the placeholders:
# - <INSERT_LICENSE_KEY>: Your New Relic license key
# - Optionally change RABBITMQ_USERNAME and RABBITMQ_PASSWORD
# - For EU region, change endpoint to: https://otlp.eu01.nr-data.net:4318

# Create the secret
kubectl apply -f secrets.yaml
```

**Important:** Never commit `secrets.yaml` to version control. The `.gitignore` should exclude this file.

### 2. Configure Cluster Name

Edit `collector.yaml` and update the cluster name:

```yaml
- name: K8S_CLUSTER_NAME
  value: "my-rabbitmq-cluster"  # Change this to your actual cluster name
```

The cluster name is required for proper entity synthesis in New Relic.

### 3. Verify RabbitMQ Pod Labels (Optional)

The collector discovers RabbitMQ pods using the label `app.kubernetes.io/name=rabbitmq`. If your pods use different labels:

```bash
# Check your RabbitMQ pod labels
kubectl get pods -n <rabbitmq-namespace> --show-labels

# Update the rule in collector.yaml if needed:
# rule: type == "pod" && labels["your-label-key"] == "your-label-value"
```

Common label patterns:
- Standard deployments: `app=rabbitmq`
- StatefulSets: `app.kubernetes.io/name=rabbitmq`
- Operators: `app.kubernetes.io/component=rabbitmq`

### 4. Deploy RabbitMQ (Optional)

If you don't have RabbitMQ running, deploy the included example:

```bash
kubectl apply -f rabbitmq.yaml
```

This creates:
- A StatefulSet with persistent storage
- A Service exposing AMQP (5672) and Management (15672) ports
- Production-grade resource limits and health probes

Wait for RabbitMQ to be ready:

```bash
kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=rabbitmq -n nr-rabbitmq --timeout=300s
```

### 5. Deploy the OpenTelemetry Collector

```bash
kubectl apply -f collector.yaml
```

This creates:
- Namespace: `nr-rabbitmq`
- RBAC resources: ClusterRole, ServiceAccount, ClusterRoleBinding
- ConfigMap: Collector configuration
- Deployment: OpenTelemetry Collector with automatic pod discovery
- Service: Health check endpoint

### 6. Verify the Deployment

Check that the collector is running:

```bash
kubectl get pods -n nr-rabbitmq -l app.kubernetes.io/name=otel-collector
```

Expected output:
```
NAME                              READY   STATUS    RESTARTS   AGE
otel-collector-xxxxx-yyyyy        1/1     Running   0          30s
```

View collector logs to verify pod discovery:

```bash
kubectl logs -n nr-rabbitmq -l app.kubernetes.io/name=otel-collector --tail=50
```

Look for messages like:
```
INFO    k8sobserver/extension.go:150    Discovered pod {"kind": "pod", "name": "rabbitmq-0", "namespace": "nr-rabbitmq"}
INFO    RabbitmqReceiver                Successfully scraped rabbitmq metrics
```

### 7. Verify Data in New Relic

Wait 2-3 minutes for data to appear, then run this NRQL query in the [New Relic Query Builder](https://one.newrelic.com/data-exploration/query-builder):

```sql
SELECT count(*)
FROM Metric
WHERE metricName LIKE 'rabbitmq.%'
  AND instrumentation.provider = 'opentelemetry'
  AND k8s.cluster.name = 'my-rabbitmq-cluster'
FACET k8s.pod.name, metricName
SINCE 10 minutes ago
```

You should see metrics from each RabbitMQ pod with attributes:
- `k8s.cluster.name` - Your cluster identifier
- `k8s.namespace.name` - Pod namespace
- `k8s.pod.name` - Individual pod name
- `rabbitmq.deployment.name` - Logical grouping name
- `rabbitmq.display.name` - Hierarchical display name

## Configuration Details

### Receiver Configuration

The collector uses `receiver_creator` with `k8s_observer` for dynamic pod discovery:

```yaml
receiver_creator/rabbitmq:
  watch_observers: [k8s_observer]
  receivers:
    rabbitmq:
      rule: type == "pod" && labels["app.kubernetes.io/name"] == "rabbitmq"
      config:
        endpoint: 'http://`endpoint`:15672'
        collection_interval: 30s
```

**Key Settings:**
- **Collection Interval**: 30s (recommended by New Relic)
- **Endpoint**: Dynamic per-pod using backtick notation
- **Metrics**: 50+ metrics enabled across all categories

### Processor Pipeline

The processor pipeline enriches metrics with required attributes:

1. **batch**: Aggregates metrics (1024 batch size, 30s timeout)
2. **resource/cluster**: Adds `k8s.cluster.name` for entity synthesis
3. **transform/rabbitmq**: Creates `rabbitmq.display.name` and `rabbitmq.deployment.name`

### Resource Attributes

The following attributes are automatically added for New Relic entity synthesis:

| Attribute | Source | Purpose |
|-----------|--------|---------|
| `k8s.cluster.name` | Environment variable | Cluster identification |
| `k8s.namespace.name` | k8s_observer | Namespace isolation |
| `k8s.pod.name` | k8s_observer | Pod-level monitoring |
| `rabbitmq.deployment.name` | Transform processor | Logical grouping |
| `rabbitmq.display.name` | Transform processor | UI presentation |
| `rabbitmq.server.endpoint` | Receiver config | Full endpoint URL |
| `rabbitmq.port` | Receiver config | Management API port |

### Metrics Collected

The integration collects 50+ metrics across these categories:

**Queue Metrics:**
- `rabbitmq.consumer.count` - Number of consumers per queue
- `rabbitmq.message.current` - Messages in queue (ready + unacknowledged)
- `rabbitmq.message.published` - Messages published rate
- `rabbitmq.message.delivered` - Messages delivered rate
- `rabbitmq.message.acknowledged` - Messages acknowledged rate
- `rabbitmq.message.dropped` - Messages dropped rate

**Node Health Metrics:**
- `rabbitmq.node.mem_used` / `rabbitmq.node.mem_limit` - Memory usage
- `rabbitmq.node.disk_free` / `rabbitmq.node.disk_free_limit` - Disk space
- `rabbitmq.node.fd_used` / `rabbitmq.node.fd_total` - File descriptors
- `rabbitmq.node.sockets_used` / `rabbitmq.node.sockets_total` - Socket usage
- `rabbitmq.node.proc_used` / `rabbitmq.node.proc_total` - Process count
- Alarm metrics for memory and disk

**I/O Metrics:**
- Read/write operation rates and average times
- Sync operation counts and times
- Message store read/write rates
- Queue index operation rates

**Lifecycle Metrics:**
- Connection creation/closure rates
- Channel creation/closure rates
- Queue creation/deletion rates

See the [full metrics reference](https://docs.newrelic.com/docs/opentelemetry/integrations/rabbitmq/metrics-reference/) for details.

## Troubleshooting

### No Data Appearing in New Relic

**1. Verify RabbitMQ management plugin is enabled:**

```bash
kubectl exec -n nr-rabbitmq rabbitmq-0 -- rabbitmq-plugins list | grep management
```

Expected: `[E*] rabbitmq_management`

**2. Check collector logs for errors:**

```bash
kubectl logs -n nr-rabbitmq -l app.kubernetes.io/name=otel-collector --tail=100
```

Look for:
- Authentication errors (incorrect credentials)
- Connection errors (network issues)
- Discovery errors (RBAC permissions)

**3. Verify RabbitMQ service is accessible:**

```bash
kubectl get svc -n nr-rabbitmq rabbitmq-service
```

Port 15672 should be exposed.

**4. Test management API from within the cluster:**

```bash
kubectl run test-pod --image=curlimages/curl:latest --rm -it --restart=Never -n nr-rabbitmq -- \
  curl -u guest:guest http://rabbitmq-service.nr-rabbitmq.svc.cluster.local:15672/api/overview
```

Should return JSON data.

**5. Verify RBAC permissions:**

```bash
kubectl auth can-i list pods --as=system:serviceaccount:nr-rabbitmq:otel-collector -n nr-rabbitmq
```

Should return `yes`.

### Pod Discovery Not Working

**1. Check k8s_observer logs:**

```bash
kubectl logs -n nr-rabbitmq -l app.kubernetes.io/name=otel-collector | grep k8sobserver
```

Look for "Discovered pod" messages.

**2. Verify label selector matches your pods:**

```bash
kubectl get pods -l app.kubernetes.io/name=rabbitmq --all-namespaces
```

If empty, update the `rule` in `collector.yaml`.

**3. Check ClusterRole permissions:**

```bash
kubectl describe clusterrole otel-collector-k8s-observer
```

Should include `get`, `list`, `watch` on pods.

### Connection Errors

**1. Verify credentials match:**

Check that credentials in secrets match RabbitMQ:

```bash
kubectl get secret nr-rabbitmq-secret -n nr-rabbitmq -o jsonpath='{.data.RABBITMQ_USERNAME}' | base64 -d
```

**2. Check network policies:**

Ensure network policies allow traffic from `nr-rabbitmq` namespace to RabbitMQ pods.

**3. Test DNS resolution:**

```bash
kubectl run test-pod --image=busybox:latest --rm -it --restart=Never -n nr-rabbitmq -- \
  nslookup rabbitmq-service.nr-rabbitmq.svc.cluster.local
```

### High Cardinality Warnings

If you see high cardinality warnings in New Relic:

**1. Increase collection interval:**

```yaml
collection_interval: 60s  # Increased from 30s
```

**2. Disable less critical metrics:**

Comment out metrics you don't need in the receiver configuration.

**3. Filter to specific queues:**

Monitor only critical queues by adjusting the discovery rule.

### Collector Pod Crashing

**1. Check resource limits:**

```bash
kubectl describe pod -n nr-rabbitmq -l app.kubernetes.io/name=otel-collector
```

Look for OOMKilled. If so, increase memory limits:

```yaml
resources:
  limits:
    memory: "512Mi"  # Increased from 300Mi
```

**2. Review crash logs:**

```bash
kubectl logs -n nr-rabbitmq -l app.kubernetes.io/name=otel-collector --previous
```

## Production Considerations

### Security Best Practices

1. **Use Strong Credentials**: Replace default `guest/guest` credentials
2. **Rotate Secrets**: Implement secret rotation policies
3. **RBAC Least Privilege**: The collector only needs `get`, `list`, `watch` on pods
4. **Network Policies**: Restrict collector network access to only RabbitMQ pods
5. **Secure OTLP**: Use HTTPS endpoints (default configuration)

### High Availability

For production deployments:

1. **RabbitMQ Cluster**: Use [RabbitMQ Cluster Operator](https://www.rabbitmq.com/kubernetes/operator/operator-overview.html)
2. **Multiple Replicas**: Consider 3+ RabbitMQ nodes for HA
3. **Collector Scaling**: Single collector replica is sufficient for discovery-based monitoring
4. **Persistent Storage**: Ensure adequate storage for RabbitMQ data

### Performance Optimization

1. **Batch Processing**: Configured with 1024 batch size, 30s timeout
2. **Compression**: GZIP compression enabled on exporter
3. **Collection Interval**: 30s recommended, adjust based on load
4. **Resource Limits**: Monitor and adjust based on actual usage

### Monitoring the Monitor

Check collector health:

```bash
# Health check endpoint
kubectl port-forward -n nr-rabbitmq svc/otel-collector 13133:13133
curl http://localhost:13133/
```

Monitor collector metrics in New Relic:

```sql
SELECT count(*)
FROM Metric
WHERE instrumentation.provider = 'opentelemetry'
  AND service.name = 'otel-collector'
SINCE 1 hour ago
```

## Migration from Legacy Setup

If migrating from the previous simple Pod-based deployment:

1. **Backup existing data**: Export any custom dashboards or alerts
2. **Update secrets**: New secret structure includes OTLP endpoint
3. **Update cluster name**: Set `K8S_CLUSTER_NAME` environment variable
4. **Deploy new configuration**: Apply updated `collector.yaml`
5. **Verify data flow**: Check New Relic for metrics with new attributes
6. **Update dashboards**: Use new resource attributes (`k8s.cluster.name`, etc.)
7. **Remove old deployment**: Delete old Pod-based collector

## Example NRQL Queries

### Queue Backlog Monitoring

```sql
SELECT average(rabbitmq.message.current)
FROM Metric
WHERE k8s.cluster.name = 'my-rabbitmq-cluster'
FACET queue
TIMESERIES
SINCE 1 hour ago
```

### Memory Usage by Pod

```sql
SELECT latest(rabbitmq.node.mem_used) / latest(rabbitmq.node.mem_limit) * 100 AS 'Memory %'
FROM Metric
WHERE k8s.cluster.name = 'my-rabbitmq-cluster'
FACET k8s.pod.name
SINCE 30 minutes ago
```

### Message Throughput

```sql
SELECT rate(sum(rabbitmq.message.published), 1 minute) AS 'Published/min',
       rate(sum(rabbitmq.message.delivered), 1 minute) AS 'Delivered/min'
FROM Metric
WHERE k8s.cluster.name = 'my-rabbitmq-cluster'
TIMESERIES
SINCE 1 hour ago
```

### Connection Health

```sql
SELECT rate(sum(rabbitmq.node.connection_created_details.rate), 1 minute) AS 'Created/min',
       rate(sum(rabbitmq.node.connection_closed_details.rate), 1 minute) AS 'Closed/min'
FROM Metric
WHERE k8s.cluster.name = 'my-rabbitmq-cluster'
TIMESERIES
SINCE 1 hour ago
```

## Additional Resources

- [New Relic RabbitMQ Integration Documentation](https://docs.newrelic.com/docs/opentelemetry/integrations/rabbitmq/kubernetes/)
- [RabbitMQ Metrics Reference](https://docs.newrelic.com/docs/opentelemetry/integrations/rabbitmq/metrics-reference/)
- [OpenTelemetry Collector Documentation](https://opentelemetry.io/docs/collector/)
- [RabbitMQ Management Plugin](https://www.rabbitmq.com/management.html)
- [Kubernetes RBAC Documentation](https://kubernetes.io/docs/reference/access-authn-authz/rbac/)

## Support

For issues or questions:
- New Relic Support: https://support.newrelic.com/
- OpenTelemetry Community: https://opentelemetry.io/community/
- RabbitMQ Community: https://www.rabbitmq.com/contact.html
