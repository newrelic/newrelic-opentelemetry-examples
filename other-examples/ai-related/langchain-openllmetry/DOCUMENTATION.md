# LangChain + OTel GenAI Normalizer (OpenLLMetry) — Architecture 

**Python version:** 3.12
**OpenTelemetry SDK version:** 1.38.0+ (pinned via `traceloop-sdk`'s own dependency range)
**LangChain version:** 0.3.0+
**Instrumentation:** `traceloop-sdk` (`Traceloop.init()`), which pulls in
`opentelemetry-instrumentation-langchain` 0.62.3
**Normalization:** `genainormalizerprocessor` (config key `gen_ai_normalizer`), source `openllmetry`, collector `v0.159.0`+

---

## 1. Technology Stack Overview

### 1.1 What this sample demonstrates

A multi-agent LangChain trip planner whose spans are normalized into
standard `gen_ai.*` OTel GenAI semantic conventions almost entirely by the
collector, driven by declarative config — the application itself never
sets a `gen_ai.*` attribute by hand. The agent/tool graph is deliberately
shaped to exercise agent→tool, tool→tool, agent→agent, and tool→agent span
relationships, so the normalization pipeline has real structure to handle,
not just a single flat tool call.

### 1.2 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     CLI INVOCATION                          │
│   python app/run_openllmetry.py "Plan a trip to Berlin —    │
│   tell me the weather and the best places to visit"         │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│         Entry script (run_openllmetry.py)                   │
│  • Loads .env                                                 │
│  • Traceloop.init() — builds TracerProvider + OTLP/HTTP       │
│    exporter, stamps instrumentation.source=openllmetry        │
│  • Instruments LangChain's callback system + LangGraph        │
│  • Imports and calls agent.run(query)                        │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  LangChain multi-agent trip planner (agent.py)               │
│  • TripPlannerAgent delegates to WeatherAgent / PlacesAgent  │
│    via tool-shaped wrappers (ask_weather_agent /              │
│    ask_places_agent) — agent→agent, tool→agent patterns      │
│  • WeatherAgent → get_weather, PlacesAgent → get_places      │
│    — agent→tool pattern                                       │
│  • get_weather internally calls _celsius_to_fahrenheit        │
│    — tool→tool pattern (plain Python, not its own span)      │
│  • ZERO OTel imports — instrumentation-agnostic by design    │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│   opentelemetry-instrumentation-langchain                    │
│   (patches LangChain + LangGraph callback/invocation paths)  │
│     - gen_ai.request.model, gen_ai.usage.*                    │
│     - gen_ai.tool.name / gen_ai.agent.name (see Section 8 for a gap)│
│     - traceloop.entity.name, traceloop.span.kind (legacy)     │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│    OpenTelemetry SDK (Transmission via OTLP/HTTP)            │
│  • SimpleSpanProcessor (disable_batch=True) + HTTP exporter   │
│  • Sends to localhost:4318                                    │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│   OpenTelemetry Collector (Docker: langchain-openllmetry-    │
│   otel-collector, image pinned to v0.159.0+)                 │
│  • Receives OTLP on 4317 (gRPC) / 4318 (HTTP)                 │
│  • memory_limiter, resource (service.name,                    │
│    deployment.environment upsert)                             │
│  • gen_ai_normalizer (source: openllmetry,                    │
│    remove_originals: true) — renames remaining vendor         │
│    attrs → gen_ai.*                                           │
│  • transform/fix_tool_agent_conflict (OTTL) — strips a stray  │
│    gen_ai.agent.name the instrumentor+normalizer combination  │
│    puts on non-agent spans (see Section 8)                           │
│  • batch                                                       │
│  • Exports traces + metrics + logs to New Relic (+ debug      │
│    console exporter for local inspection)                     │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│                     New Relic Platform                      │
│  • AI Monitoring Dashboard                                    │
│  • LLM Token Usage & Cost Tracking                            │
│  • Tool Call Analytics                                        │
│  • Distributed Tracing with Waterfall View                    │
│  • Query by gen_ai.tool.name, gen_ai.operation.name, etc.      │
└─────────────────────────────────────────────────────────────┘
```

### 1.3 Why this architecture?

* **LangChain** (`create_agent`): agent workflow logic and tool
  orchestration, kept 100% free of observability code.
* **`gen_ai_normalizer` for the bulk of the mapping**: the `openllmetry`
  source's built-in mapping table (source attribute → `gen_ai.*` target)
  ships built into the processor. A small `transform/fix_tool_agent_conflict`
  OTTL processor fixes a gap this app's traces exposed in that built-in
  mapping — see "Known Limitations" below.
* **OTel Collector**: vendor-agnostic attribute transformation and routing,
  identical to any other collector-based pipeline.
* **New Relic**: observability platform and AI Monitoring visualization.

---

## 2. Data Flow Pipeline

1. **Application** (`app/agent.py`):
   * Three agents built with `create_agent`: `TripPlannerAgent` (top-level
     supervisor), `WeatherAgent`, `PlacesAgent` (leaf specialists)
   * Four `@tool` functions: `get_weather`, `get_places` (leaf tools) and
     `ask_weather_agent`, `ask_places_agent` (tool-shaped wrappers that
     invoke a sub-agent — the tool→agent pattern)
   * `get_weather` calls the plain helper `_celsius_to_fahrenheit`
     internally — the tool→tool pattern
   * No `TracerProvider`, no `gen_ai.*` attribute code anywhere in this file
2. **Entry script** (`app/run_openllmetry.py`) — `Traceloop.init()` must
   run **before** `agent` is imported: it needs to patch LangChain's and
   LangGraph's internals before those modules build any `Runnable`
   instances, or spans are missed.
3. **OTel SDK**: `SimpleSpanProcessor` (`disable_batch=True`, for
   deterministic flush on a short-lived CLI script) + OTLP/HTTP exporter →
   `localhost:4318`.
4. **OTel Collector** (`otel-collector-config.yaml`):
   * Receives on 4317 (gRPC) and 4318 (HTTP)
   * `gen_ai_normalizer` renames remaining source attributes into `gen_ai.*`
     per the built-in `openllmetry` mapping table, removing the originals
   * `transform/fix_tool_agent_conflict` strips `gen_ai.agent.name` from
     any span whose `gen_ai.operation.name` isn't `invoke_agent` or
     `create_agent` (see Section 8)
   * Exports traces, metrics, and logs to the configured New Relic endpoint
5. **New Relic**: ingests and visualizes telemetry

---

## 3. Application Configuration

### Initialization order

> ⚠️ `Traceloop.init()` must run **before** `agent` is imported. The entry
> script enforces this with a `# noqa: E402` import of `agent` placed
> after the instrumentation setup — moving that import earlier silently
> breaks span capture with no error.

```python
# run_openllmetry.py
import os
import sys

from dotenv import load_dotenv
from traceloop.sdk import Traceloop

load_dotenv()

Traceloop.init(
    app_name=os.getenv("SERVICE_NAME", "langchain-openllmetry-demo"),
    disable_batch=True,
    api_endpoint=os.getenv("OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4318"),
    resource_attributes={"instrumentation.source": "openllmetry"},
)

from agent import run  # noqa: E402  (import after instrumentation is wired up)
```

`disable_batch=True` swaps in a `SimpleSpanProcessor` instead of the
default `BatchSpanProcessor`, giving synchronous per-span export — needed
for a CLI script that exits right after the request completes.
`Traceloop.init()` also registers an `atexit` flush handler on its own, so
no explicit flush call is required at the end of the script.

### `Traceloop.init()` real kwarg names

Confirmed against the installed `traceloop-sdk` 0.62.3 source
(`traceloop/sdk/__init__.py`, `traceloop/sdk/tracing/tracing.py`):

| Kwarg | Notes |
|---|---|
| `app_name` | Becomes `service.name` unless overridden by `resource_attributes`. |
| `disable_batch` | `True` → `SimpleSpanProcessor`. Also auto-forced `True` inside a notebook. |
| `api_endpoint` | **Not** `exporter_endpoint` — a common wrong guess. URL scheme controls the wire protocol (see below). Also settable via `TRACELOOP_BASE_URL`. |
| `resource_attributes` | Dict merged into the OTel `Resource` alongside `service.name`. |
| `api_key` | Only enforced (and only blocks init) when `api_endpoint` is still the default `https://api.traceloop.com` **and** no key is set. Pointing at a local collector skips this check entirely — no key needed. |
| `metrics_exporter` / `processor` / `exporter` | Escape hatches to bring your own pre-built exporter/processor, bypassing `api_endpoint` parsing. |

**Scheme → wire protocol** (`init_spans_exporter()` in `tracing.py`):

| `api_endpoint` scheme | Exporter |
|---|---|
| `http://` / `https://` | HTTP OTLP (`.../v1/traces` appended) |
| `grpc://` | gRPC OTLP, insecure |
| `grpcs://` | gRPC OTLP, secure/TLS |
| *(no scheme)* | gRPC OTLP, insecure (backward-compat fallback) |

This app uses `http://localhost:4318` — see Section 8.2 for why a gRPC endpoint
was tried first and abandoned.

### Tool definition patterns (`app/agent.py`)

```python
def _celsius_to_fahrenheit(celsius: float) -> float:
    return celsius * 9 / 5 + 32


@tool
def get_weather(city: str) -> str:
    """Look up the current weather for a city."""
    celsius = 22
    fahrenheit = _celsius_to_fahrenheit(celsius)  # tool → tool: plain helper call
    return f"{city}: {celsius}C ({fahrenheit:.0f}F), sunny"


@tool
def ask_weather_agent(query: str) -> str:
    """Delegate a weather question to the weather specialist agent."""
    result = weather_agent.invoke({"messages": [{"role": "user", "content": query}]})
    return result["messages"][-1].content  # tool → agent: wraps a sub-agent invocation
```

`opentelemetry-instrumentation-langchain` automatically captures, for each
tool call: `gen_ai.tool.name`, `gen_ai.tool.description`,
`gen_ai.tool.type`, `gen_ai.tool.call.arguments`, `gen_ai.tool.call.result`
— these arrive as native `gen_ai.*` names already (see Section 5).

### Required environment variables

| Variable | Purpose | Example value |
|---|---|---|
| `OPENAI_API_KEY` | OpenAI API authentication | `sk-...` |
| `NEW_RELIC_LICENSE_KEY` | New Relic ingest key (used by collector) | `...NRAL` |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | OTel Collector OTLP/HTTP endpoint | `http://localhost:4318` |
| `SERVICE_NAME` | App-side `service.name` — **overridden**, see below | `langchain-genai-openllm` |
| `LLM_MODEL` | (Optional) override LLM model | `gpt-4o` |

`OTEL_EXPORTER_OTLP_ENDPOINT` defaults to `http://localhost:4318` in the
entry script if unset — if you're also running another OTel Collector
locally, confirm this one is actually listening on that port before
assuming an empty New Relic UI means a New Relic-side problem.

**`SERVICE_NAME` does not control the `service.name` New Relic sees.**
`run_openllmetry.py` passes it as `app_name` to `Traceloop.init()`, which
sets `service.name` client-side — but the collector's `resource` processor
(`otel-collector-config.yaml`) then upserts `service.name` to the
hardcoded value `langchain-genai-openllm` unconditionally, overwriting
whatever the app sent. Changing `SERVICE_NAME` in `.env` has no effect on
what shows up in New Relic; to actually rename the service, edit the
`resource` processor's `service.name` value in
`otel-collector-config.yaml` instead.

---

## 4. OpenTelemetry Collector Configuration

### Files

| File | Role |
|---|---|
| `otel-collector-config.yaml` | **Active config** — mounted by Docker Compose into the container |
| `docker-compose.yml` | Docker Compose definition for the collector |

### Docker Compose (`docker-compose.yml`)

```yaml
services:
  otel-collector:
    image: otel/opentelemetry-collector-contrib:0.159.0
    container_name: langchain-openllmetry-otel-collector
    command: ["--config=/etc/otelcol-contrib/config.yaml"]
    volumes:
      - ./otel-collector-config.yaml:/etc/otelcol-contrib/config.yaml
    ports:
      - "4317:4317"   # OTLP gRPC receiver
      - "4318:4318"   # OTLP HTTP receiver
      - "55679:55679" # ZPages extension
    env_file:
      - .env
    restart: unless-stopped
    networks:
      - langchain-openllmetry-network

networks:
  langchain-openllmetry-network:
    driver: bridge
```

**The image tag is load-bearing.** `gen_ai_normalizer` is `alpha`
stability and only appears in the upstream release manifest starting at
`v0.159.0`. Pulling `:latest` before that processor's release date
produces a collector that crash-loops on startup with:

```
Error: failed to get config: cannot unmarshal the configuration: decoding
failed due to the following error(s):
'processors' unknown type: "gen_ai_normalizer" for id: "gen_ai_normalizer"
```

Verify any image change with:

```bash
docker run --rm <image> components | grep gen_ai_normalizer
```

### Active config (`otel-collector-config.yaml`)

#### Receivers

```yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318
```

#### Extensions

```yaml
extensions:
  zpages:
    endpoint: 0.0.0.0:55679
```

#### Processors

```yaml
processors:
  memory_limiter:
    check_interval: 1s
    limit_mib: 512

  resource:
    attributes:
      - key: service.name
        value: langchain-genai-openllm
        action: upsert
      - key: deployment.environment
        value: development
        action: upsert

  gen_ai_normalizer:
    sources:
      - name: openllmetry
        remove_originals: true

  transform/fix_tool_agent_conflict:
    error_mode: ignore
    trace_statements:
      - context: span
        statements:
          - 'delete_key(attributes, "gen_ai.agent.name") where attributes["gen_ai.operation.name"] != nil and attributes["gen_ai.operation.name"] != "invoke_agent" and attributes["gen_ai.operation.name"] != "create_agent" and attributes["gen_ai.agent.name"] != nil'

  batch:
    timeout: 10s
    send_batch_size: 1024
```

`resource` (service.name / deployment.environment) runs as its own
processor since that's env-level metadata, not something the normalizer
touches — its documented scope is span attributes only, never resource
attributes (see Section 8.3).

### Built-in `openllmetry` → `gen_ai.*` mapping table

From the processor's own README
(`processor/genainormalizerprocessor/README.md`), verified against the
canonical `internal/openllmetry/mappings.go`:

| Source attribute | Target attribute | Notes |
|---|---|---|
| `llm.usage.prompt_tokens` | `gen_ai.usage.input_tokens` | |
| `llm.usage.completion_tokens` | `gen_ai.usage.output_tokens` | |
| `llm.request.model` | `gen_ai.request.model` | |
| `llm.response.model` | `gen_ai.response.model` | |
| `llm.request.max_tokens` | `gen_ai.request.max_tokens` | |
| `llm.request.temperature` | `gen_ai.request.temperature` | |
| `llm.request.top_p` | `gen_ai.request.top_p` | |
| `llm.top_k` | `gen_ai.request.top_k` | |
| `llm.frequency_penalty` | `gen_ai.request.frequency_penalty` | |
| `llm.presence_penalty` | `gen_ai.request.presence_penalty` | |
| `llm.chat.stop_sequences` | `gen_ai.request.stop_sequences` | |
| `llm.request.functions` | `gen_ai.tool.definitions` | source shape preserved |
| `llm.response.finish_reason` | `gen_ai.response.finish_reasons` | string wrapped into `[]string` |
| `llm.response.stop_reason` | `gen_ai.response.finish_reasons` | same target; undefined precedence if both present |
| `llm.request.type` | `gen_ai.operation.name` | value-mapped, see below |
| `traceloop.span.kind` | `gen_ai.operation.name` | value-mapped, see below — **`task` folds to `invoke_agent`, see Section 8** |
| `traceloop.entity.name` | `gen_ai.agent.name` | **unconditional — see Section 8, this is the root of the tool/agent conflict** |
| `traceloop.entity.input` | `gen_ai.input.messages` | source shape preserved |
| `traceloop.entity.output` | `gen_ai.output.messages` | source shape preserved |

**Value mappings onto `gen_ai.operation.name`:**

| Source attribute | Source value | Target value |
|---|---|---|
| `traceloop.span.kind` | `workflow` | `invoke_workflow` |
| `traceloop.span.kind` | `task` | `invoke_agent` |
| `traceloop.span.kind` | `agent` | `invoke_agent` |
| `traceloop.span.kind` | `tool` | `execute_tool` |
| `llm.request.type` | `completion` | `text_completion` |
| `llm.request.type` | `chat` | `chat` |
| `llm.request.type` | `rerank` | `retrieval` |
| `llm.request.type` | `embedding` | `embeddings` |

**Important**: as of the installed `opentelemetry-instrumentation-langchain`
0.62.3, most of this table is a no-op for this app — the instrumentor
already emits `gen_ai.*` attributes directly (`gen_ai.request.model`,
`gen_ai.usage.input_tokens`, `gen_ai.input.messages`,
`gen_ai.output.messages`, `gen_ai.tool.name`, `gen_ai.operation.name`, ...)
rather than the older `llm.*`/`traceloop.*` shape this table expects. The
one row that **does** still matter in practice is `traceloop.entity.name →
gen_ai.agent.name` — that legacy attribute is still set by the
instrumentor on every task/tool span, and the normalizer still renames it
unconditionally. See Section 8 for the consequence and the fix.

#### Exporters

```yaml
exporters:
  debug:
    verbosity: detailed
  otlphttp/newrelic:
    # Production: https://otlp.nr-data.net (EU: https://otlp.eu01.nr-data.net)
    endpoint: https://staging-otlp.nr-data.net:4318
    headers:
      api-key: ${env:NEW_RELIC_LICENSE_KEY}
    compression: gzip
    timeout: 30s
    retry_on_failure:
      enabled: true
      initial_interval: 5s
      max_interval: 30s
      max_elapsed_time: 300s
```

`debug` prints normalized spans to `docker logs` for local verification
without needing a New Relic account.

#### Pipelines

```yaml
service:
  extensions: [zpages]
  pipelines:
    traces:
      receivers: [otlp]
      processors: [memory_limiter, resource, gen_ai_normalizer, transform/fix_tool_agent_conflict, batch]
      exporters: [debug, otlphttp/newrelic]

    metrics:
      receivers: [otlp]
      processors: [memory_limiter, resource, batch]
      exporters: [otlphttp/newrelic]

    logs:
      receivers: [otlp]
      processors: [memory_limiter, resource, batch]
      exporters: [otlphttp/newrelic]
```

`gen_ai_normalizer` and `transform/fix_tool_agent_conflict` only appear in
the `traces` pipeline — they normalize span attributes and have nothing to
act on in the `metrics`/`logs` pipelines.

---

## 5. Semantic Conventions and Attribute Mapping

### Attributes emitted directly by `opentelemetry-instrumentation-langchain` 0.62.3

Captured from a real run (`python app/run_openllmetry.py "Plan a trip to
Lisbon — tell me the weather and the best places to visit"`, collector
`debug` exporter output):

```
gen_ai.provider.name: langgraph            # tool/task spans
gen_ai.provider.name: openai               # LLM chat spans
gen_ai.provider.name: langchain            # create_agent spans
gen_ai.operation.name: chat                # LLM call spans
gen_ai.operation.name: execute_tool        # tool-execution spans
gen_ai.operation.name: invoke_agent        # real agent invocation spans
gen_ai.operation.name: create_agent        # create_agent() factory-call spans
gen_ai.operation.name: execute_task        # LangGraph internal node spans (model, tools)
gen_ai.tool.name: get_weather
gen_ai.tool.description: Look up the current weather for a city.
gen_ai.tool.type: function
gen_ai.agent.name: WeatherAgent            # correctly, on invoke_agent/create_agent spans
gen_ai.request.model: gpt-4o
gen_ai.response.model: gpt-4o-2024-08-06
gen_ai.usage.input_tokens: 119
gen_ai.usage.output_tokens: 20
gen_ai.input.messages: [{"role": "user", "parts": [{"type": "text", "content": "..."}]}]
gen_ai.output.messages: [{"role": "assistant", "parts": [{"type": "tool_call", ...}], "finish_reason": "tool_call"}]
gen_ai.response.finish_reasons: ["tool_call"]
```

These already match OTel GenAI semantic conventions — no `llm.*`-prefixed
originals survive on most spans, so `gen_ai_normalizer`'s `openllmetry`
mapping table has little left to do here. The instrumentor also leaves a
long tail of `traceloop.*` attributes on every span
(`traceloop.workflow.name`, `traceloop.entity.path`,
`traceloop.association.properties.*`) — these have no row in the mapping
table and pass through unrenamed; they're harmless bookkeeping/debug
attributes, not part of the GenAI semconv surface.

### Summary: OpenLLMetry attribute → `gen_ai_normalizer` mapping → what New Relic receives

Combines the raw instrumentor output (left), the `gen_ai_normalizer`
`openllmetry` mapping table (middle), and the attribute actually visible in
New Relic after `gen_ai_normalizer` + `transform/fix_tool_agent_conflict`
run (right — see Section 8.1 for why the two can differ on
`gen_ai.agent.name`).

| Attribute emitted by OpenLLMetry / instrumentor | `gen_ai_normalizer` mapping | Attribute received by New Relic |
|---|---|---|
| `llm.usage.prompt_tokens` (legacy shape only) | → `gen_ai.usage.input_tokens` | `gen_ai.usage.input_tokens` |
| `llm.usage.completion_tokens` (legacy shape only) | → `gen_ai.usage.output_tokens` | `gen_ai.usage.output_tokens` |
| `llm.request.model` (legacy shape only) | → `gen_ai.request.model` | `gen_ai.request.model` |
| `llm.response.model` (legacy shape only) | → `gen_ai.response.model` | `gen_ai.response.model` |
| `llm.request.max_tokens` (legacy shape only) | → `gen_ai.request.max_tokens` | `gen_ai.request.max_tokens` |
| `llm.request.temperature` (legacy shape only) | → `gen_ai.request.temperature` | `gen_ai.request.temperature` |
| `llm.request.top_p` (legacy shape only) | → `gen_ai.request.top_p` | `gen_ai.request.top_p` |
| `llm.top_k` (legacy shape only) | → `gen_ai.request.top_k` | `gen_ai.request.top_k` |
| `llm.frequency_penalty` (legacy shape only) | → `gen_ai.request.frequency_penalty` | `gen_ai.request.frequency_penalty` |
| `llm.presence_penalty` (legacy shape only) | → `gen_ai.request.presence_penalty` | `gen_ai.request.presence_penalty` |
| `llm.chat.stop_sequences` (legacy shape only) | → `gen_ai.request.stop_sequences` | `gen_ai.request.stop_sequences` |
| `llm.request.functions` (legacy shape only) | → `gen_ai.tool.definitions` (shape preserved) | `gen_ai.tool.definitions` |
| `llm.response.finish_reason` / `llm.response.stop_reason` (legacy shape only) | → `gen_ai.response.finish_reasons` (string wrapped into array; undefined precedence if both present) | `gen_ai.response.finish_reasons` |
| `llm.request.type` (legacy shape only) | → `gen_ai.operation.name` (value-mapped: `completion`→`text_completion`, `chat`→`chat`, `rerank`→`retrieval`, `embedding`→`embeddings`) | `gen_ai.operation.name` |
| `traceloop.span.kind` (still emitted today) | → `gen_ai.operation.name` (value-mapped: `workflow`→`invoke_workflow`, `task`→`invoke_agent`, `agent`→`invoke_agent`, `tool`→`execute_tool`) | `gen_ai.operation.name` — but the instrumentor's own direct `gen_ai.operation.name` (`chat`, `execute_tool`, `invoke_agent`, `create_agent`, `execute_task`) is already present and takes precedence in practice |
| `traceloop.entity.name` (still emitted today, on **every** task/tool span) | → `gen_ai.agent.name` (unconditional, no span-kind check) | `gen_ai.agent.name` **only on `invoke_agent`/`create_agent` spans** — `transform/fix_tool_agent_conflict` deletes it everywhere else (see Section 8.1); without that processor it would also leak onto `execute_tool` and `execute_task` spans |
| `traceloop.entity.input` (legacy shape only) | → `gen_ai.input.messages` (shape preserved) | `gen_ai.input.messages` — already emitted directly by the instrumentor on most spans, so this row rarely fires |
| `traceloop.entity.output` (legacy shape only) | → `gen_ai.output.messages` (shape preserved) | `gen_ai.output.messages` — already emitted directly by the instrumentor on most spans, so this row rarely fires |
| `gen_ai.*` (already-normalized names — `gen_ai.provider.name`, `gen_ai.request.model`, `gen_ai.tool.name`, `gen_ai.usage.*`, `gen_ai.input.messages`/`gen_ai.output.messages`, `gen_ai.response.*`) | no row needed — already the target shape, passed through untouched | same `gen_ai.*` attribute, unchanged |
| `traceloop.workflow.name`, `traceloop.entity.path`, `traceloop.association.properties.*` | no row in the mapping table | passed through **unrenamed** — harmless bookkeeping, not part of the GenAI semconv surface |

---

## 6. Agent Trace Structure

`app/agent.py` is a small multi-agent **trip planner**, deliberately
shaped to exercise all four span-relationship patterns this normalizer
pipeline needs to handle:

| Pattern | Where it appears |
|---|---|
| agent → tool | `WeatherAgent` → `get_weather`; `PlacesAgent` → `get_places` |
| tool → tool | `get_weather` calls `_celsius_to_fahrenheit` internally (plain Python — not its own span; see note below) |
| agent → agent / tool → agent | `TripPlannerAgent` → `ask_weather_agent` (tool span) → nested `WeatherAgent` span; same for `ask_places_agent` → `PlacesAgent` |

`ask_weather_agent` / `ask_places_agent` are `@tool`-decorated functions
whose bodies call `weather_agent.invoke(...)` / `places_agent.invoke(...)`
— the standard LangChain idiom for exposing one agent as a callable tool
of another. From `TripPlannerAgent`'s perspective these look like
ordinary tool calls (they get `gen_ai.tool.name` like any other tool), but
one level deeper in the trace they open into a full nested agent
invocation.

### Span list for "Plan a trip to Lisbon — tell me the weather and the best places to visit"

Verified against a real run's `docker logs
langchain-openllmetry-otel-collector` output:

```
create_agent WeatherAgent      gen_ai.operation.name=create_agent   gen_ai.agent.name=WeatherAgent
create_agent PlacesAgent       gen_ai.operation.name=create_agent   gen_ai.agent.name=PlacesAgent
create_agent TripPlannerAgent  gen_ai.operation.name=create_agent   gen_ai.agent.name=TripPlannerAgent
execute_tool get_weather       gen_ai.operation.name=execute_tool   gen_ai.tool.name=get_weather
execute_tool get_places        gen_ai.operation.name=execute_tool   gen_ai.tool.name=get_places
invoke_agent WeatherAgent      gen_ai.operation.name=invoke_agent   gen_ai.agent.name=WeatherAgent
execute_tool ask_weather_agent gen_ai.operation.name=execute_tool   gen_ai.tool.name=ask_weather_agent
invoke_agent PlacesAgent       gen_ai.operation.name=invoke_agent   gen_ai.agent.name=PlacesAgent
execute_tool ask_places_agent  gen_ai.operation.name=execute_tool   gen_ai.tool.name=ask_places_agent
TripPlannerAgent.workflow      gen_ai.operation.name=invoke_agent   gen_ai.agent.name=TripPlannerAgent
invoke_agent TripPlannerAgent  gen_ai.operation.name=invoke_agent   gen_ai.agent.name=TripPlannerAgent
```

No span carries both `gen_ai.tool.name` and `gen_ai.agent.name`. Exactly
the four tool spans (`get_weather`, `get_places`, `ask_weather_agent`,
`ask_places_agent`) have `gen_ai.tool.name`; exactly the three real agents
(`TripPlannerAgent`, `WeatherAgent`, `PlacesAgent`) have `gen_ai.agent.name`
— **after** `transform/fix_tool_agent_conflict` runs (see Section 8.1 for what it
looks like without that processor).

**Every agent and tool call in this tree is a genuine, model-driven
decision** — not scripted or hardcoded. `create_agent` lets the model
choose whether/which tools to call on each turn; nothing in `app/agent.py`
forces this sequence.

**`_celsius_to_fahrenheit` never appears as its own span** — it's a plain
Python function call inside `get_weather`, not a LangChain-visible
operation, so the callback-based instrumentor has nothing to hook. Verify
the tool→tool effect indirectly, via the `72F` conversion showing up in
`get_weather`'s `gen_ai.output.messages`, rather than by looking for a
fourth span.

---

## 7. Running the Application

### Setup

**1. Create virtual environment and install dependencies:**

```bash
python3.12 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

`langchain.agents.create_agent` requires LangChain `>=0.3.0` and was
developed/verified against Python 3.12 — a much older interpreter may not
resolve compatible wheels.

**2. Configure environment variables:**

```bash
cp .env.example .env
# fill in OPENAI_API_KEY and NEW_RELIC_LICENSE_KEY
```

**3. Start the OTel Collector:**

```bash
docker compose up -d
```

**4. Run a prompt:**

```bash
python app/run_openllmetry.py "Plan a trip to Berlin — tell me the weather and the best places to visit"
```

Or use `./run.sh` to bring everything up and fire 4 sample trip-planning
prompts automatically.

**5. View traces in New Relic:**

* Navigate to **APM & Services** → `langchain-genai-openllm` (the
  collector's `resource` processor overrides `SERVICE_NAME` — see
  "Required environment variables" above)
* Open **AI Monitoring** or Distributed Tracing to see the span waterfall
* Query: `SELECT * FROM Span WHERE gen_ai.tool.name = 'get_weather'`

---

## 8. Known Limitations

### 8.1 `gen_ai.agent.name` leaks onto non-agent spans — root cause and fix

**Mechanism, confirmed by reading both sides directly:**

1. `opentelemetry-instrumentation-langchain`'s `callback_handler.py`
   (`_create_task_span`, called from both `on_chain_start` and
   `on_tool_start`) sets `traceloop.entity.name` **unconditionally** on
   every task/tool span, using that span's own node name — `model`,
   `tools`, `get_weather`, `ask_weather_agent`, etc. This is legacy
   OpenLLMetry bookkeeping, not gated by whether the span represents a
   real agent.
2. `gen_ai_normalizer`'s `openllmetry` source (confirmed against the
   processor's own README and `internal/openllmetry/mappings.go`) maps
   `traceloop.entity.name → gen_ai.agent.name` — also unconditionally,
   with no span-kind check.
3. The combination means `gen_ai.agent.name` ends up on:
   * The three real agent spans (correct — `TripPlannerAgent`,
     `WeatherAgent`, `PlacesAgent`).
   * LangGraph's internal per-step task spans (`execute_task model`,
     `execute_task tools`) — **incorrect**, these are graph plumbing, not
     agents.
   * Every tool-execution span (`execute_tool get_weather`, `execute_tool
     ask_weather_agent`, ...) — **incorrect**, and this collides with
     `gen_ai.tool.name` on the same span.

