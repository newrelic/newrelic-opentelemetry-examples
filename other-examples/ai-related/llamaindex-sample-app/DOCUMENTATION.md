# LlamaIndex + OpenTelemetry + New Relic - AI Agent Observability

**Python version:** 3.9
**OpenTelemetry-SDK version:** 1.40.0
**LlamaIndex version:** 0.10.68
**OpenInference version:** 2.2.4

## 1. Technology Stack Overview

### 1.1 What is LlamaIndex?

LlamaIndex is a data framework for LLM applications that provides:

- **Agents**: Agentic workflows that use LLMs to reason and decide which tools to call
- **Tools**: Function tools that agents can invoke to perform specific tasks (calculations, API calls, data retrieval, etc.)

### 1.2 High-Level Architecture

We are using OpenInference LlamaIndex instrumentation for comprehensive observability.

This application brings together multiple technologies in a cohesive observability pipeline:

```
┌─────────────────────────────────────────────────────────────┐
│                    USER REQUEST                             │
│              "What is 20 times 5 divided by 4?"             │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│              FastAPI Application (llamaindex_app.py)              │
│  • Receives HTTP request                                    │
│  • Routes to /chat or /chat/react endpoint                  │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│     LlamaIndex FunctionCallingAgent or ReActAgent           │
│  • Step 1: LLM decides to call tools                        │
│  • Step 2: Executes multiply(20, 5) → 100                   │
│  • Step 3: Executes divide(100, 4) → 25                     │
│  • Step 4: LLM generates final response                     │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│      LlamaIndexInstrumentor (OpenInference 2.2.4)           │
│  • Auto-captures agent workflow steps                       │
│  • Creates spans for CHAIN, LLM, and TOOL operations        │
│  • Adds OpenInference semantic convention attributes        │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│      OpenTelemetry Collector (Transformation)               │
│  • Receives OTLP traces on port 4318 (HTTP)                 │
│  • Transforms OpenInference → OTel GenAI conventions        │
│    - llm.model_name → gen_ai.request.model                  │
│    - llm.token_count.* → gen_ai.usage.*_tokens              │
│    - tool.name → gen_ai.tool.name                           │
│    - span name → gen_ai.agent.name (for CHAIN spans)        │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│                     New Relic                               │
│  • AI Monitoring Dashboard                                  │
│  • LLM Token Usage & Cost Tracking                          │
│  • Tool Call Analytics                                      │
│  • Distributed Tracing                                      │
└─────────────────────────────────────────────────────────────┘
```

### 1.3 Why This Architecture?

**Separation of Concerns:**

- **LlamaIndex**: Agent workflow logic and tool orchestration
- **LlamaIndexInstrumentor**: Auto-instrumentation with OpenInference semantic conventions
- **OpenTelemetry**: Standard protocol and transformation layer
- **OTel Collector**: Vendor-agnostic routing and attribute transformation to observability backends

## 2. Architecture Overview

### Data Flow Pipeline

1. **Application (Instrumentation)**: The application uses LlamaIndex agents with `LlamaIndexInstrumentor()` which automatically traces:
   - Agent workflow execution (CHAIN spans)
   - LLM calls (LLM spans with OpenInference attributes)
   - Tool invocations (TOOL spans with function calls and results)

2. **OTel SDK (Transmission)**: The SDK exports OTLP data with OpenInference semantic conventions to a local OpenTelemetry Collector via HTTP (port 4318) using `SimpleSpanProcessor` for immediate export.

3. **OTel Collector (Transformation & Routing)**: The collector:
   - Receives traces on OTLP HTTP endpoint (port 4318)
   - Transforms OpenInference attributes to OTel GenAI semantic conventions via the `transform/genai` processor
   - Exports to New Relic's staging OTLP endpoint and logs spans locally via the `debug` exporter

4. **Observability Platform**: New Relic ingests and visualizes the telemetry data in AI Monitoring dashboards.

## 3. Application Configuration (llamaindex_app.py)

### Critical Initialization Order

⚠️ Initialize OpenTelemetry `TracerProvider` BEFORE importing and instrumenting LlamaIndex modules. This ensures all LlamaIndex telemetry is captured by the configured provider.

```python
# 1. Load environment variables
_ = load_dotenv(find_dotenv(), override=True)

# 2. Initialize OTel TracerProvider FIRST
prov = TracerProvider()
exporter = OTLPSpanExporter(endpoint="http://localhost:4318/v1/traces")
prov.add_span_processor(SimpleSpanProcessor(exporter))
trace.set_tracer_provider(prov)

# 3. Auto-instrument LlamaIndex - MUST be called before importing LlamaIndex
LlamaIndexInstrumentor().instrument(tracer_provider=prov)

# 4. NOW import LlamaIndex components
from llama_index.core.agent import AgentRunner, FunctionCallingAgentWorker, ReActAgent
from llama_index.core.tools import FunctionTool
from llama_index.llms.openai import OpenAI
```

