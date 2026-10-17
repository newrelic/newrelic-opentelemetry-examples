# Strands Agents Math Tutor with OpenTelemetry and New Relic Integration

**Project:** Strands-sample-app
**Dependencies (as installed):**
- Python: `3.12.12`
- strands-agents: `1.52.0` (with OpenAI support; `requirements.txt` is unversioned — `pip show strands-agents` to confirm what you have)
- openai (SDK): `1.109.1`
- FastAPI: `0.135.1`
- Uvicorn: `0.42.0`
- Pydantic: `2.12.5`
- python-dotenv: `1.2.2`
- OpenTelemetry Collector: `0.91.0`

**Table of Contents:**
1. [Technology Stack Overview](#1-technology-stack-overview) — architecture, data flow, Strands Agents framework
2. [Application Configuration](#2-application-configuration) — setup code, environment variables, tools
3. [OTel Collector Configuration](#3-opentelemetry-collector-configuration) — pipeline, processors, exporters
4. [Strands Agent Telemetry](#4-strands-agent-telemetry--span-structure-and-attributes) — native attributes, span structure
5. [Running the Application](#5-running-the-application) — setup, testing, verification
6. [Known Gaps](#6-known-gaps) — fields that don't show up, and why
7. [Production Deployment](#7-production-deployment-considerations) — deployment guide
8. [Troubleshooting](#8-troubleshooting) — common issues and fixes

## 1. Technology Stack Overview

### 1.1 What is Strands Agents?

**Strands Agents** is a framework for building AI agents that provides:

- **Agents**: Autonomous agents that use LLMs to reason, plan, and decide which tools to call in an event loop
- **Tools**: Python functions decorated with `@tool` that agents can invoke to perform specific tasks
- **Built-in Telemetry**: Native OpenTelemetry instrumentation via `StrandsTelemetry` — no external instrumentor library needed
- **Model Flexibility**: Support for multiple model providers (OpenAI, Bedrock, etc.) via pluggable model classes

### 1.2 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    USER REQUEST                             │
│                  "What is 2 + 2?"                            │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│              FastAPI Application (strands_app.py)            │
│  • Receives HTTP request at /prompt endpoint                │
│  • Routes prompt to a single, module-level Strands Agent     │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│               Strands Agent (Event Loop)                    │
│  • Cycle 1: LLM decides to call add_numbers(2, 2) → 4       │
│  • Cycle 2: LLM generates the final response                │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│          StrandsTelemetry (Built-in OTel)                   │
│  • Creates spans for chat, execute_tool, execute_event_      │
│    loop_cycle, and invoke_agent operations                  │
│  • Emits gen_ai.* attributes directly on those spans         │
│    (gen_ai_span_attributes_only opt-in — no span events)    │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│      OpenTelemetry Collector                                 │
│  • Receives OTLP traces on port 4318                        │
│  • memory_limiter → resource (stamps service.name)           │
│  • No transform, no batch processor — spans pass through     │
│    largely as emitted                                        │
└─────────────────────────────────────────────────────────────┘
                           ↓
              ┌────────────┴────────────┐
              ↓                         ↓
┌──────────────────────┐   ┌──────────────────────┐
│   New Relic          │   │   Console (logging)  │
│  (staging endpoint)  │   │   + optional file     │
└──────────────────────┘   └──────────────────────┘
```

### 1.3 Data Flow Pipeline

1. **Application (Instrumentation)**: The Strands `Agent`, wrapped with `StrandsTelemetry`, automatically traces:
   - Agent execution (`invoke_agent Strands Agents` span)
   - Agent event loop cycles (`execute_event_loop_cycle` spans)
   - LLM chat calls (`chat` spans with `gen_ai.*` attributes, including `gen_ai.input.messages` / `gen_ai.output.messages` as JSON-string attributes)
   - Tool invocations (`execute_tool <name>` spans with tool metadata)

2. **OTel SDK (Transmission)**: `strands_app.py` hardcodes the OTLP exporter endpoint to `http://localhost:4318/v1/traces` — the `OTEL_EXPORTER_OTLP_ENDPOINT` env var has no effect on this app.

3. **OTel Collector (Routing)**: Receives traces, applies `memory_limiter` and `resource` (which force-sets `service.name: strands-math-tutor` and `deployment.environment: development`), then exports. There is currently **no transform processor and no batch processor** in the traces pipeline.

4. **Observability Platforms**:
   - **New Relic**: Receives whatever attributes Strands emitted natively — no extraction, remapping, or enrichment happens in the collector
   - **Console Logs**: Debug output via the `logging` exporter (`loglevel: info`)
   - **Local File**: Optional JSON export to `telemetry-data.json` via the `file` exporter


---

## 2. Application Configuration

### 2.1 Setup Overview

See [strands_app.py](strands_app.py) for the full implementation. There is no per-request agent creation and no conversation tracking — one `Agent` instance is created at import time and reused for every request.

```python
from strands import Agent, tool
from strands.telemetry import StrandsTelemetry
from strands.models.openai import OpenAIModel
from fastapi import FastAPI
from pydantic import BaseModel
from dotenv import load_dotenv, find_dotenv

# 1. Load environment variables
load_dotenv(find_dotenv(), override=True)

# 2. Define tools using the @tool decorator
@tool
def add_numbers(a: int, b: int) -> str:
    """Add two numbers together and return the result.

    Args:
        a: The first number.
        b: The second number.
    """
    return f"{a} + {b} = {a + b}"

# ... subtract_numbers, multiply_numbers, divide_numbers follow the same pattern

# 3. Set up telemetry (OTLP exporter + metrics) — endpoint is hardcoded
strands_telemetry = StrandsTelemetry()
strands_telemetry.setup_otlp_exporter(endpoint="http://localhost:4318/v1/traces")
strands_telemetry.setup_meter(
    enable_console_exporter=False,
    enable_otlp_exporter=True,
)

# 4. Initialize FastAPI app
app = FastAPI(
    title="Strands Math Tutor Agent",
    description="Math tutor agent with Strands tools and OpenTelemetry instrumentation",
)

# 5. Create ONE agent at module load time — no per-request state
openai_model = OpenAIModel(model_id="gpt-4o")

agent = Agent(
    model=openai_model,
    system_prompt=system_prompt,
    tools=[add_numbers, subtract_numbers, multiply_numbers, divide_numbers],
)

# 6. Endpoint just delegates to the shared agent
@app.post("/prompt", response_model=PromptResponse)
async def prompt_agent(request: PromptRequest):
    response = agent(request.prompt)
    return PromptResponse(response=str(response))
```

### 2.2 Environment Variables

| Variable | Purpose | Required | Actually used by this app? |
|---|---|---|---|
| `OPENAI_API_KEY` | OpenAI API authentication key | Yes | Yes |
| `NEW_RELIC_LICENSE_KEY` | New Relic license key, used by the OTel Collector | Yes (if exporting to NR) | Yes — read by the Docker Compose `env_file` for the collector container |
| `OTEL_SEMCONV_STABILITY_OPT_IN` | Selects GenAI semantic convention behavior | Yes (recommended) | Yes — `strands.telemetry` reads this to decide attribute vs. event emission |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | Standard OTel env var for the OTLP endpoint | No | **No effect** — `strands_app.py` hardcodes `http://localhost:4318/v1/traces` |
| `OTEL_SERVICE_NAME` | Standard OTel env var for the resource's service name | No | Partially — `StrandsTelemetry`'s `get_otel_resource()` does read it, but the collector's `resource` processor force-upserts `service.name: strands-math-tutor` afterward, overwriting it |

Recommended `.env` value:

```env
OTEL_SEMCONV_STABILITY_OPT_IN=gen_ai_latest_experimental,gen_ai_span_attributes_only
```

`gen_ai_span_attributes_only` is the setting that determines whether `gen_ai.input.messages` / `gen_ai.output.messages` land as span **attributes** (current behavior, with this flag) or as span **events** (behavior without it, on a separate span named `gen_ai.client.inference.operation.details`). Since the collector no longer has a transform processor for either shape, this flag mainly affects where the data lives on the span — not whether New Relic can further process it.

### 2.3 Key Implementation Points

- **Tool Definition:** `@tool`-decorated functions; docstrings become tool descriptions, and the `Args:` section becomes per-argument descriptions
- **Telemetry:** One `StrandsTelemetry()` instance, `setup_otlp_exporter()` + `setup_meter()` — no external instrumentor
- **Model Configuration:** `OpenAIModel(model_id="gpt-4o")`
- **Agent Creation:** A single `Agent(model=..., system_prompt=..., tools=[...])` at module scope — every request shares it
- **Invocation:** `agent("your prompt")` runs the full reasoning loop
- **No conversation/session tracking:** there is no `conversation_id` field on the request/response models and no `trace_attributes` passed to `Agent` — every request is telemetrically independent, even if it happens to reuse the same agent instance

### 2.4 Available Tools

| Tool | Description |
|---|---|
| `add_numbers(a, b)` | Adds two integers, e.g. `"2 + 3 = 5"` |
| `subtract_numbers(a, b)` | Subtracts b from a, e.g. `"10 - 3 = 7"` |
| `multiply_numbers(a, b)` | Multiplies two integers, e.g. `"4 * 5 = 20"` |
| `divide_numbers(a, b)` | Divides a by b; returns `"Error: Cannot divide by zero"` if `b == 0` |

Defined in [strands_app.py](strands_app.py).

---

## 3. OpenTelemetry Collector Configuration

See [strands-otel-collector-config.yaml](strands-otel-collector-config.yaml) for the full config.

### 3.1 Components

**Receivers:**
- OTLP gRPC on port 4317
- OTLP HTTP on port 4318 (used by the application)

**Processors (traces pipeline, in order):**
1. `memory_limiter` — Prevents OOM (512 MiB limit, 1s check interval)
2. `resource` — Force-upserts:
   - `service.name`: `strands-math-tutor`
   - `deployment.environment`: `development`

There is currently **no `transform` processor and no `batch` processor** in the traces pipeline. Spans are exported close to as-emitted by Strands, just with the resource attributes above stamped on.

**Exporters:**
- `otlphttp/newrelic` — Sends to New Relic's **staging** endpoint (`staging-otlp.nr-data.net:4318`)
- `logging` — Prints to collector stdout (`loglevel: info`)
- `file` — Writes to `./telemetry-data.json` (path is relative to the container's working directory, `/`, not the host — inspect it with `docker cp` or `docker exec`, not directly on the host filesystem)

### 3.2 Pipelines

| Pipeline | Receivers | Processors | Exporters |
|---|---|---|---|
| Traces | OTLP | `memory_limiter`, `resource` | `otlphttp/newrelic`, `logging` |
| Metrics | OTLP | `memory_limiter`, `batch`, `resource` | `otlphttp/newrelic` |
| Logs | OTLP | `memory_limiter`, `batch`, `resource` | `otlphttp/newrelic` |

Note the asymmetry: metrics and logs pipelines still batch; traces do not.

---

## 4. Strands Agent Telemetry — Span Structure and Attributes

### 4.1 Native `gen_ai.*` Attributes

Strands emits these directly on spans — no collector-side remapping happens for any of them currently:

| Attribute | Present on | Example |
|---|---|---|
| `gen_ai.system` | Every span | `strands-agents` (set natively in Strands' tracer — not by any collector transform) |
| `gen_ai.operation.name` | Every span | `chat`, `execute_tool`, `execute_event_loop_cycle`, `invoke_agent` |
| `gen_ai.provider.name` | Every span | `strands-agents` |
| `gen_ai.request.model` | `chat`, `invoke_agent` | `gpt-4o` |
| `gen_ai.input.messages` | `chat`, `execute_tool`, `execute_event_loop_cycle`, `invoke_agent` | JSON string, e.g. `[{"role":"user","parts":[{"type":"text","content":"What is 2+2?"}]}]` |
| `gen_ai.output.messages` | `chat`, `execute_tool`, `invoke_agent` | JSON string with an embedded `"finish_reason"` field, e.g. `[{"role":"assistant","parts":[...],"finish_reason":"end_turn"}]` |
| `gen_ai.usage.input_tokens` / `.output_tokens` / `.total_tokens` | `chat`, `invoke_agent` | Integers |
| `gen_ai.server.time_to_first_token` | `chat` | Milliseconds |
| `gen_ai.tool.name`, `.call.id`, `.call.arguments`, `.call.result`, `.description`, `.json_schema`, `.status` | `execute_tool` | Tool metadata and result |
| `event_loop.cycle_id`, `event_loop.parent_cycle_id` | `execute_event_loop_cycle` | UUIDs |
| `gen_ai.agent.name`, `gen_ai.agent.tools` | `invoke_agent` | `Strands Agents`, JSON array of tool names |

`finish_reason` is present, but only **embedded inside the `gen_ai.output.messages` JSON string** — it is never emitted as its own top-level attribute (see [Known Gaps](#6-known-gaps)).

### 4.2 Agent Trace Structure

Strands builds a hierarchical span tree per request:

| Span Name | `gen_ai.operation.name` | Description |
|---|---|---|
| `invoke_agent Strands Agents` | `invoke_agent` | The root span for the whole agent run |
| `execute_event_loop_cycle` | `execute_event_loop_cycle` | One iteration of the reasoning loop; nests a `chat` span and, if a tool was called, an `execute_tool` span |
| `chat` | `chat` | One LLM API call |
| `execute_tool <name>` | `execute_tool` | One tool execution |

Example, for "What is 9 \* 3?" (one tool call, then a final answer):

```
invoke_agent Strands Agents
└── execute_event_loop_cycle (cycle 1)
    ├── chat                          → finish_reason: tool_use (in gen_ai.output.messages)
    └── execute_tool multiply_numbers
        └── execute_event_loop_cycle (cycle 2, nested)
            └── chat                  → finish_reason: end_turn (in gen_ai.output.messages)
```

---

## 5. Running the Application

### 5.1 Prerequisites

| Requirement | Version | Purpose |
|---|---|---|
| Python | 3.12+ | Application runtime |
| Docker | 20.10+ | Running the OTel Collector container |
| Docker Compose | v2+ | Orchestrating the collector service |
| OpenAI API key | — | GPT-4o model access |
| New Relic license key | — | Telemetry export (staging endpoint by default) |

### 5.2 Setup

```bash
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

Create `.env` **at the project root** — not inside `nr-config/` — (see [`.env.example`](.env.example)):

```env
OPENAI_API_KEY=<your-openai-api-key>
NEW_RELIC_LICENSE_KEY=<your-new-relic-license-key>
OTEL_SEMCONV_STABILITY_OPT_IN=gen_ai_latest_experimental,gen_ai_span_attributes_only
```

**Security note:** `.env` is gitignored — never commit it. Rotate any key that has been pasted into a terminal, chat log, or shared screen.

`nr-config/docker-compose-strands.yaml` loads this file via `env_file: ../.env`, resolved relative to the compose file's own directory — so it always points at the project-root `.env` regardless of the working directory `docker compose` is invoked from. If `.env` ends up anywhere else, the collector container starts without `NEW_RELIC_LICENSE_KEY` and traces silently fail to export (no error — the OTLP exporter just gets rejected by New Relic's endpoint).


### 5.3 Start the OTel Collector

```bash
docker compose -f docker-compose-strands.yaml up -d
```

Verify:

```bash
docker ps --filter name=strands-otel-collector
```

Should show `Up` with ports `4317`, `4318`, `55679` mapped.

Restart after any config edit — the collector reads its config once at startup, not on file change:

```bash
docker compose -f docker-compose-strands.yaml restart strands-otel-collector
```

### 5.4 Run the Application

```bash
uvicorn strands_app:app --host 0.0.0.0 --port 8000 --reload
```

Server starts at `http://localhost:8000`.

### 5.5 Verify

```bash
curl -X POST http://localhost:8000/prompt \
  -H "Content-Type: application/json" \
  -d '{"prompt": "What is 15 multiplied by 7?"}'
```

Expected:

```json
{
  "response": "15 * 7 = 105\n\nThe result of multiplying 15 by 7 is 105."
}
```

Swagger UI: [http://localhost:8000/docs](http://localhost:8000/docs)

To confirm telemetry is flowing:

```bash
docker logs strands-otel-collector --tail 50
```

ZPages debug UI: [http://localhost:55679/debug/tracez](http://localhost:55679/debug/tracez)

---

## 6. Known Gaps

These fields won't show up in NRQL or the New Relic UI:

| Field | Status | Why |
|---|---|---|
| **Finish reason** | Not captured as its own attribute | Strands embeds `finish_reason` inside the `gen_ai.output.messages` JSON string, not as a top-level attribute. |
| **Response model** | Not captured | Strands' OpenAI integration never reads the `model` field back off the OpenAI API response. |
| **Response ID** | Not captured | Same cause — the raw HTTP response object isn't captured, so its `id` field is never read into a span attribute. |
| **Request ID** | Not captured | No `x-request-id` or equivalent header is captured anywhere in Strands' tracer or the OpenAI model wrapper. |
| **Host** | Not captured | Not a Strands limitation — this app doesn't configure an OTel host resource detector, so `Resource.create()` never picks up the machine's hostname. |
| **Conversation ID** | Not captured | This app never sets it — `Agent` is created once at module scope with no `trace_attributes`, and the request/response models have no conversation field. |


---

## 7. Production Deployment Considerations

### 7.1 Application

- Run behind Gunicorn + Uvicorn workers, or a process manager:
  ```bash
  gunicorn strands_app:app -w 4 -k uvicorn.workers.UvicornWorker --bind 0.0.0.0:8000
  ```
- Put a reverse proxy in front for TLS
- Drop `--reload` outside development

### 7.2 Environment Variables

- Use a secrets manager instead of a `.env` file in production
- Rotate API keys regularly, and immediately if one has ever been displayed in a terminal, log, or chat tool output

### 7.3 OTel Collector

- Pin the collector image to a specific version (currently `otel/opentelemetry-collector-contrib:0.91.0`)
- Switch the New Relic exporter from staging (`staging-otlp.nr-data.net`) to production (`otlp.nr-data.net`) before going live
- Consider re-adding a `batch` processor to the traces pipeline for efficiency at higher volume (currently absent, unlike the metrics/logs pipelines which still batch)

### 7.4 Containerizing

```dockerfile
FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY strands_app.py .
EXPOSE 8000
CMD ["uvicorn", "strands_app:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "4"]
```

---

## 8. Troubleshooting

### 8.1 Application won't start

| Symptom | Fix |
|---|---|
| `ModuleNotFoundError` | `pip install -r requirements.txt` |
| `OPENAI_API_KEY not set` | Create `.env` at the project root with your OpenAI key |
| `Connection refused on port 4318` | Start the collector: `docker compose -f docker-compose-strands.yaml up -d` |
| `Address already in use :8000` | `lsof -nP -iTCP:8000 -sTCP:LISTEN` then `kill <PID>` |

### 8.2 Collector config edits don't seem to take effect

The collector reads its config file **once at container startup** — editing `strands-otel-collector-config.yaml` on disk does nothing until you restart the container:

```bash
docker compose -f docker-compose-strands.yaml restart strands-otel-collector
```

Check `docker logs strands-otel-collector` after restarting — a YAML structural error (e.g. a missing top-level `exporters:` key) will show as `failed to get config: cannot unmarshal the configuration`, and the collector will keep crash-looping until fixed.

### 8.3 Telemetry not appearing in New Relic

1. `docker logs strands-otel-collector --tail 100` — look for `exporterhelper` errors on `otlphttp/newrelic`
2. Verify `NEW_RELIC_LICENSE_KEY` is correct and set in the project-root `.env` (not a `.env` inside `nr-config/` — `env_file: ../.env` in `nr-config/docker-compose-strands.yaml` won't find it there, and the collector will start with the variable unset, causing traces to silently fail to export)
3. Confirm you're looking at the **staging** account/UI — this config exports to `staging-otlp.nr-data.net`, not production
4. Query `Span` directly via NRQL to separate a real ingest problem from a UI-page gap:
   ```sql
   SELECT * FROM Span WHERE service.name = 'strands-math-tutor' SINCE 30 minutes ago LIMIT 20
   ```
5. If a specific field (e.g. finish reason) is missing from a NRQL result too, see [Known Gaps](#6-known-gaps) — it may genuinely not be captured, not just hidden in one UI page.

### 8.4 Agent returns errors

| Symptom | Fix |
|---|---|
| `RateLimitError` | Check usage at [platform.openai.com](https://platform.openai.com/account/rate-limits) |
| `AuthenticationError` | Verify `OPENAI_API_KEY` |
| Tools not being called | Confirm each `@tool` function has a docstring with an `Args:` section; try a simple prompt like "What is 2 + 3?" |