**Confirmed empirically**, A/B tested by temporarily removing the fix from
the pipeline and re-running an identical request: without a fix,
`gen_ai.agent.name` values observed across a single trip-planning request
include `PlacesAgent`, `TripPlannerAgent`, `WeatherAgent`, `model`, and
`tools` — the last two are LangGraph's internal node spans getting
misclassified as agents. New Relic's AI Monitoring UI reflected this
directly: the Agents column showed `model` and `tools` alongside the
three real agents, and the Tools column stopped showing entries whose
span also carried `gen_ai.agent.name` — New Relic's AI Monitoring
entity-synthesis layer treats "is a tool span" and "is an agent span" as
mutually exclusive per span, so a span carrying both attributes confuses
that classification.

**Fix** — `transform/fix_tool_agent_conflict` (OTTL, run immediately after
`gen_ai_normalizer` in the traces pipeline):

```yaml
transform/fix_tool_agent_conflict:
  error_mode: ignore
  trace_statements:
    - context: span
      statements:
        - 'delete_key(attributes, "gen_ai.agent.name") where attributes["gen_ai.operation.name"] != nil and attributes["gen_ai.operation.name"] != "invoke_agent" and attributes["gen_ai.operation.name"] != "create_agent" and attributes["gen_ai.agent.name"] != nil'
```

