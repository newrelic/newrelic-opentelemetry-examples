# Instrument your AutoGen App with OpenTelemetry and New Relic

This guide walks you through everything needed to get your AutoGen Core application sending telemetry to New Relic — from instrumenting your code to seeing data in AI Monitoring.

---

## How it works

```
Your AutoGen Core App
  ├── AutoGen SingleThreadedAgentRuntime (tracer_provider)
  │     └── messaging spans: create / send / process / ack
  ├── OpenAIInstrumentor (auto-instrumentation)
  │     └── openai.chat spans: tokens, prompts, completions
  └── Manual spans
        └── gen_ai.agent.name + gen_ai.tool.name attributes
              │
              ↓
    OTLP exporter → http://localhost:4318/v1/traces
                                ↓
                  OTel Collector (Docker)
                    └── resource processor
                          adds service metadata
                                ↓
                           New Relic
                      AI Monitoring Dashboard
```

---

## Step 1 — Instrument your AutoGen app

AutoGen Core telemetry requires **three layers** of instrumentation. Set them all up **before** creating any agents or clients.

### Layer 1: TracerProvider + AutoGen runtime

```python
from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from opentelemetry.sdk.resources import Resource, SERVICE_NAME
from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter

# 1. Create and register TracerProvider
resource = Resource.create({SERVICE_NAME: "autogen-sample-app"})
tracer_provider = TracerProvider(resource=resource)
tracer_provider.add_span_processor(
    BatchSpanProcessor(OTLPSpanExporter(endpoint="http://localhost:4318/v1/traces"))
)
trace.set_tracer_provider(tracer_provider)
```

> **Full path required:** The OTLP HTTP exporter does **not** append `/v1/traces` automatically. Always include it in the endpoint.

### Layer 2: OpenAIInstrumentor (LLM call spans)

```python
from opentelemetry.instrumentation.openai import OpenAIInstrumentor

# 2. Instrument OpenAI BEFORE creating any OpenAI clients
OpenAIInstrumentor().instrument()
```

> Without `OpenAIInstrumentor`, there are zero LLM spans. AutoGen's runtime only traces message routing — it does not intercept OpenAI SDK calls.

### Layer 3: Pass tracer_provider to the AutoGen runtime

```python
from autogen_core import SingleThreadedAgentRuntime

# 3. Pass the same tracer_provider to the runtime — enables messaging spans
runtime = SingleThreadedAgentRuntime(tracer_provider=tracer_provider)
```

See the full example in [`main_autogen_core.py`](../main_autogen_core.py).

---

## Step 2 — Set gen_ai.agent.name and gen_ai.tool.name manually

AutoGen's runtime emits messaging-convention spans (`messaging.destination`, `messaging.operation`). New Relic AI Monitoring requires the GenAI semantic convention attributes. You must set these manually.

### `gen_ai.agent.name`

Inside `on_message()`, the AutoGen runtime has already created the `autogen process` span. Enrich it directly:

```python
async def on_message(self, message: UserMessage, ctx: MessageContext) -> AssistantMessage:
    trace.get_current_span().set_attribute("gen_ai.agent.name", "MathTutorAgent")
    ...
```

This adds the attribute to the existing span — no new span is needed.

### `gen_ai.tool.name`

Create a dedicated child span wrapping each tool execution:

```python
with trace.get_tracer(__name__).start_as_current_span(f"tool.{tool_name}") as tool_span:
    tool_span.set_attribute("gen_ai.tool.name", tool_name)
    tool_result = await tool.run_json(tool_args, cancellation_token)
```

> The OTel Collector cannot promote tool names from `openai.chat` span attributes into new spans — a dedicated span is the only reliable solution.

