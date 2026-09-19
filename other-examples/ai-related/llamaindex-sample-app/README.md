# LlamaIndex FastAPI with OpenTelemetry → New Relic

A FastAPI application using LlamaIndex agents with full OpenTelemetry tracing via OpenInference auto-instrumentation. Traces are collected by an OTel Collector and forwarded to New Relic.

## Architecture

```
FastAPI App (port 8080)
    └── OpenInference auto-instrumentation (LlamaIndex spans)
    └── OTLPSpanExporter (HTTP)
            ↓
OTel Collector (port 4318)
    └── transform/genai processor  →  maps to gen_ai.* attributes
    └── debug exporter             →  logs spans locally
    └── otlphttp/newrelic exporter →  forwards to New Relic
```

## Prerequisites

- Python 3.9+
- Docker
- OpenAI API key
- New Relic license key

## Setup

### 1. Environment variables

Create a `.env` file:

```env
OPENAI_API_KEY=your-openai-api-key
NEW_RELIC_LICENSE_KEY=your-newrelic-license-key
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318
OTEL_SERVICE_NAME=llamaindex-sample-app
```

### 2. Python virtual environment

```bash
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

### 3. Enable New Relic AI Monitoring

To surface your app in New Relic's **AI Monitoring** section, add `aiEnabledApp: "true"` to the `resource` processor in your OTel Collector config ([`nr-config/otel-collector-llamaindex.yaml`](nr-config/otel-collector-llamaindex.yaml)):

```yaml
processors:
  resource:
    attributes:
      - key: aiEnabledApp
        value: "true"
        action: insert
```

> This is required for OTel-instrumented apps. Without it, spans arrive in New Relic but the app will not appear in the AI Monitoring entity list. Apps using the New Relic agent get this automatically.

### 5. Start the OTel Collector

```bash
docker compose -f docker-compose-test.yaml up -d
```

### 6. Start the app

```bash
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318 venv/bin/uvicorn llamaindex_app:app --port 8080
```

## API Endpoints

### POST /chat
Uses `FunctionCallingAgent` — directly calls tools based on the user's request.

```bash
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"prompt": "What is 10 plus 5?"}'
```

### POST /chat/react
Uses `ReActAgent` — reasons step by step (Thought → Action → Observation → Answer).

```bash
curl -X POST http://localhost:8080/chat/react \
  -H "Content-Type: application/json" \
  -d '{"prompt": "What is 20 divided by 4?"}'
```

## Available Tools

| Tool | Description |
|------|-------------|
| `add_numbers` | Add two integers |
| `subtract_numbers` | Subtract b from a |
| `multiply_numbers` | Multiply two integers |
| `divide_numbers` | Divide a by b (returns error on divide by zero) |

## Telemetry

### Spans emitted per request

| Span Name | Kind | Description |
|-----------|------|-------------|
| `chat` / `chat_react` | CHAIN | Top-level request span |
| `AgentRunner.achat` | AGENT | Agent execution |
| `AgentRunner._achat` | AGENT | Internal async chat |
| `AgentRunner._arun_step` | AGENT | Each reasoning step |
| `FunctionCallingAgentWorker.*` / `ReActAgent.*` | AGENT | Agent worker steps |
| `OpenAI.achat` | LLM | LLM API call |
| `FunctionTool.acall` | TOOL | Tool execution |

### GenAI attributes (mapped by collector)

| Attribute | Source | Description |
|-----------|--------|-------------|
| `gen_ai.system` | `llm.model_name` | Always `openai` |
| `gen_ai.request.model` | `llm.model_name` | e.g. `gpt-4` |
| `gen_ai.usage.input_tokens` | `llm.token_count.prompt` | Prompt token count |
| `gen_ai.usage.output_tokens` | `llm.token_count.completion` | Completion token count |
| `gen_ai.tool.name` | `tool.name` | Tool called |
| `gen_ai.agent.name` | span name | `FunctionCallingAgent` or `ReActAgent` |
| `gen_ai.input.messages` | set in app | JSON array: `[{"role":"user","content":"..."}]` |
| `gen_ai.output.messages` | set in app | JSON array: `[{"role":"assistant","content":"..."}]` |
| `gen_ai.response.finish_reasons` | set in app | JSON array: e.g. `["stop"]` or `["tool_calls"]` |

## Verify traces

Check the OTel Collector is receiving spans:

```bash
docker logs test-collector --since 30s | grep -v "0.0.0.0"
```