This keeps `gen_ai.agent.name` only on spans whose `gen_ai.operation.name`
is `invoke_agent` or `create_agent` — the two operations that genuinely
represent an agent — and strips it from everything else (`execute_tool`,
`execute_task`, `chat`, ...). Verified after the fix: `gen_ai.agent.name`
values across the same request are exactly `PlacesAgent`,
`TripPlannerAgent`, `WeatherAgent` — nothing else — and no span carries
both `gen_ai.tool.name` and `gen_ai.agent.name` simultaneously.

**Why `delete_key`, and why a `where`-scoped rule:**

* **`delete_key` over `set(..., nil)`**: OTTL's `set()` requires a
  replacement value; there's no `gen_ai.agent.name` value that means
  "absent" other than not having the key at all. New Relic's AI Monitoring
  entity-synthesis layer checks for the *presence* of `gen_ai.agent.name`
  to decide whether a span becomes an `AI_AGENT` entity — setting it to an
  empty string or `nil` would either fail type validation or still count
  as "present" depending on how the backend checks. `delete_key` is the
  only OTTL function that removes the attribute outright, which is what
  "this span is not an agent" actually requires.
* **Scoped by `gen_ai.operation.name`, not by tool-name co-presence**: an
  earlier version of this fix keyed off whether `gen_ai.tool.name` was
  also present on the same span. That version caught the tool-execution
  leak but missed the LangGraph internal task spans (`model`, `tools`) —
  those have no tool name at all, just a bogus agent name, so a fix
  scoped only to "tool name present" silently let them through. Scoping
  by `gen_ai.operation.name` instead catches both leak paths in one rule.
  This was confirmed the hard way via the A/B test described above.