### Custom OpenAI Subclass for finish_reason

The `OpenAI` LLM is subclassed to capture `finish_reason` from the raw API response and emit it as `gen_ai.response.finish_reasons` on the current span:

```python
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

### Wrapper Spans for gen_ai.input/output.messages

Each endpoint creates a parent span using `tracer.start_as_current_span()` to attach `gen_ai.input.messages` and `gen_ai.output.messages` in New Relic's expected JSON array format:

```python
tracer = trace.get_tracer(__name__)
with tracer.start_as_current_span("chat") as span:
    result = await function_agent.achat(request.prompt)
    span.set_attribute("gen_ai.input.messages",
        json.dumps([{"role": "user", "content": request.prompt}]))
    span.set_attribute("gen_ai.output.messages",
        json.dumps([{"role": "assistant", "content": result.response}]))
```

### Required Environment Variables

| Variable | Purpose | Example Value |
|----------|---------|---------------|
| `OPENAI_API_KEY` | OpenAI API authentication key | `sk-your-openai-api-key` |
| `NEW_RELIC_LICENSE_KEY` | New Relic license key (used by collector) | `your-newrelic-license-key` |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | OTel Collector HTTP endpoint | `http://localhost:4318` |
| `OTEL_SERVICE_NAME` | Service name in traces | `llamaindex-sample-app` |
| `LLM_MODEL` | LLM model name (optional) | `gpt-4` (default) |

## 4. OpenTelemetry Collector Configuration (otel-collector-llamaindex.yaml)

The OTel Collector receives OTLP traces with OpenInference semantic conventions, transforms them to OTel GenAI semantic conventions, and routes them to New Relic.

### Key Components

#### 1. Receivers

Listens for OTLP data on:
- Port `4317` (gRPC)
- Port `4318` (HTTP) — used by the application

#### 2. Processors (Transformation Pipeline)

**a. memory_limiter**: Prevents OOM — runs first in the pipeline

**b. batch**: Groups spans for efficient export (timeout: 10s, max batch: 2048)

**c. resource**: Inserts `collector.name: otel-collector-llamaindex` on all spans, and sets `aiEnabledApp: "true"` which is **required to surface the app in New Relic's AI Monitoring entity list** for OTel-instrumented apps:

```yaml
resource:
  attributes:
    - key: collector.name
      value: llamaindex-otel-collector
      action: insert
    # Required to enable New Relic AI Monitoring for OTel-instrumented apps
    - key: aiEnabledApp
      value: "true"
      action: insert
```

> Apps using the New Relic agent receive this tag automatically. OTel-instrumented apps must set it explicitly — without it, spans arrive in New Relic but the app will not appear in the AI Monitoring section.

**d. transform/genai**: Maps OpenInference attributes to OTel GenAI semantic conventions:

- `llm.model_name` → `gen_ai.request.model`
- `llm.token_count.prompt` → `gen_ai.usage.input_tokens`
- `llm.token_count.completion` → `gen_ai.usage.output_tokens`
- `tool.name` → `gen_ai.tool.name`
- Sets `gen_ai.system: openai` on all spans with `llm.model_name`
- Sets `gen_ai.agent.name: FunctionCallingAgent` where span name is `chat`
- Sets `gen_ai.agent.name: ReActAgent` where span name is `chat_react`

#### 3. Exporters

- **debug** (`verbosity: detailed`): Logs full span details to collector stdout for local troubleshooting
- **otlphttp/newrelic**: Sends to New Relic's staging OTLP endpoint with gzip compression, retries, and license key authentication

#### 4. Pipeline

```yaml
service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [memory_limiter, batch, resource, transform/genai]
      exporters: [debug, otlphttp/newrelic]
```

## 5. OpenTelemetry Semantic Conventions and Attribute Mapping

### Why Attribute Mapping Matters

- **Platform Compatibility**: New Relic's AI Monitoring expects standard OTel GenAI attribute names (e.g., `gen_ai.system`) to populate LLM-specific dashboards
- **Data Transformation**: OpenInference uses its own naming convention (`llm.*`, `tool.*`) which must be mapped to the standard `gen_ai.*` namespace

### Attribute Mapping Reference