- Full example — [`main_autogen_core.py` lines 100–144](../main_autogen_core.py#L100-L144)

---

## Step 3 — Configure the OTel Collector

The collector receives spans from your app and forwards them to New Relic with service metadata applied.

### Collector config ([`otel-collector-config.yaml`](./otel-collector-config.yaml))

#### What gets mapped / added

| Layer | Attribute | Source | Description |
|-------|-----------|--------|-------------|
| AutoGen runtime | `messaging.destination` | Auto | Agent ID (e.g. `math_tutor.(default)-A`) |
| AutoGen runtime | `messaging.operation` | Auto | `publish` / `process` / `receive` |
| Manual (app code) | `gen_ai.agent.name` | Manual | Agent name for AI Monitoring UI |
| Manual (app code) | `gen_ai.tool.name` | Manual | Tool name for AI Monitoring UI |
| OpenAIInstrumentor | `gen_ai.request.model` | Auto | LLM model (e.g. `gpt-4o-mini`) |
| OpenAIInstrumentor | `gen_ai.usage.input_tokens` | Auto | Input token count |
| OpenAIInstrumentor | `gen_ai.usage.output_tokens` | Auto | Output token count |
| Collector resource | `collector.name` | Collector | Identifies the collector instance |

> No `transform` processor is needed — `gen_ai.*` attributes are set directly in the app. The collector's `resource` processor adds service metadata only.

### Enable New Relic AI Monitoring for OTel apps

To enable the **AI Monitoring** experience in New Relic for your OTel-instrumented AutoGen app, add the `aiEnabledApp: "true"` tag to the `resource` processor in your collector config:

```yaml
processors:
  resource:
    attributes:
      - key: collector.name
        value: autogen-otel-collector
        action: insert
      - key: aiEnabledApp
        value: "true"
        action: insert
```

> Without this tag, your spans will still arrive in New Relic but the **AI Monitoring** section may not surface your app in its entity list. This is required for OTel-instrumented apps — apps using the New Relic agent get this automatically.

---

## Step 4 — Set environment variables

Create a `.env` file in the project root:

```
OPENAI_API_KEY=<your_openai_api_key>
NEW_RELIC_LICENSE_KEY=<your_license_key>
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318/v1/traces
OTEL_SERVICE_NAME=autogen-sample-app
LLM_MODEL=gpt-4o-mini   # optional, defaults to gpt-4
```

The collector also needs `NEW_RELIC_LICENSE_KEY`. Create a `.env` file in this `nr-config/` directory:

```
NEW_RELIC_LICENSE_KEY=<your_license_key>
```

Replace `<your_license_key>` with your [Account License Key](https://one.newrelic.com/launcher/api-keys-ui.launcher).

> For EU accounts, update the endpoint in [`otel-collector-config.yaml`](./otel-collector-config.yaml):
> `endpoint: https://otlp.eu01.nr-data.net:4318`

---

## Step 5 — Start the OTel Collector

> The collector must be running before the app starts. Spans sent before the collector is ready will arrive in New Relic without the resource processor metadata.

```bash
docker compose -f docker-compose.yaml up -d
```

Confirm it is ready:

```bash
docker logs autogen-otel-collector --since 10s
# Look for: "Everything is ready. Begin running and processing data."
```

---

## Step 6 — Run the application

From the project root:

```bash
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318/v1/traces \
  venv/bin/uvicorn main_autogen_core:app --host 0.0.0.0 --port 8000
```

> Always set `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318/v1/traces` explicitly with the full path. The OTLP HTTP exporter does not append `/v1/traces` automatically.

---

## Step 7 — Send a request and verify

```bash
curl -X POST http://localhost:8000/chat \
  -H "Content-Type: application/json" \
  -d '{"prompt": "What is 25 multiplied by 4?"}'
```

Check your [New Relic account](https://one.newrelic.com) under **AI Monitoring** or **All Entities > Services - OpenTelemetry** to confirm data is flowing. For EU users check your [account here](https://one.eu.newrelic.com).

---

## Troubleshooting

**No data in New Relic?**
- Confirm `NEW_RELIC_LICENSE_KEY` is set correctly in both `.env` and `nr-config/.env`
- Check the collector is running: `docker ps | grep autogen-otel-collector`
- Check collector logs for export errors: `docker logs autogen-otel-collector --since 60s`
- For EU accounts, verify the endpoint in [`otel-collector-config.yaml`](./otel-collector-config.yaml) is set to `https://otlp.eu01.nr-data.net:4318`

**No spans reaching the collector at all?**
- Verify `OTEL_EXPORTER_OTLP_ENDPOINT` includes the full path: `http://localhost:4318/v1/traces` — a 404 means the path is missing
- Confirm the collector was running *before* the app started

**`gen_ai.agent.name` missing in New Relic?**
- This must be set manually inside `on_message()`. Verify `trace.get_current_span().set_attribute("gen_ai.agent.name", "MathTutorAgent")` is called — see [`main_autogen_core.py` line 102](../main_autogen_core.py#L102)

**`gen_ai.tool.name` missing in New Relic?**
- This requires a dedicated child span per tool execution. Verify the `start_as_current_span(f"tool.{tool_name}")` wrapper is in place — see [`main_autogen_core.py` lines 141–143](../main_autogen_core.py#L141-L143)

**No LLM spans (no token counts, no prompts)?**
- `OpenAIInstrumentor().instrument()` must be called before any `OpenAIChatCompletionClient` is created. Check the initialization order in your app — see Step 1 above

**AutoGen spans appear but no `openai.chat` spans?**
- `OpenAIInstrumentor` was called after the OpenAI client was created. Ensure it is called at module level before the `lifespan` context manager runs

**Collector fails to start?**
- Check config syntax: `docker logs autogen-otel-collector`
- After any config change, restart: `docker restart autogen-otel-collector`

---

## Additional References

For a deep-dive into the three-layer instrumentation strategy, full span trees, and known limitations, see [`DOCUMENTATION_GUIDE.md`](../DOCUMENTATION_GUIDE.md).

| Resource | Description |
|----------|-------------|
| [DOCUMENTATION_GUIDE.md](../DOCUMENTATION_GUIDE.md) | Full technical reference: instrumentation layers, span trees, attribute details, known limitations |
| [New Relic AI Monitoring](https://docs.newrelic.com/docs/ai-monitoring/) | How New Relic ingests and displays AI telemetry |
| [New Relic Account License Key](https://one.newrelic.com/launcher/api-keys-ui.launcher) | Where to find your license key |
| [AutoGen Core Telemetry Guide](https://microsoft.github.io/autogen/stable/user-guide/core-user-guide/core-concepts/telemetry.html) | AutoGen built-in OTel support |
| [opentelemetry-instrumentation-openai](https://pypi.org/project/opentelemetry-instrumentation-openai/) | OpenAI auto-instrumentation package |
| [OTel GenAI Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/gen-ai/) | Standard `gen_ai.*` attributes expected by New Relic |
| [OTel Messaging Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/messaging/messaging-spans/) | Conventions used by AutoGen runtime spans |
| [OTel Collector Transform Processor](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/processor/transformprocessor) | How to use OTTL transforms if attribute mapping is needed |
| [OpenTelemetry Python SDK](https://opentelemetry.io/docs/languages/python/) | OTel SDK setup and configuration reference |