* **A separate `transform` processor, not a change inside
  `gen_ai_normalizer`**: `gen_ai_normalizer`'s built-in `openllmetry`
  mapping table is fixed, shipped code — it has no `where`-clause
  mechanism of its own to make the `traceloop.entity.name →
  gen_ai.agent.name` rename conditional on span kind. The only place left
  to add that condition is a processor that runs *after* the rename has
  already happened, which is exactly what `transform`/OTTL is for.

**This rule is mandatory, not defensive/optional.** It was A/B tested by
removing it from the traces pipeline and re-running an otherwise identical
request — `model`/`tools` reappeared as bogus agents immediately. It has
also been observed to get silently commented out or dropped from the
pipeline list by IDE autoformat/autosave during editing; if `model` or
`tools` reappear in the New Relic Agents view, check
`otel-collector-config.yaml` for both (a) the processor block being
present and uncommented, and (b) `transform/fix_tool_agent_conflict` being
listed in the `traces` pipeline's `processors` array — losing either one
silently reintroduces the bug with no error from the collector.

### 8.2 `traceloop-sdk`'s metrics gRPC exporter never sets `insecure=True`

Confirmed by reading the installed `traceloop-sdk` 0.62.3 source directly:

* `traceloop/sdk/tracing/tracing.py`'s `init_spans_exporter()` correctly
  parses the `api_endpoint` scheme and passes `insecure=True`/`False`
  accordingly for gRPC.
