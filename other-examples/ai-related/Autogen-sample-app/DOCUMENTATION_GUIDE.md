# AutoGen Core Instrumentation Guide

**Observability for a FastAPI Math Tutor using AutoGen Core + OpenTelemetry + New Relic**

---

## Table of Contents

1. [Technology Stack Overview](#1-technology-stack-overview)
2. [Why Three Layers of Instrumentation](#2-why-three-layers-of-instrumentation)
3. [Architecture Overview](#3-architecture-overview)
4. [Application Configuration](#4-application-configuration)
5. [OpenTelemetry Collector Configuration](#5-opentelemetry-collector-configuration)
6. [Span Structure and Attributes](#6-span-structure-and-attributes)
7. [Trace Walkthrough](#7-trace-walkthrough)
8. [Known Limitations](#8-known-limitations)
9. [Resources](#9-resources)

---

## 1. Technology Stack Overview

| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Python | 3.12 |
| Web Framework | FastAPI | Laautogen-sample-app |
| Agent Framework | AutoGen Core (`autogen-core`) | 0.4.2 |
| LLM Client | `autogen-ext` OpenAI client | 0.4.2 |
| OTel SDK | `opentelemetry-sdk` | 1.x |
| LLM Auto-instrumentation | `opentelemetry-instrumentation-openai` | 0.52.3 |
| Collector | OpenTelemetry Collector Contrib | 0.91.0 |
| Observability Backend | New Relic | — |

### 1.1 Instrumentation Strategy

This application uses **three layers** of instrumentation:

| Layer | What It Captures | Mechanism |
|-------|-----------------|-----------|
| AutoGen Core runtime | Message routing spans | Built-in — `tracer_provider` on `SingleThreadedAgentRuntime` |
| `OpenAIInstrumentor` | LLM call spans (tokens, prompts, completions) | Auto-patch on OpenAI SDK |
| Manual spans | `gen_ai.agent.name`, `gen_ai.tool.name` | `trace.get_current_span().set_attribute(...)` |

---

## 2. Why Three Layers of Instrumentation

Understanding why each layer is needed is essential for debugging missing spans.

### 2.1 AutoGen Core Runtime — Messaging Spans Only

AutoGen Core's built-in telemetry (`SingleThreadedAgentRuntime`) is based on **OpenTelemetry messaging semantic conventions**. It traces the message passing infrastructure — not the content of what agents do.

When you pass a `tracer_provider` to the runtime:

```python
runtime = SingleThreadedAgentRuntime(tracer_provider=tracer_provider)
```

AutoGen emits spans for:
- `autogen create math_tutor.(default)-A` — a message was created and queued
- `autogen send math_tutor.(default)-A` — a message was dispatched to an agent
- `autogen process math_tutor.(default)-A` — the agent's `on_message()` method was called
- `autogen ack` — message acknowledged

Key attributes on these spans:
```
messaging.operation:   publish / process / receive
messaging.destination: math_tutor.(default)-A
messaging.message.type: UserMessage
```

**What AutoGen does NOT capture:**
- LLM API calls (which model, how many tokens, what was the prompt)
- Which tool was called and with what arguments
- Tool execution results
- `gen_ai.agent.name` or `gen_ai.tool.name` as named attributes

AutoGen's runtime only knows about message routing. It has no visibility into what happens inside `on_message()` — that is your application code.

### 2.2 OpenAIInstrumentor — LLM Call Spans

`OpenAIInstrumentor` monkey-patches the OpenAI Python SDK to emit a span for every `chat.completions.create()` call. This is what produces the `openai.chat` spans with:

```
gen_ai.system:                openai
gen_ai.request.model:         gpt-4o-mini
gen_ai.usage.input_tokens:    280
gen_ai.usage.output_tokens:   18
gen_ai.prompt.0.role:         system
gen_ai.prompt.1.role:         user
gen_ai.prompt.2.tool_calls.0.name: multiply_numbers
gen_ai.completion.0.finish_reason: tool_calls
```

**Without `OpenAIInstrumentor`, there are zero LLM spans.** AutoGen Core uses the OpenAI SDK internally via `OpenAIChatCompletionClient`, but the runtime does not wrap or trace those calls itself.

`OpenAIInstrumentor` must be called **before** the OpenAI client is created, and the same `tracer_provider` must be registered globally:

```python
trace.set_tracer_provider(tracer_provider)
OpenAIInstrumentor().instrument()
# OpenAI client is created later inside the agent factory
```

### 2.3 Manual Spans — Agent Name and Tool Name

Even with AutoGen runtime spans and `OpenAIInstrumentor`, two critical attributes are missing from New Relic:

**`gen_ai.agent.name`** — AutoGen's runtime span (`autogen process math_tutor.(default)-A`) carries `messaging.destination = math_tutor.(default)-A`, but New Relic's AI Monitoring UI looks for `gen_ai.agent.name` (OTel GenAI semantic convention). The runtime does not set this. Solution: set it manually on the existing runtime span from inside `on_message()`:

```python
async def on_message(self, message: UserMessage, ctx: MessageContext) -> AssistantMessage:
    trace.get_current_span().set_attribute("gen_ai.agent.name", "MathTutorAgent")
    ...
```

This enriches the span AutoGen already created — no new span needed.

**`gen_ai.tool.name`** — The tool name does appear inside `openai.chat` spans as `gen_ai.prompt.N.tool_calls.0.name`, but:
1. New Relic's AI Monitoring UI does not surface it from there
2. The OTel Collector cannot copy it to a new span — it can only modify existing spans

The only reliable solution is a dedicated child span per tool execution with `gen_ai.tool.name` set explicitly:

```python
with trace.get_tracer(__name__).start_as_current_span(f"tool.{tool_name}") as tool_span:
    tool_span.set_attribute("gen_ai.tool.name", tool_name)
    tool_result = await tool.run_json(tool_args, cancellation_token)
```

---

## 3. Architecture Overview

### Data Flow Pipeline

```
App (main_autogen_core.py)
  │
  │  [AutoGen runtime]  →  messaging spans (send/process/ack)
  │  [OpenAIInstrumentor]  →  openai.chat spans
  │  [Manual spans]  →  tool.{name} spans with gen_ai.tool.name attribute
  │
  │  OTLPSpanExporter (HTTP)
  │  endpoint: http://localhost:4318/v1/traces
  ↓
OTel Collector (otel-collector-config.yaml)
  │  Receivers:  OTLP gRPC :4317 / HTTP :4318
  │  Processors: memory_limiter → transform/genai_mapping → batch → resource
  │  Exporters:  debug (stdout) + otlphttp/newrelic
  ↓
New Relic (staging-otlp.nr-data.net:4318)
```

### Full Span Tree Per Request

```
autogen create math_tutor.(default)-A     [AutoGen — PRODUCER]
autogen send math_tutor.(default)-A       [AutoGen — PRODUCER]
autogen process math_tutor.(default)-A    [AutoGen — CONSUMER]
  │  gen_ai.agent.name = MathTutorAgent   ← set manually inside on_message()
  │
  ├── openai.chat                         [OpenAIInstrumentor — CLIENT]
  │     gen_ai.request.model = gpt-4o-mini
  │     gen_ai.usage.input_tokens = 280
  │     gen_ai.prompt.2.tool_calls.0.name = multiply_numbers
  │
  ├── tool.multiply_numbers               [Manual span — INTERNAL]
  │     gen_ai.tool.name = multiply_numbers
  │
  └── openai.chat                         [OpenAIInstrumentor — CLIENT]
        gen_ai.completion.0.finish_reason = stop
autogen ack                               [AutoGen — CONSUMER]
```

---

## 4. Application Configuration

### 4.1 OTel Initialization Order

```python
# 1. Load env vars first
load_dotenv(find_dotenv(), override=True)

# 2. Create TracerProvider
resource = Resource.create({SERVICE_NAME: service_name})
tracer_provider = TracerProvider(resource=resource)
tracer_provider.add_span_processor(
    BatchSpanProcessor(
        OTLPSpanExporter(endpoint=otel_endpoint),
        max_export_batch_size=10,
        schedule_delay_millis=1000,
    )
)

# 3. Register globally and instrument OpenAI BEFORE any client is created
trace.set_tracer_provider(tracer_provider)
OpenAIInstrumentor().instrument()

# 4. AutoGen runtime gets the same tracer_provider
runtime = SingleThreadedAgentRuntime(tracer_provider=tracer_provider)
```

### 4.2 Environment Variables

| Variable | Purpose | Example |
|----------|---------|---------|
| `OPENAI_API_KEY` | OpenAI authentication | `sk-...` |
| `LLM_MODEL` | Model name | `gpt-4o-mini` |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | Collector HTTP endpoint (full path required) | `http://localhost:4318/v1/traces` |
| `OTEL_SERVICE_NAME` | Service name in traces | `autogen-math-tutor` |
| `NEW_RELIC_LICENSE_KEY` | Used by collector to export to NR | `...NRAL` |

> The endpoint must include `/v1/traces`. The HTTP exporter does **not** append it automatically.

### 4.3 Setting gen_ai.agent.name (Manual)

Inside `on_message()`, the AutoGen runtime has already created and started the `autogen process` span. Get a reference to it and add the attribute:

```python
async def on_message(self, message: UserMessage, ctx: MessageContext) -> AssistantMessage:
    trace.get_current_span().set_attribute("gen_ai.agent.name", "MathTutorAgent")
    ...
```

This enriches the existing span — no new span is created. `gen_ai.agent.name` follows the OTel GenAI semantic convention, which is what New Relic's AI Monitoring UI looks for.

### 4.4 Setting gen_ai.tool.name (Manual)

Create a dedicated child span wrapping each tool execution:

```python
with trace.get_tracer(__name__).start_as_current_span(f"tool.{tool_name}") as tool_span:
    tool_span.set_attribute("gen_ai.tool.name", tool_name)
    tool_result = await tool.run_json(tool_args, cancellation_token)
```

The span name (`tool.multiply_numbers`) and the attribute (`gen_ai.tool.name = multiply_numbers`) both appear in New Relic.

---

## 5. OpenTelemetry Collector Configuration

See [otel-collector-config.yaml](otel-collector-config.yaml) for the full config.

### 5.1 transform/genai_mapping Processor

Copies `messaging.destination` → `agent.name` as a fallback for spans that don't have `gen_ai.agent.name` set manually:

```yaml
transform/genai_mapping:
  trace_statements:
    - context: span
      statements:
        - set(attributes["tool.name"], attributes["gen_ai.tool.name"]) where attributes["gen_ai.tool.name"] != nil
        - set(attributes["agent.name"], attributes["messaging.destination"]) where attributes["messaging.destination"] != nil
```

> Note: `messaging.destination` contains the full value `math_tutor.(default)-A`. The manually set `gen_ai.agent.name = MathTutorAgent` on the process span is the authoritative attribute for New Relic AI Monitoring.

### 5.2 Pipeline Order

```yaml
processors: [memory_limiter, transform/genai_mapping, batch, resource]
```

`transform` runs before `batch` so all modifications are included in the batched export.

### 5.3 Enable New Relic AI Monitoring

To surface your app in New Relic's **AI Monitoring** section, add `aiEnabledApp: "true"` to the `resource` processor:

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

> This is required for OTel-instrumented apps. Without it, spans arrive in New Relic but the app will not appear in the AI Monitoring entity list. Apps using the New Relic agent get this automatically.

### 5.4 Starting and Restarting the Collector

After any config change, the collector must be restarted:

```bash
docker restart autogen-otel-collector
docker logs autogen-otel-collector  # verify no startup errors
```

---

## 6. Span Structure and Attributes

### 6.1 autogen process span (AutoGen runtime + manual gen_ai.agent.name)

```
Name           : autogen process math_tutor.(default)-A
Kind           : Consumer
InstrumentationScope: autogen SingleThreadedAgentRuntime

Attributes:
  messaging.operation:   process
  messaging.destination: math_tutor.(default)-A
  gen_ai.agent.name:     MathTutorAgent          ← set manually
```

### 6.2 openai.chat span (OpenAIInstrumentor — tool call)

```
Name           : openai.chat
Kind           : Client
Parent         : autogen process math_tutor.(default)-A

Attributes:
  gen_ai.system:                    openai
  gen_ai.request.model:             gpt-4o-mini
  gen_ai.usage.input_tokens:        280
  gen_ai.usage.output_tokens:       18
  gen_ai.completion.0.finish_reason: tool_calls
  gen_ai.prompt.2.tool_calls.0.name: multiply_numbers
  llm.request.functions.2.name:     multiply_numbers
```

### 6.3 tool span (manual)

```
Name           : tool.multiply_numbers
Kind           : Internal
Parent         : autogen process math_tutor.(default)-A

Attributes:
  gen_ai.tool.name: multiply_numbers    ← set manually
```

### 6.4 openai.chat span (OpenAIInstrumentor — final response)

```
Name           : openai.chat
Kind           : Client
Parent         : autogen process math_tutor.(default)-A

Attributes:
  gen_ai.system:                    openai
  gen_ai.request.model:             gpt-4o-mini
  gen_ai.usage.input_tokens:        186
  gen_ai.usage.output_tokens:       9
  gen_ai.completion.0.finish_reason: stop
  gen_ai.completion.0.content:      25 multiplied by 4 is 100.
```

---

## 7. Trace Walkthrough

**Request:** `"What is 25 multiplied by 4?"`

```
autogen create math_tutor.(default)-A  (0ms)
autogen send math_tutor.(default)-A    (1ms)
autogen process math_tutor.(default)-A (2ms → 3200ms)
│  gen_ai.agent.name = MathTutorAgent
│
├── openai.chat  (2ms → 1800ms)
│     LLM receives user message, decides to call multiply_numbers
│     gen_ai.completion.0.finish_reason = tool_calls
│
├── tool.multiply_numbers  (1800ms → 1801ms)
│     gen_ai.tool.name = multiply_numbers
│     executes: 25 * 4 = 100
│
└── openai.chat  (1801ms → 3200ms)
      LLM receives tool result (100), returns final answer
      gen_ai.completion.0.content = "25 multiplied by 4 is 100."
      gen_ai.completion.0.finish_reason = stop

autogen ack  (3200ms)
```

**Total spans per request:** 6 (3 AutoGen messaging + 2 LLM + 1 tool)

---

## 8. Known Limitations

### 8.1 AutoGen Runtime Only Traces Message Routing

AutoGen Core's `SingleThreadedAgentRuntime` follows OTel **messaging** semantic conventions, not GenAI conventions. It emits spans for:
- `create` — a new message envelope
- `send` — message dispatched to agent
- `process` — agent's `on_message()` invoked
- `ack` — message acknowledged

It does **not** emit spans for LLM calls, tool executions, or any business logic inside `on_message()`. This is by design — the runtime is a generic message bus, not an LLM framework.

### 8.2 OpenAIInstrumentor Required for LLM Spans

AutoGen's `OpenAIChatCompletionClient` wraps the OpenAI SDK. The runtime does not intercept or trace the underlying HTTP calls. `OpenAIInstrumentor` is required to get LLM spans. Without it, you see only messaging spans and no `openai.chat` spans.

### 8.3 Manual Spans Required for gen_ai.agent.name and gen_ai.tool.name

The OTel Collector can copy or rename existing attributes on existing spans, but it **cannot create new spans**. This means:

- `gen_ai.agent.name` must be set directly on the `autogen process` span inside `on_message()`
- `gen_ai.tool.name` must be on a dedicated span — the OTel Collector cannot promote `gen_ai.prompt.N.tool_calls.0.name` from the `openai.chat` span into a new span

OTTL transforms in the collector are useful for renaming attributes but cannot solve the missing-span problem.

### 8.4 OTTL Array Indexing Not Supported in v0.91.0

In collector version 0.91.0, `Split(str, ".")[0]` syntax is not supported — `[]` indexing on `[]string` types is not implemented. Use direct attribute copy (`set(attributes["agent.name"], attributes["messaging.destination"])`) as a workaround, or upgrade the collector.

### 8.5 OTLP HTTP Endpoint Requires Full Path

```bash
# Correct
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318/v1/traces

# Wrong — results in 404
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318
```

---

## 9. Resources

- [AutoGen Core Telemetry Guide](https://microsoft.github.io/autogen/stable/user-guide/core-user-guide/core-concepts/telemetry.html)
- [AutoGen AgentChat Tracing Guide](https://microsoft.github.io/autogen/stable/user-guide/agentchat-user-guide/tracing.html)
- [OTel Messaging Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/messaging/messaging-spans/)
- [OTel GenAI Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/gen-ai/)
- [opentelemetry-instrumentation-openai](https://pypi.org/project/opentelemetry-instrumentation-openai/)
- [New Relic AI Monitoring](https://docs.newrelic.com/docs/ai-monitoring/)
- [OTel Collector Contrib](https://github.com/open-telemetry/opentelemetry-collector-contrib)

---

**Application:** AutoGen Core FastAPI Math Tutor
**Instrumentation:** AutoGen runtime (messaging) + OpenAIInstrumentor (LLM) + manual spans (gen_ai.agent.name, gen_ai.tool.name)
**Last Updated:** 2026-03-17
