# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Overview

This repository contains OpenTelemetry examples demonstrating integration with New Relic across three main categories:

1. **APM Monitoring** (`getting-started-guides/`): Language-specific examples (.NET, Go, Java, JavaScript, Python, Ruby, Rust) demonstrating application instrumentation
2. **Infrastructure Monitoring** (`other-examples/collector/`): OpenTelemetry Collector examples for monitoring various infrastructure components
3. **Other Examples** (`other-examples/`): Additional integrations including serverless, agent configurations, and specialized use cases

## Key Architecture Patterns

### Infrastructure Monitoring Examples Structure

All collector-based infrastructure examples in `other-examples/collector/` follow a consistent pattern:

**Directory Structure:**
```
other-examples/collector/<component>/
├── README.md
└── k8s/
    ├── collector.yaml          # K8s resources: Namespace, ConfigMap, Pod/DaemonSet
    ├── secrets.yaml.template   # Template for New Relic API key
    └── <component>.yaml        # (Optional) K8s resources for the monitored component
```

**Key Configuration Elements:**

1. **Namespace**: Each example uses a dedicated namespace (e.g., `nr-redis`, `nr-statsd`)

2. **ConfigMap**: Contains the OTel Collector configuration with:
   - `receivers`: Component-specific receiver (e.g., `redis`, `nginx`, `rabbitmq`)
   - `processors`: Data transformation (attributes, batch, filtering)
   - `exporters`: OTLP export to New Relic
   - `service.pipelines`: Wire receivers → processors → exporters

3. **Secrets**: API key stored in secrets, referenced via `secretKeyRef`

4. **Environment Variables**:
   - `NEW_RELIC_OTLP_ENDPOINT`: Default `https://otlp.nr-data.net/` (US), or `https://otlp.eu01.nr-data.net/` (EU)
   - `NEW_RELIC_API_KEY`: License key from secrets

5. **Resource Attributes**: Examples add identifying attributes (e.g., `server.address`, `server.port`) required for New Relic entity synthesis

6. **Collector Image**: Use `otel/opentelemetry-collector-contrib` with specific version tags

### APM Examples Structure

Getting-started guides follow the demo app specification (`getting-started-guides/demo-app-specification.md`):

- Fibonacci calculator web service on port 8080
- Endpoint: `/fibonacci?n=[input]`
- Standard OTel environment variable configuration
- Required telemetry: traces (root + child spans), metrics (counter), logs
- Language-specific build and run instructions in each README

### Docker vs Kubernetes Deployment

- **Kubernetes** (most common): Uses `kubectl apply -f k8s/` with ConfigMaps and Secrets
- **Docker Compose** (rare): Uses `.env` files and `docker-compose.yaml` (e.g., docker, prometheus examples)

## Common Development Tasks

### Creating New Infrastructure Examples

When adding a new collector-based infrastructure example:

1. Create directory: `other-examples/collector/<component>/`
2. Create `k8s/` subdirectory
3. Copy and adapt from similar example (e.g., redis, statsd)
4. Required files:
   - `README.md` following standard format (Requirements, Running, Viewing Data, Additional Notes)
   - `k8s/collector.yaml` with component-specific receiver configuration
   - `k8s/secrets.yaml.template` for API key
   - `k8s/<component>.yaml` if deploying the component itself for demo
5. Ensure resource attributes enable entity synthesis in New Relic
6. Update main `README.md` to add example to index
7. Configure codeowners in `.github/CODEOWNERS`

### README Template Structure

All infrastructure monitoring READMEs follow this structure:

```markdown
# Monitoring <Component> with OpenTelemetry Collector

Brief description with link to OTel receiver documentation.

## Requirements

- Kubernetes cluster with kubectl (or Docker if applicable)
- New Relic account and license key

## Running the example

1. Create secrets file from template
2. (Optional) Update endpoint for EU accounts
3. Run with kubectl/docker-compose
4. Cleanup instructions

## Viewing your data

Instructions for finding data in New Relic UI and example NRQL queries.

## Additional notes

Production considerations, configuration caveats, and optimizations.
```

### Secrets Management

- Never commit actual API keys
- Always use `secrets.yaml.template` with placeholder `<INSERT_API_KEY>`
- For Docker examples, use `.env` files (add to `.gitignore` and use `git update-index --skip-worktree`)

### OTLP Endpoint Configuration

Default US endpoint: `https://otlp.nr-data.net/`
EU endpoint: `https://otlp.eu01.nr-data.net/`

Always document how to change endpoint for EU accounts in README.

## Contribution Workflow

1. **For infrastructure examples**: Create an issue first (required for non-trivial changes)
2. Ensure issue is accepted before creating PR
3. Follow criteria in main README:
   - Common format with Kubernetes deployment
   - Complete README with usage instructions
   - Produce data with corresponding New Relic workflow/dashboard
   - Coordinate with maintainers on maintenance plan
4. Sign CLA via CLA-Assistant
5. Codeowners must be assigned for new examples

## Collector Configuration Patterns

### Common Processors

- `batch`: Batch telemetry before export (standard in all examples)
- `attributes/<pipeline>`: Add/modify resource attributes for entity synthesis
- `transform`: Modify metric descriptions/units
- `filter`: Remove unwanted metrics/attributes
- `cumulativetodelta`: Convert cumulative to delta metrics (New Relic preference)
- `resourcedetection`: Detect host.id and other resource attributes

### Entity Synthesis Requirements

New Relic requires specific resource attributes for entity synthesis:

- **Generic services**: `server.address`, `server.port`
- **Hosts**: `host.id`
- **Containers**: `host.id`, container identifiers

Examples demonstrate adding these via `attributes/<pipeline>` processor when receivers don't provide them.

## Testing and Validation

### Verifying Data in New Relic

Most examples include NRQL queries for validation:

```sql
FROM Metric SELECT uniques(metricName) WHERE otel.library.name = 'otelcol/<receiver>' LIMIT MAX
```

### APM Examples Validation

Use this query to verify all APM examples are reporting:

```sql
FROM Span, Metric, Log
SELECT
  filter(count(*), WHERE eventType() = 'Log') as 'log_record_count',
  filter(count(*), WHERE eventType() = 'Metric') as 'metric_point_count',
  filter(count(*), WHERE eventType() = 'Span') as 'span_count'
WHERE service.name LIKE 'getting%' AND metricName NOT LIKE 'apm%'
FACET service.name SINCE 5 minute ago
```

## Important Conventions

- Collector image version should be consistent across examples (currently `0.98.0`)
- Use environment variable references in collector config: `${ENV_VAR_NAME}`
- Kubernetes service names follow pattern: `<component>-service`
- Namespace names follow pattern: `nr-<component>`
- Secret names follow pattern: `nr-<component>-secret`
- Always include cleanup commands in README (`kubectl delete -f k8s/`)

## Codeowners

- Global: `@newrelic/opentelemetry-community`
- Infrastructure examples: `@jcountsNR`
- .NET examples: `@alanwest`
- Ruby examples: `@kaylareopelle`

Examples without codeowners may be deleted.