* `traceloop/sdk/metrics/metrics.py`'s `init_metrics_exporter()` does not
  — it only checks whether `"http"` appears in the endpoint string to pick
  HTTP vs. gRPC, and constructs the gRPC exporter with no `insecure` kwarg
  at all, which defaults to a secure/TLS channel.

Pointing `api_endpoint` at a bare `grpc://localhost:4317` therefore works
fine for traces but makes metrics export fail with repeated
`SSL_ERROR_SSL: ... WRONG_VERSION_NUMBER` handshake errors against the
collector's plaintext gRPC port — this is a real, reproducible bug in the
installed release, not a config mistake.

**Fix used in this app**: `run_openllmetry.py` points `api_endpoint` at
`http://localhost:4318` (the collector's OTLP/HTTP receiver) instead of a
gRPC endpoint. The `"http"` substring check in both `tracing.py` and
`metrics.py` correctly selects the HTTP exporter for both signals, which
has no such insecure-flag bug — traces and metrics both export cleanly.

An alternative fix, if a gRPC endpoint is required for some other reason,
is passing a pre-built exporter to bypass the buggy code path entirely:

```python
from opentelemetry.exporter.otlp.proto.grpc.metric_exporter import OTLPMetricExporter

Traceloop.init(
    ...,
    api_endpoint="grpc://localhost:4317",
    metrics_exporter=OTLPMetricExporter(endpoint="localhost:4317", insecure=True),
)
```

Disabling metrics entirely (`TRACELOOP_METRICS_ENABLED=false`) also works
around the bug, but was deliberately **not** the chosen fix for this app —
metrics stay enabled.

### 8.3 Resource attributes are out of scope

`gen_ai_normalizer` only rewrites **span** attributes. Resource, scope,
span-event, and span-link attributes are never touched — `service.name`
and similar env-level metadata must be set by a separate processor
(`resource`, in this config) or at the SDK level.

### 8.4 Image pin is load-bearing

Moving `docker-compose.yml`'s collector image tag back to `:latest` risks
silently regressing to a build that predates `gen_ai_normalizer`'s release
(`v0.159.0`), crash-looping the container. See "OpenTelemetry Collector
Configuration" above for the verification command to run before changing
the tag.

### 8.5 Most of the `openllmetry` mapping table is currently a no-op for this app

Worth calling out explicitly: the built-in `openllmetry` source's mapping
table is written against the *older* OpenLLMetry attribute shape
(`llm.request.model`, `llm.usage.prompt_tokens`, `traceloop.span.kind`,
...). The installed `opentelemetry-instrumentation-langchain` 0.62.3
already emits `gen_ai.*`-named attributes directly for most of these —
the normalizer's job for this app's traces is almost entirely done by the
instrumentor itself. The one row that still actively matters is
`traceloop.entity.name → gen_ai.agent.name`, which is also the source of
the Section 8.1 bug. Don't assume the mapping table is dead weight for a
different app/instrumentor-version combination, though — this observation
is specific to the currently pinned `traceloop-sdk` release.

---

## 9. Debugging Tips

```bash
# Stream all collector logs
docker logs langchain-openllmetry-otel-collector -f

# Check gen_ai.* attributes are flowing (post-normalization)
docker logs langchain-openllmetry-otel-collector 2>&1 | grep "gen_ai\."

# List every distinct gen_ai.tool.name / gen_ai.agent.name value seen
docker logs langchain-openllmetry-otel-collector 2>&1 | grep -oE "gen_ai.tool.name: Str\([a-z_]*\)" | sort -u
docker logs langchain-openllmetry-otel-collector 2>&1 | grep -oE "gen_ai.agent.name: Str\([A-Za-z]*\)" | sort -u
# Expect exactly: get_weather, get_places, ask_weather_agent, ask_places_agent (tools)
#            and: TripPlannerAgent, WeatherAgent, PlacesAgent (agents) — nothing else

# Check collector startup succeeded (no crash-loop)
docker ps --filter name=langchain-openllmetry-otel-collector --format '{{.Status}}'
# "Up Nm" is healthy; "Restarting" means check `docker logs` immediately

# Verify the pulled image actually contains gen_ai_normalizer
docker run --rm otel/opentelemetry-collector-contrib:0.159.0 components | grep -A2 gen_ai_normalizer
```

### Common issues

| Issue | Solution |
|---|---|
| Collector container restarting in a loop | `docker logs` for `unknown type: "gen_ai_normalizer"` — image predates `v0.159.0`; check the pinned tag |
| `Connection refused` from the Python script | Collector isn't listening where `OTEL_EXPORTER_OTLP_ENDPOINT` (or its `http://localhost:4318` default) points; check `docker ps` port mappings |
| TLS handshake errors exporting metrics | Use the HTTP endpoint (`http://localhost:4318`), not a bare `grpc://` one — see Section 8.2 |
| `gen_ai.agent.name` on `model`/`tools` spans, or colliding with `gen_ai.tool.name` | `transform/fix_tool_agent_conflict` missing or not wired into the traces pipeline — see Section 8.1 |
| Spans present locally, nothing in New Relic UI | Confirm the exporter endpoint matches your license key's environment (production/EU/staging — see `README.md`); confirm the New Relic account/UI filter matches `SERVICE_NAME` |

---

## 10. Resources

* [LangChain Documentation](https://python.langchain.com/)
* [OpenTelemetry Python SDK](https://opentelemetry.io/docs/instrumentation/python/)
* [OTel GenAI Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/gen-ai/)
* [`genainormalizerprocessor` source](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/processor/genainormalizerprocessor)
* [OpenLLMetry (Traceloop)](https://github.com/traceloop/openllmetry)
* [New Relic AI Monitoring](https://docs.newrelic.com/docs/ai-monitoring/)