| Category | OpenInference Attribute | OTel GenAI Attribute | Where Applied |
|----------|------------------------|----------------------|---------------|
| **Provider** | *(hardcoded)* | `gen_ai.system: openai` | transform/genai processor |
| **Model Name** | `llm.model_name` | `gen_ai.request.model` | transform/genai processor |
| **Input Tokens** | `llm.token_count.prompt` | `gen_ai.usage.input_tokens` | transform/genai processor |
| **Output Tokens** | `llm.token_count.completion` | `gen_ai.usage.output_tokens` | transform/genai processor |
| **Tool Name** | `tool.name` | `gen_ai.tool.name` | transform/genai processor |
| **Agent Name** | span name (`chat`/`chat_react`) | `gen_ai.agent.name` | transform/genai processor |
| **Input Messages** | *(user prompt)* | `gen_ai.input.messages` | Set in app (JSON array) |
| **Output Messages** | *(agent response)* | `gen_ai.output.messages` | Set in app (JSON array) |
| **Finish Reasons** | `response.raw.choices[0].finish_reason` | `gen_ai.response.finish_reasons` | Set in app via OpenAI subclass |
| **Span Kind** | `openinference.span.kind` | *(retained as-is)* | OpenInference auto |
| **Service Name** | `service.name` | `service.name` | Resource (from env) |
| **Collector Tag** | *(added by collector)* | `collector.name` | resource processor |

## 6. Agent Trace Structure

### LlamaIndex Agent Spans Overview

LlamaIndex automatically creates spans for each internal workflow step. Here are the spans emitted per request:

**Core Agent Workflow Spans (openinference.span.kind: CHAIN):**

| Span Name | Description |
|-----------|-------------|
| `AgentRunner.achat` | Top-level agent async chat entry point |
| `AgentRunner._achat` | Internal async chat orchestration |
| `AgentRunner.create_task` | Creates the agent task from user input |
| `AgentRunner._arun_step` | Orchestrates each reasoning loop iteration |
| `AgentRunner.get_task` | Retrieves current task state |
| `AgentRunner.finalize_response` | Formats and returns the final response |
| `FunctionCallingAgentWorker.initialize_step` | Initializes the first agent step |
| `FunctionCallingAgentWorker.arun_step` | Executes a single agent reasoning step |
| `FunctionCallingAgentWorker.finalize_task` | Cleans up after task completion |

**LLM Spans (openinference.span.kind: LLM):**

| Span Name | Description |
|-----------|-------------|
| `OpenAI.achat` | Actual OpenAI API call — carries token counts, model name, finish reason |
| `OpenAI._prepare_chat_with_tools` | Formats messages and tool definitions |

**Tool Spans (openinference.span.kind: TOOL):**

| Span Name | Description |
|-----------|-------------|
| `FunctionTool.acall` | Individual tool execution (add, subtract, multiply, divide) |

**Wrapper Spans (created by llamaindex_app.py):**

| Span Name | Agent | Attributes Set |
|-----------|-------|----------------|
| `chat` | FunctionCallingAgent | `gen_ai.input.messages`, `gen_ai.output.messages` |
| `chat_react` | ReActAgent | `gen_ai.input.messages`, `gen_ai.output.messages` |

### Example: "What is 6 times 7?"

```
Root: chat (wrapper span)
│   gen_ai.input.messages: [{"role":"user","content":"What is 6 times 7?"}]
│   gen_ai.output.messages: [{"role":"assistant","content":"6 times 7 equals 42."}]
│   gen_ai.agent.name: FunctionCallingAgent   ← set by transform/genai processor
│
└── AgentRunner.achat (CHAIN)
    └── AgentRunner._achat (CHAIN)
        ├── AgentRunner.create_task (CHAIN)
        │
        ├── AgentRunner._arun_step (CHAIN) — Step 1: decide to call tool
        │   ├── FunctionCallingAgentWorker.initialize_step (CHAIN)
        │   └── FunctionCallingAgentWorker.arun_step (CHAIN)
        │       ├── OpenAI._prepare_chat_with_tools (LLM)
        │       └── OpenAI.achat (LLM)                    ← First LLM call
        │           gen_ai.request.model: gpt-4
        │           gen_ai.system: openai
        │           gen_ai.usage.input_tokens: 294
        │           gen_ai.usage.output_tokens: 17
        │           gen_ai.response.finish_reasons: ["tool_calls"]
        │           llm.output_messages → tool_call: multiply_numbers(6, 7)
        │
        ├── FunctionTool.acall (TOOL)                     ← Tool execution
        │   gen_ai.tool.name: multiply_numbers
        │   input: {"a": 6, "b": 7}
        │   output: 42
        │
        ├── AgentRunner._arun_step (CHAIN) — Step 2: generate final answer
        │   └── FunctionCallingAgentWorker.arun_step (CHAIN)
        │       ├── OpenAI._prepare_chat_with_tools (LLM)
        │       └── OpenAI.achat (LLM)                    ← Second LLM call
        │           gen_ai.request.model: gpt-4
        │           gen_ai.system: openai
        │           gen_ai.usage.input_tokens: 325
        │           gen_ai.usage.output_tokens: 9
        │           gen_ai.response.finish_reasons: ["stop"]
        │
        ├── AgentRunner.get_task (CHAIN)
        ├── FunctionCallingAgentWorker.finalize_task (CHAIN)
        └── AgentRunner.finalize_response (CHAIN)
```

