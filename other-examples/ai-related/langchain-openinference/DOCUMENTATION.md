# LangChain + OTel GenAI Normalizer — Architecture 

Sample-app repo: `git@source.datanerd.us:ai-o11y/langchain-otel-genai-sample-app.git`

**Python version:** 3.12
**OpenTelemetry SDK version:** 1.38.0+
**LangChain version:** 0.3.0+
**Instrumentation:** `openinference-instrumentation-langchain` (`LangChainInstrumentor`)
**Normalization:** `genainormalizerprocessor` (config key `gen_ai_normalizer`), collector `v0.159.0`+

---

## 1. Technology Stack Overview

### 1.1 What this sample demonstrates

A multi-agent LangChain application whose spans are normalized into
standard `gen_ai.*` OTel semantic conventions **entirely by the
collector**, driven by declarative config — no `gen_ai.*` attribute is
ever set by hand in application code. The agent/tool graph (see Agent
Trace Structure below) is deliberately shaped to exercise agent→tool,
tool→tool, agent→agent, and tool→agent span relationships, so the
normalization has to handle more
than a single flat tool call.

### 1.2 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     CLI INVOCATION                          │
│   python app/run_openinference.py "Plan a trip to Berlin —  │
│   tell me the weather and the best places to visit"         │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│         Entry script (run_openinference.py)                 │
│  • Loads .env, builds TracerProvider + OTLPSpanExporter      │
│  • Stamps resource attr instrumentation.source=              │
│    "openinference"                                            │
│  • Instruments LangChain's callback system                  │
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
│   OpenInference LangChainInstrumentor                       │
│   (patches LangChain's callback system)                     │
│     - llm.model_name, llm.token_count.*                     │
│     - llm.input_messages.N.message.*, tool.name              │
│     - openinference.span.kind: LLM / TOOL / CHAIN / AGENT   │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│    OpenTelemetry SDK (Transmission via gRPC)                │
│  • BatchSpanProcessor + OTLPSpanExporter (proto/grpc)        │
│  • Sends to localhost:4317                                   │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│   OpenTelemetry Collector (Docker: langchain-genai-          │
│   otel-collector, image pinned to v0.159.0+)                │
│  • Receives OTLP on 4317 (gRPC) / 4318 (HTTP)                │
│  • memory_limiter, resource (service.name,                   │
│    deployment.environment upsert)                            │
│  • gen_ai_normalizer (source: openinference,                 │
│    remove_originals: true) — renames vendor attrs → gen_ai.* │
│    and reconstructs gen_ai.input.messages /                  │
│    gen_ai.output.messages from flattened indexed attrs        │
│  • transform/backfill_missing (OTTL) — backfills              │
│    gen_ai.agent.name and a few gen_ai.response.* fields       │
│    gen_ai_normalizer doesn't cover                            │
│  • batch                                                      │
│  • Exports traces + metrics + logs to New Relic (+ debug     │
│    console exporter for local inspection)                    │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│                     New Relic Platform                      │
│  • AI Monitoring Dashboard                                   │
│  • LLM Token Usage & Cost Tracking                            │
│  • Tool Call Analytics                                        │
│  • Distributed Tracing with Waterfall View                    │
│  • Query by gen_ai.tool.name, gen_ai.operation.name, etc.     │
└─────────────────────────────────────────────────────────────┘
```

### 1.3 Why this architecture?

* **LangChain** (`create_agent`): agent workflow logic and tool
  orchestration, kept 100% free of observability code.
* **`gen_ai_normalizer` for the bulk of the mapping**: the mapping table
  (source attribute → `gen_ai.*` target, including message reconstruction
  and operation-name value-folding) ships built into the processor for
  the `openinference` source. A small `transform/backfill_missing` OTTL
  processor fills the gaps the built-in table doesn't cover — see "Known
  Limitations" below for why `gen_ai.agent.name` needs this backfill.
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
2. **Entry script** (`app/run_openinference.py`):
   * Builds the `TracerProvider`/exporter **before** importing `agent` —
     instrumentation must patch LangChain's callback system before the
     agent module is imported, or spans are missed
   * Calls `LangChainInstrumentor().instrument()`
   * Prints the query and result, then flushes the span processor
3. **OTel SDK**: `BatchSpanProcessor` + `OTLPSpanExporter` (gRPC) →
   `localhost:4317`
4. **OTel Collector** (`otel-collector-config.yaml`):
   * Receives on 4317 (gRPC) and 4318 (HTTP)
   * `gen_ai_normalizer` renames source attributes into `gen_ai.*` per the
     built-in `openinference` mapping table, removing the originals
   * `transform/backfill_missing` fills in `gen_ai.agent.name` and a few
     `gen_ai.response.*` fields the built-in table doesn't cover (see
     "Known Limitations" below)
   * Exports traces, metrics, and logs to the configured New Relic endpoint
     (production, EU, or staging — see `README.md`)
5. **New Relic**: ingests and visualizes telemetry

---

## 3. Application Configuration

### Initialization order

> ⚠️ The `TracerProvider` must be set up and
> `LangChainInstrumentor().instrument()` must run **before** `agent` is
> imported. The entry script enforces this with a `# noqa: E402` import of
> `agent` placed after the instrumentation setup — moving that import
> earlier silently breaks span capture with no error.

```python
# run_openinference.py
from dotenv import load_dotenv
from openinference.instrumentation.langchain import LangChainInstrumentor
from opentelemetry import trace
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor

load_dotenv()

resource = Resource.create({
    "service.name": os.getenv("SERVICE_NAME", "langchain-openinference-demo"),
    "instrumentation.source": "openinference",
})
provider = TracerProvider(resource=resource)
exporter = OTLPSpanExporter(
    endpoint=os.getenv("OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4317"),
    insecure=True,
)
provider.add_span_processor(BatchSpanProcessor(exporter))
trace.set_tracer_provider(provider)

LangChainInstrumentor().instrument()

from agent import run  # noqa: E402  (import after instrumentation is wired up)
```

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

`LangChainInstrumentor` automatically captures, for each tool call:
`tool.name`, `tool.description`, `tool_call.function.arguments`,
`tool_call.id`.

### Required environment variables

| Variable | Purpose | Example value |
|---|---|---|
| `OPENAI_API_KEY` | OpenAI API authentication | `sk-...` |
| `NEW_RELIC_LICENSE_KEY` | New Relic ingest key (used by collector) | `...NRAL` |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | OTel Collector gRPC endpoint | `http://localhost:4317` |
| `SERVICE_NAME` | Resource `service.name` for telemetry | `langchain-genai-demo` |
| `LLM_MODEL` | (Optional) override LLM model | `gpt-4o` |

`OTEL_EXPORTER_OTLP_ENDPOINT` defaults to `http://localhost:4317` in the
entry script if unset — if you're also running another OTel Collector
locally, confirm this one is actually listening on that port before
assuming an empty New Relic UI means a New Relic-side problem.

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
    container_name: langchain-genai-otel-collector
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
      - langchain-genai-network

networks:
  langchain-genai-network:
    driver: bridge
```

**The image tag is load-bearing.** `gen_ai_normalizer` is `alpha` stability
and only appears in the upstream release manifest starting at `v0.159.0`.
Pulling `:latest` before that processor's release date produces a collector
that crash-loops on startup with:

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
        value: langchain-genai-demo
        action: upsert
      - key: deployment.environment
        value: development
        action: upsert

  gen_ai_normalizer:
    sources:
      - name: openinference
        remove_originals: true

  batch:
    timeout: 10s
    send_batch_size: 1024
```

**Why no per-source conditions to author?** Unlike a hand-written
`transform`/OTTL processor that needs explicit `where` clauses,
`gen_ai_normalizer`'s built-in `openinference` mapping table already
encodes which source attribute belongs on which span type — including
inferring message `role` from context (`tool_call_id` present → `tool`;
`tool_calls` present → `assistant`; otherwise → `user`) and reconstructing
the flattened `llm.input_messages.N.message.*` shape into a single
`gen_ai.input.messages` JSON array. `resource` (service.name /
deployment.environment) still runs as its own processor since that's
env-level metadata, not something the normalizer touches — its
documented scope is span attributes only, never resource attributes.

### Full OpenInference → `gen_ai_normalizer` → New Relic mapping table (verified against live spans)

Every row below was cross-checked against real span dumps from this repo's
`debug` exporter output, not just the processor's documentation. The
"New Relic receives" column reflects what actually leaves this pipeline
after both `gen_ai_normalizer` and `transform/backfill_missing` run — the
attribute New Relic's AI Monitoring UI and NRQL queries see.

| OpenInference emits (raw) | `gen_ai_normalizer` maps to | New Relic receives | Confirmed on |
|---|---|---|---|
| `tool.name` | `gen_ai.tool.name` | `gen_ai.tool.name` | every tool-execution span (`get_weather`, `get_places`, `ask_weather_agent`, `ask_places_agent`) |
| `tool.description` | `gen_ai.tool.description` | `gen_ai.tool.description` | tool-execution spans |
| `tool_call.function.arguments` | `gen_ai.tool.call.arguments` | `gen_ai.tool.call.arguments` | tool-call spans |
| `tool_call.id` | `gen_ai.tool.call.id` | `gen_ai.tool.call.id` | tool-call spans |
| `llm.model_name` | `gen_ai.request.model` | `gen_ai.request.model` | every `ChatOpenAI` span |
| `llm.token_count.prompt` | `gen_ai.usage.input_tokens` | `gen_ai.usage.input_tokens` | every `ChatOpenAI` span |
| `llm.token_count.completion` | `gen_ai.usage.output_tokens` | `gen_ai.usage.output_tokens` | every `ChatOpenAI` span |
| `llm.provider` | `gen_ai.provider.name` | `gen_ai.provider.name` | `ChatOpenAI` spans |
| `llm.input_messages.N.message.*` | `gen_ai.input.messages` (reconstructed JSON) | `gen_ai.input.messages` | `ChatOpenAI` spans |
| `llm.output_messages.N.message.*` | `gen_ai.output.messages` (reconstructed JSON) | `gen_ai.output.messages` | `ChatOpenAI` spans, incl. multi-tool-call responses |
| `agent.name` | **Never fires** — `LangChainInstrumentor` never emits this source attribute for `create_agent()`-built agents (confirmed by inspecting the installed package's `_tracer.py`) | `gen_ai.agent.name` — set instead by `transform/backfill_missing`, on real agent spans only | see "Known Limitations" below |
| `session.id` | `gen_ai.conversation.id` | — | not exercised by this app |
| `embedding.model_name` / `reranker.model_name` | `gen_ai.request.model` | — | not exercised (no embedding/rerank calls) |
| `openinference.span.kind` | `gen_ai.operation.name` (value-mapped: `LLM`→`chat`, `TOOL`→`execute_tool`, `AGENT`/`CHAIN`→`invoke_agent`) | `gen_ai.operation.name` | every span |
| *(not emitted by OpenInference)* | `transform/backfill_missing` reads `llm.finish_reason` directly | `gen_ai.response.finish_reasons` | `ChatOpenAI` spans |
| *(not emitted by OpenInference)* | `transform/backfill_missing` extracts from `output.value.llm_output.id` | `gen_ai.response.id` | `ChatOpenAI` spans |
| *(not emitted by OpenInference)* | `transform/backfill_missing` extracts from `output.value.llm_output.model_name` | `gen_ai.response.model` | `ChatOpenAI` spans |

**Attributes OpenInference emits that have no row in the mapping table at
all** — these pass through completely unrenamed and are still present
after normalization:

| Attribute | Why it's untouched |
|---|---|
| `input.value` / `output.value` | Generic I/O capture, OpenInference's own concept — no `gen_ai.*` equivalent exists in the table |
| `metadata` | Generic passthrough of LangChain's internal run metadata (a JSON-serialized blob) — not a recognized source key, so it's copied verbatim without being inspected. This is where an agent's name (`lc_agent_name`) actually lives — see "Known Limitations" below |
| `llm.finish_reason` | Emitted as a real top-level attribute by the instrumentor, but absent from the built-in table |
| `llm.invocation_parameters`, `llm.tools.*.tool.json_schema`, `llm.system`, `llm.token_count.total`, `llm.token_count.*_details.*` | Not in the built-in table |

### `transform/backfill_missing` (OTTL)

```yaml
transform/backfill_missing:
  error_mode: ignore
  trace_statements:
    - context: span
      statements:
        - 'set(attributes["gen_ai.agent.name"], ParseJSON(attributes["metadata"])["lc_agent_name"]) where attributes["metadata"] != nil and attributes["gen_ai.agent.name"] == nil and ParseJSON(attributes["metadata"])["lc_agent_name"] == name'
        - 'set(attributes["gen_ai.response.finish_reasons"], [attributes["llm.finish_reason"]]) where attributes["llm.finish_reason"] != nil'
        - 'set(attributes["gen_ai.response.id"], ParseJSON(attributes["output.value"])["llm_output"]["id"]) where name == "ChatOpenAI" and attributes["output.value"] != nil'
        - 'set(attributes["gen_ai.response.model"], ParseJSON(attributes["output.value"])["llm_output"]["model_name"]) where name == "ChatOpenAI" and attributes["output.value"] != nil'
```

Runs immediately after `gen_ai_normalizer` in the traces pipeline. See
"Known Limitations" below for why the `gen_ai.agent.name` statement's
condition is shaped the way it is.

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

`debug` (replaces the deprecated `logging` exporter name from older
collector distros) prints normalized spans to `docker logs` for local
verification without needing a New Relic account.

#### Pipelines

```yaml
service:
  extensions: [zpages]
  pipelines:
    traces:
      receivers: [otlp]
      processors: [memory_limiter, resource, gen_ai_normalizer, transform/backfill_missing, batch]
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

`gen_ai_normalizer` and `transform/backfill_missing` only appear in the
`traces` pipeline — they normalize span attributes and have nothing to
act on in the `metrics`/`logs` pipelines.

---

## 5. Semantic Conventions and Attribute Mapping

### Attributes emitted before normalization (`LangChainInstrumentor`)

| Attribute | Description | Example |
|---|---|---|
| `llm.model_name` | Requested/response model | `gpt-4o-2024-08-06` |
| `llm.token_count.prompt` | Input token count | `68` |
| `llm.token_count.completion` | Output token count | `14` |
| `llm.input_messages.0.message.role` | First message role | `system` |
| `tool.name` | Tool invoked | `get_weather` |
| `tool.description` | Tool docstring | `Look up the current weather for a city.` |
| `openinference.span.kind` | Span type | `LLM`, `TOOL`, `CHAIN`, `AGENT` |

### Attributes after `gen_ai_normalizer` alone

Captured from a real run (`python app/run_openinference.py "What's the
weather in London?"`, collector `debug` exporter output, before the
multi-agent rewrite):

```
gen_ai.provider.name: openai
gen_ai.request.model: gpt-4o-2024-08-06
gen_ai.usage.input_tokens: 100
gen_ai.usage.output_tokens: 13
gen_ai.operation.name: chat            # on the ChatOpenAI LLM span
gen_ai.operation.name: execute_tool    # on the get_weather tool span
gen_ai.operation.name: invoke_agent    # on the WeatherAgent root span
gen_ai.tool.name: get_weather
gen_ai.tool.description: Look up the current weather for a city.
gen_ai.input.messages: [{"role":"system","parts":[{"type":"text","content":"You are a helpful assistant. Use the get_weather tool when asked about weather."}]},{"role":"user","parts":[{"type":"text","content":"What's the weather in London?"}]}]
gen_ai.output.messages: [{"role":"assistant","parts":[{"type":"tool_call","id":"call_...","name":"get_weather","arguments":{"city":"London"}}],"finish_reason":""}]
```

No `llm.*` / `tool.*` attributes survive — `remove_originals: true` strips
them after a successful rename. This example shows `gen_ai_normalizer`'s
output alone — it does not include `gen_ai.agent.name`, which
`gen_ai_normalizer` never sets for this instrumentor (see "Known
Limitations" below). `gen_ai.agent.name` is added on top by
`transform/backfill_missing`; see Agent Trace Structure below for a
current multi-agent trace showing both `gen_ai.tool.name` and
`gen_ai.agent.name` together, correctly separated by span.

---

## 6. Agent Trace Structure

`app/agent.py` is a small multi-agent **trip planner**, deliberately shaped
to exercise all four span-relationship patterns this normalizer pipeline
needs to handle:

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

### Span tree for "Plan a trip to Madrid — tell me the weather and the best places to visit"

Verified against a real run's `docker logs langchain-genai-otel-collector`
output (trace `55db606cc8f71f17d3eef44aa32bb34f`). `gen_ai.agent.name` is
present on exactly the three real agent spans; `gen_ai.tool.name` is
present on exactly the four tool spans:

```
Span: TripPlannerAgent  (root, gen_ai.operation.name: invoke_agent)
  │   gen_ai.agent.name: TripPlannerAgent
  │
  ├── Span: ChatOpenAI  (gen_ai.operation.name: chat)
  │     └── gen_ai.output.messages: [tool_call ask_weather_agent, tool_call ask_places_agent]
  │
  ├── Span: ask_weather_agent  (gen_ai.operation.name: execute_tool)
  │     ├── gen_ai.tool.name: ask_weather_agent
  │     └── Span: WeatherAgent  (nested agent → agent / tool → agent)
  │           │   gen_ai.agent.name: WeatherAgent
  │           ├── Span: ChatOpenAI  →  tool_call: get_weather
  │           └── Span: get_weather  (gen_ai.operation.name: execute_tool)
  │                 ├── gen_ai.tool.name: get_weather
  │                 └── output.value: "Madrid: 22C (72F), sunny"   ← Fahrenheit from tool→tool call
  │
  ├── Span: ask_places_agent  (gen_ai.operation.name: execute_tool)
  │     ├── gen_ai.tool.name: ask_places_agent
  │     └── Span: PlacesAgent
  │           │   gen_ai.agent.name: PlacesAgent
  │           ├── Span: ChatOpenAI  →  tool_call: get_places
  │           └── Span: get_places  (gen_ai.operation.name: execute_tool)
  │                 └── gen_ai.tool.name: get_places
  │
  └── Span: ChatOpenAI  (final synthesis, gen_ai.operation.name: chat)
        └── gen_ai.output.messages: [trip summary combining both answers]
```

**Total spans:** ~14 per request across two `ResourceSpans` batches — one
`TripPlannerAgent` root, two sub-agent invocations (each contributing its
own `ChatOpenAI` + tool span), plus LangGraph's internal `model`/`tools`
node wrapper spans.

**Every agent and tool call in this tree is a genuine, model-driven
decision** — not scripted or hardcoded. Confirmed via the trace's own
`gen_ai.output.messages`: `TripPlannerAgent`'s first LLM call decided on
its own to invoke both `ask_weather_agent` and `ask_places_agent`;
`WeatherAgent`'s LLM call independently decided to call `get_weather`;
`PlacesAgent`'s independently decided to call `get_places`. Nothing in
`app/agent.py` forces this sequence — `create_agent` lets the model choose
whether/which tools to call on each turn.

**`_celsius_to_fahrenheit` never appears as its own span** — it's a plain
Python function call inside `get_weather`, not a LangChain-visible
operation, so OpenInference's callback-based instrumentor has nothing to
hook. This is expected: verify the tool→tool effect indirectly, via the
`72F` conversion showing up in `get_weather`'s `output.value` /
`gen_ai.output.messages`, rather than by looking for a fourth span.

### Request / response example

**Invocation:**

```bash
python app/run_openinference.py "Plan a trip to Tokyo — tell me the weather and the best places to visit"
```

**Output:**

```
> Plan a trip to Tokyo — tell me the weather and the best places to visit
Here's a helpful summary for your trip to Tokyo:

**Weather:**
The current weather in Tokyo is 22°C (72°F) and sunny, making it a perfect day for exploring the city.

**Best Places to Visit:**
1. **Old Town Square** - ...
2. **Riverside Promenade** - ...
3. **Central Museum** - ...
```

---

## 7. Running the Application

### Setup

**1. Create virtual environment and install dependencies:**

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

**2. Configure environment variables:**

```bash
cp .env.example .env
# fill in OPENAI_API_KEY and NEW_RELIC_LICENSE_KEY
```

**3. Start the OTel Collector:**

```bash
docker-compose up -d
```

**4. Run a prompt:**

```bash
python app/run_openinference.py "Plan a trip to Berlin — tell me the weather and the best places to visit"
```

Or use `./run.sh` to bring everything up and fire 4 sample trip-planning
prompts automatically.

**5. View traces in New Relic:**

* Navigate to **APM & Services** → your `SERVICE_NAME`
* Open **AI Monitoring** or Distributed Tracing to see the span waterfall
* Query: `SELECT * FROM Span WHERE gen_ai.tool.name = 'get_weather'`

---

## 8. Known Limitations

### 8.1 Multimodal content is not reconstructed (OpenInference source)

OpenInference's indexed content-array format for images, audio, and other
non-text modalities (`llm.{input,output}_messages.N.message.contents.M.*`)
is not reconstructed into `gen_ai.input.messages` /
`gen_ai.output.messages`. Only the flat `message.content` string field is
handled. Multimodal spans pass through with their original flattened
attributes intact rather than being normalized.

### 8.2 `finish_reason` on output messages is always empty (OpenInference source)

OpenInference has no per-message finish reason; the field is required by
the GenAI output-messages schema and is always emitted as `""`. Use the
span-level `gen_ai.response.finish_reasons` attribute instead for the
model's actual stop reason.

### 8.3 Resource attributes are out of scope

`gen_ai_normalizer` only rewrites **span** attributes. Resource, scope,
span-event, and span-link attributes are never touched — `service.name` and
similar env-level metadata must be set by a separate processor (`resource`,
in this config) or at the SDK level.

### 8.4 Image pin is load-bearing

Moving `docker-compose.yml`'s collector image tag back to `:latest` risks
silently regressing to a build that predates `gen_ai_normalizer`'s release
(`v0.159.0`), crash-looping the container. See OpenTelemetry Collector
Configuration above for the verification command to run before changing
the tag.

### 8.5 `gen_ai.agent.name` never fires from OpenInference alone — root cause

Confirmed by reading the installed `openinference-instrumentation-langchain`
package's source directly (`_tracer.py`, both the locally installed
`0.1.72` and the latest published `0.1.73` — this is not fixed by
upgrading):

* `openinference-semantic-conventions` **does** define `AGENT_NAME =
  "agent.name"` as a real, documented attribute — the concept exists in
  the spec.
* `openinference-instrumentation-langchain`'s `_tracer.py` never imports
  or references that constant anywhere. Its local alias block only pulls
  in `TOOL_NAME`, `TOOL_DESCRIPTION`, `LLM_MODEL_NAME`, etc. — `AGENT_NAME`
  is absent. No code path in this instrumentor ever calls
  `span.set_attribute(AGENT_NAME, ...)`, for any span.
* The one piece of "agent" logic that does exist
  (`OpenInferenceSpanKindValues.AGENT if "agent" in run.name.lower()`)
  only classifies the span's *kind*, not its *name*.
* The agent's name (e.g. `"WeatherAgent"`) still ends up in the trace, but
  only because LangGraph attaches it to `run.extra["metadata"]` as
  `lc_agent_name`, and OpenInference's generic `_metadata()` function
  (line ~1452 in `_tracer.py`) dumps that entire dict, unopened, into the
  catch-all `metadata` span attribute — it never recognizes
  `lc_agent_name` as something that maps to the standard `agent.name`.

Because `gen_ai_normalizer`'s `agent.name` → `gen_ai.agent.name` mapping
rule has nothing to consume, `gen_ai.agent.name` is absent from every span
unless something else extracts it. This repo's collector config runs a
`transform/backfill_missing` OTTL processor (see OpenTelemetry Collector
Configuration above) that does exactly that.

**Getting the condition right took three iterations**, summarized below:

1. **Unconditional** (`metadata != nil`): sets `gen_ai.agent.name` on
   *every* span carrying LangGraph metadata — including tool-execution
   spans that also carry `gen_ai.tool.name`. This broke New Relic's AI
   Monitoring table: confirmed via live UI A/B testing that whenever a
   span has both attributes, the table stops showing `AI Tool` for that
   row (New Relic's own internal reference `genainormalizer` processor
   deliberately treats "is a tool span" and "is an agent span" as
   mutually exclusive — see below).
2. **`IsRootSpan()` only**: correctly excludes every tool span, but also
   excludes nested sub-agents (`WeatherAgent`, `PlacesAgent`) since they
   aren't the trace root — only the top-level `TripPlannerAgent` got
   tagged.
3. **Final fix — span name matches its own `lc_agent_name`**:
   `metadata.lc_agent_name` is the name of the *owning* agent, set
   identically on every span inside that agent's graph, including its own
   tool-execution spans (e.g. `get_weather`'s `lc_agent_name` is
   `"WeatherAgent"`, same as the `WeatherAgent` span itself). But an
   agent-level span's own `name` always equals its `lc_agent_name` (the
   agent reporting its own name); a tool span's `name` never does
   (`ask_weather_agent`'s `lc_agent_name` is `"TripPlannerAgent"`, not
   `"ask_weather_agent"`). Matching on this tags every real agent span —
   root and nested — while still excluding every tool-execution span:

```yaml
- 'set(attributes["gen_ai.agent.name"], ParseJSON(attributes["metadata"])["lc_agent_name"]) where attributes["metadata"] != nil and attributes["gen_ai.agent.name"] == nil and ParseJSON(attributes["metadata"])["lc_agent_name"] == name'
```

Confirmed correct, end to end: zero OTTL execution errors; `gen_ai.tool.name`
present on all four tool spans (`get_weather`, `get_places`,
`ask_weather_agent`, `ask_places_agent`); `gen_ai.agent.name` present on
all three agent spans (`TripPlannerAgent`, `WeatherAgent`, `PlacesAgent`)
and no others; and both `AI Tool` and `AI Agent` visible together in New
Relic's AI Monitoring UI for the same trace.

**Root cause, found via New Relic's internal reference implementation**
(Confluence, space `AIO11y`, "Custom-Otel-Collector" — documents a
separate, more capable `genainormalizer` processor than the open-source
`gen_ai_normalizer` this repo uses): that processor's own "LangChain Agent
Name (root span only)" logic sets `gen_ai.agent.name` **only on the root
LangGraph span** — one whose `metadata` does *not* contain
`langgraph_node` — specifically to avoid polluting internal
pipeline/tool nodes. New Relic's AI Monitoring entity-synthesis layer
(documented separately in "OTEL gen-ai Entity Synthesis - Initiative
Design Doc") turns `gen_ai.tool.name` and `gen_ai.agent.name` into
distinct `AI_TOOL` / `AI_AGENT` entities per span — which is almost
certainly why a span carrying both confused that classification.

---

## 9. Debugging Tips

```bash
# Stream all collector logs
docker logs langchain-genai-otel-collector -f

# Check gen_ai.* attributes are flowing (post-normalization)
docker logs langchain-genai-otel-collector 2>&1 | grep "gen_ai\."

# Confirm no un-normalized vendor attributes survived (should be empty)
docker logs langchain-genai-otel-collector 2>&1 | grep -E "llm\."

# Check collector startup succeeded (no crash-loop)
docker ps --filter name=langchain-genai-otel-collector --format '{{.Status}}'
# "Up Nm" is healthy; "Restarting" means check `docker logs` immediately

# Check for real error/warn-level log lines (ignores debug-exporter span dumps,
# which can coincidentally contain words like "fail" inside JSON string values)
docker logs langchain-genai-otel-collector 2>&1 | grep -E '^\s*20[0-9]{2}-[0-9]{2}-[0-9]{2}T.*\t(error|warn)\t'

# Verify the pulled image actually contains gen_ai_normalizer
docker run --rm otel/opentelemetry-collector-contrib:0.159.0 components | grep -A2 gen_ai_normalizer
```

### Common issues

| Issue | Solution |
|---|---|
| Collector container restarting in a loop | `docker logs` for `unknown type: "gen_ai_normalizer"` — image predates `v0.159.0`; check the pinned tag |
| `Connection refused` from the Python script | Collector isn't listening where `OTEL_EXPORTER_OTLP_ENDPOINT` (or its `localhost:4317` default) points; check `docker ps` port mappings, especially if another local collector is also using 4317 |
| No `gen_ai.*` in collector logs | Confirm `LangChainInstrumentor().instrument()` ran **before** `agent` was imported |
| Spans present locally, nothing in New Relic UI | Confirm the exporter endpoint matches your license key's environment (production/EU/staging — see `README.md`); confirm the New Relic account/UI filter matches `SERVICE_NAME` |
| Leftover `llm.*` attributes on normalized spans | Confirm `remove_originals: true` is set on `gen_ai_normalizer` |

---

## 10. Resources

* [LangChain Documentation](https://python.langchain.com/)
* [OpenTelemetry Python SDK](https://opentelemetry.io/docs/instrumentation/python/)
* [OTel GenAI Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/gen-ai/)
* [`genainormalizerprocessor` source](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/processor/genainormalizerprocessor)
* [OpenInference](https://github.com/Arize-ai/openinference)
* [New Relic AI Monitoring](https://docs.newrelic.com/docs/ai-monitoring/)
