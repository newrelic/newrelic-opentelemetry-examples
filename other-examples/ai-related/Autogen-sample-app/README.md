# AutoGen Core FastAPI with OpenTelemetry & New Relic

A FastAPI application using AutoGen Core with a `MathTutorAgent` that performs calculations via tool calls. Traces are exported via OTEL Collector to New Relic.

## Architecture

```
FastAPI App (AutoGen Core)
    ↓ AutoGen runtime  →  messaging-level spans (send/process)
    ↓ OpenAIInstrumentor  →  LLM call spans (openai.chat)
    ↓ Manual spans  →  gen_ai.agent.name + gen_ai.tool.name
OTel Collector (port 4318 HTTP)
    ↓ OTLP
New Relic
```

## Why Three Layers of Instrumentation

| Layer | What it captures | How |
|-------|-----------------|-----|
| AutoGen Core runtime | Message routing spans (`send`, `process`) | Built-in — pass `tracer_provider` to `SingleThreadedAgentRuntime` |
| `OpenAIInstrumentor` | LLM call spans with token counts, prompts, completions | Auto — `OpenAIInstrumentor().instrument()` |
| Manual spans | `gen_ai.agent.name`, `gen_ai.tool.name` | Manual — `trace.get_current_span().set_attribute(...)` |

AutoGen's built-in telemetry only covers **message routing** (who sent what to whom). It does not instrument LLM calls or tool executions — those require `OpenAIInstrumentor` and manual spans respectively.

## Trace Structure

Each request produces the following span hierarchy:

```
autogen process math_tutor.(default)-A    ← AutoGen runtime span (messaging)
  openai.chat                              ← LLM call (OpenAIInstrumentor)
    tool.multiply_numbers                  ← tool execution (manual span)
      gen_ai.tool.name = multiply_numbers
  openai.chat                              ← LLM final response (OpenAIInstrumentor)
```

Key attributes visible in New Relic:
- `gen_ai.agent.name` — set manually on the AutoGen runtime span
- `gen_ai.tool.name` — set manually on a dedicated child span per tool call
- `gen_ai.usage.input_tokens` / `gen_ai.usage.output_tokens` — from OpenAIInstrumentor
- `messaging.destination` — from AutoGen runtime (e.g. `math_tutor.(default)-A`)

## Prerequisites

- Python 3.9+
- Docker
- OpenAI API key
- New Relic account and license key

## Setup

### 1. Create virtual environment and install dependencies

```bash
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

### 2. Configure environment

Edit `.env`:

```bash
OPENAI_API_KEY=sk-your-key
LLM_MODEL=gpt-4o-mini
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318/v1/traces
OTEL_SERVICE_NAME=autogen-math-tutor
NEW_RELIC_LICENSE_KEY=your-new-relic-license-key
```

### 3. Enable New Relic AI Monitoring

To surface your app in New Relic's **AI Monitoring** section, add `aiEnabledApp: "true"` to the `resource` processor in your OTel Collector config ([`nr-config/otel-collector-config.yaml`](nr-config/otel-collector-config.yaml)):

```yaml
processors:
  resource:
    attributes:
      - key: aiEnabledApp
        value: "true"
        action: insert
```

> This is required for OTel-instrumented apps. Without it, spans arrive in New Relic but the app will not appear in the AI Monitoring entity list. Apps using the New Relic agent get this automatically.

### 4. Start the OTEL Collector

```bash
docker run -d \
  --name autogen-otel-collector \
  -p 4317:4317 -p 4318:4318 \
  -e NEW_RELIC_LICENSE_KEY=your-new-relic-license-key \
  -v $(pwd)/otel-collector-config.yaml:/etc/otel-collector-config.yaml \
  otel/opentelemetry-collector-contrib:0.91.0 \
  --config=/etc/otel-collector-config.yaml
```

### 5. Run the application

```bash
venv/bin/uvicorn main_autogen_core:app --host 0.0.0.0 --port 8000
```

## API

```bash
curl -X POST http://localhost:8000/chat \
  -H "Content-Type: application/json" \
  -d '{"prompt": "What is 25 multiplied by 4?"}'
```

## Available Tools

- `add_numbers(a, b)` — Addition
- `subtract_numbers(a, b)` — Subtraction
- `multiply_numbers(a, b)` — Multiplication
- `divide_numbers(a, b)` — Division (returns error if b is zero)

## Project Structure

```
autogen-fastapi-newrelic/
├── main_autogen_core.py         # FastAPI app with AutoGen Core + instrumentation
├── otel-collector-config.yaml   # OTEL collector config (transform + export to NR)
├── requirements.txt             # Python dependencies
├── .env                         # Environment variables
├── README.md
└── DOCUMENTATION_GUIDE.md      # Detailed instrumentation guide
```

## Viewing in New Relic

1. Go to **APM & Services** → your service name
2. Open **Distributed Tracing**
3. Filter by `gen_ai.agent.name = MathTutorAgent` or `gen_ai.tool.name = multiply_numbers`

## Port Reference

- **8000** — FastAPI app
- **4317** — OTEL Collector gRPC
- **4318** — OTEL Collector HTTP

## Troubleshooting

**No spans in collector logs**
- Check `OTEL_EXPORTER_OTLP_ENDPOINT` includes the full path: `http://localhost:4318/v1/traces`

**No data in New Relic**
- Verify `NEW_RELIC_LICENSE_KEY` is set in the collector environment
- Check collector logs: `docker logs autogen-otel-collector`
- Wait 1-2 minutes for data to appear

**Collector fails to start**
- Check config syntax: `docker logs autogen-otel-collector`
- After any config change, restart: `docker restart autogen-otel-collector`
