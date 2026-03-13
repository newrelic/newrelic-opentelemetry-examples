# Instrument your LlamaIndex App with OpenTelemetry and New Relic

This guide walks you through everything needed to get your LlamaIndex application sending telemetry to New Relic — from instrumenting your code to seeing data in AI Monitoring.

---

## How it works

```
Your LlamaIndex App
  └── LlamaIndexInstrumentor (OpenInference auto-instrumentation)
        └── OTLP exporter → http://localhost:4318
                                    ↓
                      OTel Collector (Docker)
                        └── transform/genai processor
                              maps OpenInference → gen_ai.* attributes
                                    ↓
                               New Relic
                          AI Monitoring Dashboard
```

---

## Step 1 — Instrument your LlamaIndex app

LlamaIndex telemetry is captured via [OpenInference](https://github.com/Arize-ai/openinference) auto-instrumentation. Add the following to your app **before** importing any LlamaIndex modules:

```python
from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import SimpleSpanProcessor
from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
from openinference.instrumentation.llama_index import LlamaIndexInstrumentor

# 1. Set up TracerProvider first
prov = TracerProvider()
exporter = OTLPSpanExporter(endpoint="http://localhost:4318/v1/traces")
prov.add_span_processor(SimpleSpanProcessor(exporter))
trace.set_tracer_provider(prov)

# 2. Instrument LlamaIndex — must happen before importing LlamaIndex
LlamaIndexInstrumentor().instrument(tracer_provider=prov)

# 3. Now import LlamaIndex
from llama_index.core.agent import ...
```

> **Order matters:** `LlamaIndexInstrumentor().instrument()` must be called before any LlamaIndex imports, otherwise spans will not be captured.

See the full example in [`llamaindex_app.py`](../llamaindex_app.py).

---

## Step 2 — Add the required gen_ai attribute mappings to your app

OpenInference does not emit two attributes that New Relic AI Monitoring requires. You must set these manually in your application code.

### `gen_ai.input.messages` and `gen_ai.output.messages`

OpenInference emits message data as flat indexed attributes (e.g. `llm.input_messages.0.role`). New Relic expects a JSON array: `[{"role": "user", "content": "..."}]`.

Wrap each agent call in a custom span and set these attributes:

```python
tracer = trace.get_tracer(__name__)
with tracer.start_as_current_span("chat") as span:
    result = await agent.achat(request.prompt)
    span.set_attribute("gen_ai.input.messages",
        json.dumps([{"role": "user", "content": request.prompt}]))
    span.set_attribute("gen_ai.output.messages",
        json.dumps([{"role": "assistant", "content": result.response}]))
```

- FunctionCallingAgent example — [`llamaindex_app.py` lines 138–147](../llamaindex_app.py#L138-L147)
- ReActAgent example — [`llamaindex_app.py` lines 162–171](../llamaindex_app.py#L162-L171)

### `gen_ai.response.finish_reasons`

OpenInference does not capture the LLM finish reason. Subclass the `OpenAI` LLM to read it from the raw response and set it on the current span:

```python
from llama_index.llms.openai import OpenAI as _OpenAI

class OpenAI(_OpenAI):
    async def achat(self, messages, **kwargs):
        response = await super().achat(messages, **kwargs)
        try:
            finish_reason = response.raw.choices[0].finish_reason
            trace.get_current_span().set_attribute(
                "gen_ai.response.finish_reasons", json.dumps([finish_reason])
            )
        except Exception:
            pass
        return response
```

Full example — [`llamaindex_app.py` lines 37–49](../llamaindex_app.py#L37-L49)

---

## Step 3 — Configure the OTel Collector

The collector receives spans from your app and maps OpenInference attribute names to the `gen_ai.*` conventions New Relic requires. This mapping is done by the `transform/genai` processor in [`otel-collector-llamaindex.yaml`](./otel-collector-llamaindex.yaml).

### What gets mapped

| OpenInference Attribute | New Relic (`gen_ai.*`) Attribute | Description |
|-------------------------|----------------------------------|-------------|
| `llm.model_name` | `gen_ai.request.model` | LLM model (e.g. `gpt-4`) |
| `llm.token_count.prompt` | `gen_ai.usage.input_tokens` | Input token count |
| `llm.token_count.completion` | `gen_ai.usage.output_tokens` | Output token count |
| `tool.name` | `gen_ai.tool.name` | Tool called by the agent |
| *(hardcoded)* | `gen_ai.system: openai` | Required by New Relic to identify LLM spans |
| span name `chat` | `gen_ai.agent.name: FunctionCallingAgent` | Agent type |
| span name `chat_react` | `gen_ai.agent.name: ReActAgent` | Agent type |

> **Using your own collector config?** Copy the `transform/genai` processor block from [`otel-collector-llamaindex.yaml` lines 27–42](./otel-collector-llamaindex.yaml#L27-L42) into your config and add `transform/genai` to your `traces` pipeline processors list.

### Enable New Relic AI Monitoring for OTel apps

To enable the **AI Monitoring** experience in New Relic for your OTel-instrumented LlamaIndex app, add the `aiEnabledApp: "true"` tag to the `resource` processor in your collector config:

```yaml
processors:
  resource:
    attributes:
      - key: aiEnabledApp
        value: "true"
        action: insert
```

> Without this tag, your spans will still arrive in New Relic but the **AI Monitoring** section may not surface your app in its entity list. This is required for OTel-instrumented apps — apps using the New Relic agent get this automatically.

---

## Step 4 — Set environment variables

A `.env.example` file is provided at the project root with all required variables. Copy it and fill in your values:

```
cp ../.env.example ../.env
```

Then edit `../.env`:

```
OPENAI_API_KEY=<your_openai_api_key>
NEW_RELIC_LICENSE_KEY=<your_license_key>
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318
OTEL_SERVICE_NAME=llamaindex-sample-app
Replace lamaindex-sample-app with the name you wish to call the application.
LLM_MODEL=gpt-4o   # optional, defaults to gpt-4o
```

The collector also needs `NEW_RELIC_LICENSE_KEY`. Create a `.env` file in this `nr-config/` directory:

```
NEW_RELIC_LICENSE_KEY=<your_license_key>
```

Replace `<your_license_key>` with your [Account License Key](https://one.newrelic.com/launcher/api-keys-ui.launcher).

> For EU accounts, update the endpoint in [`otel-collector-llamaindex.yaml`](./otel-collector-llamaindex.yaml):
> `endpoint: https://otlp.eu01.nr-data.net:4318`

---

## Step 5 — Start the OTel Collector

> The collector must be running before the app starts. Spans sent before the collector is ready will arrive in New Relic without `gen_ai.*` attributes.

```
docker compose -f docker-compose-llamaindex.yaml up -d
```

Confirm it is ready:

```
docker logs llamaindex-otel-collector --since 10s
# Look for: "Everything is ready. Begin running and processing data."
```

---

## Step 6 — Run the application

From the project root:

```
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318 venv/bin/uvicorn llamaindex_app:app --port 8080
```

> Always set `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318` explicitly. If your shell already has this variable set to an external endpoint, spans will bypass the collector and arrive in New Relic without the `gen_ai.*` mappings.

---

## Step 7 — Send a request and verify

```
# FunctionCallingAgent endpoint
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"prompt": "What is 10 times 5?"}'

# ReActAgent endpoint
curl -X POST http://localhost:8080/chat/react \
  -H "Content-Type: application/json" \
  -d '{"prompt": "What is 20 divided by 4?"}'
```

Check your [New Relic account](https://one.newrelic.com) under **AI Monitoring** or **All Entities > Services - OpenTelemetry** to confirm data is flowing. For EU users check your [account here](https://one.eu.newrelic.com).

---

## Troubleshooting

**No data in New Relic?**
- Confirm `NEW_RELIC_LICENSE_KEY` is set correctly in both `../.env` and `nr-config/.env`
- Check the collector is running: `docker ps | grep llamaindex-otel-collector`
- Check collector logs for export errors: `docker logs llamaindex-otel-collector --since 60s`
- For EU accounts, verify the endpoint in [`otel-collector-llamaindex.yaml`](./otel-collector-llamaindex.yaml) is set to `https://otlp.eu01.nr-data.net:4318`

**Spans arriving in New Relic but missing `gen_ai.*` attributes?**
- The app is likely sending spans directly to New Relic, bypassing the collector. Always start the app with `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318` explicitly set — your shell environment may be overriding it
- Confirm the collector was running *before* the app started

**`gen_ai.input.messages` / `gen_ai.output.messages` missing?**
- These must be set manually in the app. Verify the wrapper span code is in place — see [`llamaindex_app.py` lines 138–147](../llamaindex_app.py#L138-L147) (FunctionCallingAgent) and [`llamaindex_app.py` lines 162–171](../llamaindex_app.py#L162-L171) (ReActAgent)

**`gen_ai.response.finish_reasons` missing?**
- This requires the custom `OpenAI` subclass. Verify it is defined and used as the LLM — see [`llamaindex_app.py` lines 37–49](../llamaindex_app.py#L37-L49)

**LlamaIndex spans not being captured at all?**
- `LlamaIndexInstrumentor().instrument()` must be called before any LlamaIndex imports. Check the initialization order in your app — see Step 1 above
- Confirm `openinference-instrumentation-llama-index==2.2.4` is installed. Version 3.x is incompatible with `llama-index-core 0.10.x` and silently fails

**App failing to connect to the collector?**
- Confirm the collector is listening on port `4318`: `docker logs llamaindex-otel-collector | grep "4318"`
- Confirm no firewall or port conflict: `lsof -i :4318`

---

## Additional References

For a deep-dive into span structure, before/after attribute transformation examples, and known limitations, see [`DOCUMENTATION.md`](../DOCUMENTATION.md).

| Resource | Description |
|----------|-------------|
| [DOCUMENTATION.md](../DOCUMENTATION.md) | Full technical reference: span trees, attribute mapping details, known limitations |
| [New Relic AI Monitoring](https://docs.newrelic.com/docs/ai-monitoring/) | How New Relic ingests and displays AI telemetry |
| [New Relic Account License Key](https://one.newrelic.com/launcher/api-keys-ui.launcher) | Where to find your license key |
| [LlamaIndex Documentation](https://docs.llamaindex.ai/) | LlamaIndex agents, tools, and LLM integrations |
| [OpenInference Semantic Conventions](https://github.com/Arize-ai/openinference) | Attribute names emitted by `LlamaIndexInstrumentor` |
| [OTel GenAI Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/gen-ai/) | Standard `gen_ai.*` attributes expected by New Relic |
| [OTel Collector Transform Processor](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/processor/transformprocessor) | How the `transform/genai` processor works |
| [OpenTelemetry Python SDK](https://opentelemetry.io/docs/languages/python/) | OTel SDK setup and configuration reference |