**Key Observations:**
- ~16 total spans per request representing the complete agent workflow
- Token usage tracked per LLM call: first call (tool selection) + second call (final answer)
- `finish_reason: tool_calls` on first LLM call, `finish_reason: stop` on final LLM call
- All `gen_ai.*` attributes applied by the collector's `transform/genai` processor

## 7. Before and After Transformation Comparison

### 1. LLM Span (OpenAI.achat)

**BEFORE Transformation (OpenInference attributes only):**

```
Span: OpenAI.achat
    openinference.span.kind: LLM
    llm.model_name: gpt-4
    llm.token_count.prompt: 294
    llm.token_count.completion: 17
    llm.input_messages.0.message.role: system
    llm.input_messages.0.message.content: You are a helpful math tutor...
    llm.input_messages.1.message.role: user
    llm.input_messages.1.message.content: What is 6 times 7?
    llm.output_messages.0.message.role: assistant
    llm.output_messages.0.message.tool_calls.0.tool_call.function.name: multiply_numbers
    llm.output_messages.0.message.tool_calls.0.tool_call.function.arguments: {"a":6,"b":7}
    llm.invocation_parameters: {"model":"gpt-4","temperature":0}
```

**AFTER Transformation (OTel GenAI attributes added by collector):**

```
Span: OpenAI.achat
    openinference.span.kind: LLM
    llm.model_name: gpt-4
    llm.token_count.prompt: 294
    llm.token_count.completion: 17
    [... original llm.* attributes retained ...]
✅ Added by transform/genai processor:
    gen_ai.request.model: gpt-4
    gen_ai.system: openai
    gen_ai.usage.input_tokens: 294
    gen_ai.usage.output_tokens: 17
✅ Added by app (OpenAI subclass):
    gen_ai.response.finish_reasons: ["tool_calls"]
✅ Added by resource processor:
    collector.name: llamaindex-otel-collector
```

### 2. Tool Span (FunctionTool.acall)

**BEFORE Transformation:**

```
Span: FunctionTool.acall
    openinference.span.kind: TOOL
    tool.name: multiply_numbers
    tool.parameters: {"a": int, "b": int}
    input.value: {"kwargs": {"a": 6, "b": 7}}
    output.value: 42
```

**AFTER Transformation:**

```
Span: FunctionTool.acall
    openinference.span.kind: TOOL
    tool.name: multiply_numbers
    input.value: {"kwargs": {"a": 6, "b": 7}}
    output.value: 42
✅ Added by transform/genai processor:
    gen_ai.tool.name: multiply_numbers
```

### 3. Wrapper Span (chat endpoint)

**BEFORE Transformation:**

```
Span: chat
    gen_ai.input.messages: [{"role":"user","content":"What is 6 times 7?"}]
    gen_ai.output.messages: [{"role":"assistant","content":"6 times 7 equals 42."}]
```

**AFTER Transformation:**

```
Span: chat
    gen_ai.input.messages: [{"role":"user","content":"What is 6 times 7?"}]
    gen_ai.output.messages: [{"role":"assistant","content":"6 times 7 equals 42."}]
✅ Added by transform/genai processor:
    gen_ai.agent.name: FunctionCallingAgent
✅ Added by resource processor:
    collector.name: llamaindex-otel-collector
```

### Transformation Summary

The `transform/genai` processor applies the following OTTL statements:

```yaml
# Model name
set(attributes["gen_ai.request.model"], attributes["llm.model_name"])
    where attributes["llm.model_name"] != nil

# Token counts
set(attributes["gen_ai.usage.input_tokens"], attributes["llm.token_count.prompt"])
    where attributes["llm.token_count.prompt"] != nil
set(attributes["gen_ai.usage.output_tokens"], attributes["llm.token_count.completion"])
    where attributes["llm.token_count.completion"] != nil

# Tool name
set(attributes["gen_ai.tool.name"], attributes["tool.name"])
    where attributes["tool.name"] != nil

# Provider (required by New Relic AI Monitoring)
set(attributes["gen_ai.system"], "openai")
    where attributes["llm.model_name"] != nil

# Agent name from endpoint span name
set(attributes["gen_ai.agent.name"], "FunctionCallingAgent") where name == "chat"
set(attributes["gen_ai.agent.name"], "ReActAgent") where name == "chat_react"
```

## 8. Known Limitations

### 8.1 Message History Not Captured

**Limitation**: `gen_ai.input.messages` only captures the current user prompt, not the full conversation history.

**Details**: The wrapper span sets `gen_ai.input.messages` from `request.prompt` directly. The full conversation history (all prior turns) is available in `llm.input_messages.*` attributes on the `OpenAI.achat` span but is not consolidated into the New Relic format.

### 8.2 OpenInference 2.x Required for LlamaIndex 0.10.x

**Limitation**: `openinference-instrumentation-llama-index>=3.x` is incompatible with `llama-index-core 0.10.x`.

**Details**: Version 3.x emits span lifecycle events but finds no open spans, producing silent failures with `"Open span is missing"` warnings. Version 2.2.4 is required for `llama-index-core 0.10.x`.

### 8.3 finish_reason Not Captured by LlamaIndex Instrumentation — Manual Mapping Required

**Limitation**: LlamaIndex's OpenInference instrumentation does not capture `gen_ai.response.finish_reasons`. Manual mapping in the application code is required.

**Details**: The OpenInference spans emitted by LlamaIndex do not include a `finish_reason` attribute. To populate `gen_ai.response.finish_reasons`, the application subclasses the `OpenAI` LLM and manually reads `response.raw.choices[0].finish_reason` after each `achat()` call, then sets it on the current span via `trace.get_current_span().set_attribute(...)`. This mapping is defined in the app file (`llamaindex_app.py`).

**Impact**: Without the custom `OpenAI` subclass in the app file, `gen_ai.response.finish_reasons` will be absent from all spans. For multi-step agent requests (tool use followed by a final answer), each `OpenAI.achat` span captures its own `finish_reason` independently (`tool_calls` on the first call, `stop` on the final call). The wrapper `chat` span does not receive `finish_reason` — consolidating it there would require additional context propagation in the app code.

By contrast, the following attributes are available without manual mapping and are sourced from the config file :

**From the collector config (transform/genai processor) — no app changes needed:**
- **Agent name** — derived from the span name (`chat` / `chat_react`); mapped to `gen_ai.agent.name`
- **Model name** — from `llm.model_name`; mapped to `gen_ai.request.model`
- **Tool name** — from `tool.name`; mapped to `gen_ai.tool.name`
- **Token counts** — from `llm.token_count.prompt` / `llm.token_count.completion`; mapped to `gen_ai.usage.input_tokens` / `gen_ai.usage.output_tokens`
- **Provider name** — hardcoded as `gen_ai.system: openai`

**From the request/response — manual mapping required due to data format mismatch:**
- **Input messages** (`gen_ai.input.messages`) and **output messages** (`gen_ai.output.messages`) also require manual mapping in the app file (`llamaindex_app.py`). LlamaIndex's OpenInference instrumentation emits message data as flat indexed attributes (e.g., `llm.input_messages.0.content`, `llm.input_messages.0.role`), which do not match the JSON array format New Relic expects for `gen_ai.input.messages` / `gen_ai.output.messages`. The app works around this by wrapping each endpoint call in a custom span and manually setting these attributes as a JSON array: `[{"role": "user", "content": "..."}]`.


### 8.4 OTEL_EXPORTER_OTLP_ENDPOINT Shell Override

**Limitation**: The shell environment may have `OTEL_EXPORTER_OTLP_ENDPOINT` set to an external endpoint (e.g., New Relic directly), which overrides the `.env` file value.

**Solution**: Always start the app with the endpoint explicitly set:
```bash
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318 uvicorn llamaindex_app:app --port 8080
```

## 9. Resources

- [LlamaIndex Documentation](https://docs.llamaindex.ai/)
- [OpenTelemetry Documentation](https://opentelemetry.io/docs/)
- [OpenInference Semantic Conventions](https://github.com/Arize-ai/openinference)
- [OTel GenAI Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/gen-ai/)
- [New Relic AI Monitoring](https://docs.newrelic.com/docs/ai-monitoring/)
- [OTel Collector Transform Processor](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/processor/transformprocessor)
