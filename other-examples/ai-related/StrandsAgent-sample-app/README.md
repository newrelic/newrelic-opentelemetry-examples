# Strands Math Tutor Agent

A FastAPI-based math tutor agent built with [Strands Agents](https://github.com/strands-agents) and OpenAI GPT-4o, featuring full observability through OpenTelemetry and New Relic.

## Overview

This application demonstrates how to build an AI agent with tool-use capabilities while maintaining production-grade observability. The agent accepts math questions via a REST API, uses dedicated math tools (add, subtract, multiply, divide) to compute answers, and returns explanations.

## Tech Stack

- **Agent Framework:** Strands Agents `1.52.0` (with OpenAI provider)
- **LLM:** OpenAI GPT-4o (via `openai` SDK `1.109.1`)
- **Web Framework:** FastAPI `0.135.1` + Uvicorn `0.42.0`
- **Validation:** Pydantic `2.12.5`
- **Config:** python-dotenv `1.2.2`
- **Observability:** OpenTelemetry SDK → OTel Collector (`otel/opentelemetry-collector-contrib:0.91.0`) → New Relic
- **Language:** Python `3.12.12`

`requirements.txt` doesn't pin exact versions (`strands-agents[openai]`, unversioned) — the numbers above are what's currently installed in this project's virtual environment. Run `pip show strands-agents` (or any package above) to check what you have.

## Project Structure

```
├── strands_app.py                       # Main application (API + agent + tools)
├── requirements.txt                     # Python dependencies
├── .env                                 # Environment variables (API keys, config) — must live here, at the root
├── nr-config/
│   ├── docker-compose-strands.yaml      # Docker Compose for OTel Collector (reads ../.env)
│   └── strands-otel-collector-config.yaml # OTel Collector pipeline configuration
└── .venv/                               # Python virtual environment
```

## Prerequisites

- Python 3.12+
- Docker and Docker Compose
- An OpenAI API key
- A New Relic license key (for telemetry export)

## Setup

1. **Clone the repository and create a virtual environment:**

   ```bash
   python -m venv .venv
   source .venv/bin/activate
   ```

2. **Install dependencies:**

   ```bash
   pip install -r requirements.txt
   ```

3. **Configure environment variables:**

   Create a `.env` file **at the project root** (not inside `nr-config/`) with the following:

   ```env
   OPENAI_API_KEY=<your-openai-api-key>
   NEW_RELIC_LICENSE_KEY=<your-new-relic-license-key>
   OTEL_SEMCONV_STABILITY_OPT_IN=gen_ai_latest_experimental,gen_ai_span_attributes_only
   ```

   See [`.env.example`](.env.example) for a template.

   > **Note:** `nr-config/docker-compose-strands.yaml` references this file as `env_file: ../.env`, so it must stay at the project root. If the collector starts but `NEW_RELIC_LICENSE_KEY` is missing, traces will silently fail to export — check `docker compose -f nr-config/docker-compose-strands.yaml logs` and confirm `.env` exists at the root.

   `gen_ai_span_attributes_only` makes Strands record `gen_ai.input.messages` / `gen_ai.output.messages` as attributes directly on the `chat`/`invoke_agent` spans.

   > **Note:** `strands_app.py` hardcodes the OTLP exporter endpoint (`http://localhost:4318/v1/traces`) and the collector's `resource` processor force-sets `service.name` to `strands-math-tutor` — so `OTEL_EXPORTER_OTLP_ENDPOINT` and `OTEL_SERVICE_NAME` env vars have no effect here even though the underlying SDK/library support them.

4. **Start the OpenTelemetry Collector:**

   ```bash
   docker-compose -f nr-config/docker-compose-strands.yaml up -d
   ```

5. **Run the application:**

   ```bash
   uvicorn strands_app:app --reload
   ```

   The API will be available at `http://localhost:8000`.

## API Usage

### Endpoints

| Method | Path      | Description                |
|--------|-----------|----------------------------|
| GET    | `/`       | Health check               |
| POST   | `/prompt` | Send a math question       |

### Example Request

```bash
curl -X POST "http://localhost:8000/prompt" \
  -H "Content-Type: application/json" \
  -d '{"prompt": "What is 25 times 4?"}'
```

### Interactive Docs

- Swagger UI: [http://localhost:8000/docs](http://localhost:8000/docs)
- ReDoc: [http://localhost:8000/redoc](http://localhost:8000/redoc)

## Available Tools

The agent has access to four math tools:

| Tool               | Description                          |
|--------------------|--------------------------------------|
| `add_numbers`      | Adds two integers                    |
| `subtract_numbers` | Subtracts two integers               |
| `multiply_numbers` | Multiplies two integers              |
| `divide_numbers`   | Divides two integers (zero-safe)     |

## Observability

### Architecture

```
FastAPI App → Strands Telemetry (OTel SDK) → OTel Collector → New Relic
```

### What's Instrumented

- Agent execution traces
- Individual tool invocations
- LLM API calls (including input/output content)
- Token usage metrics

### OTel Collector

The collector runs as a Docker container and is configured with:

- **Receivers:** OTLP over gRPC (`:4317`) and HTTP (`:4318`)
- **Processors:** Memory limiter, resource attributes
- **Exporters:** New Relic (OTLP/HTTP), console logging, optional file output

### Ports

| Port  | Protocol   | Service              |
|-------|------------|----------------------|
| 8000  | HTTP       | FastAPI application  |
| 4317  | gRPC       | OTel Collector OTLP  |
| 4318  | HTTP       | OTel Collector OTLP  |
| 55679 | HTTP       | ZPages (debug UI)    |

### Known gaps — fields that don't show up

| Field | Why it's not visible |
|---|---|
| **Finish reason** | Not captured. It's not extracted into its own attribute, so it isn't visible in NRQL or the New Relic UI. |
| **Response model** | Not captured. |
| **Response ID** | Not captured. |
| **Request ID** | Not captured. |
| **Host** | Not captured. |
| **Conversation ID** | Not captured. |
