# AI Sample App - OpenAI Chat Completion with OpenTelemetry (Python)

This is a sample AI application instrumented with [OpenTelemetry Python](https://github.com/open-telemetry/opentelemetry-python) and the official [OpenAI instrumentation](https://github.com/open-telemetry/opentelemetry-python-contrib/tree/main/instrumentation-genai/opentelemetry-instrumentation-openai-v2). It demonstrates how to monitor AI/LLM applications in New Relic using OpenTelemetry.

The application exposes a `/chat` endpoint that sends prompts to OpenAI's chat completion API and returns the response, while exporting rich telemetry (traces, metrics, and logs) to New Relic.

## What telemetry is captured?

The OpenTelemetry OpenAI instrumentation automatically captures:

- **Traces**: `gen_ai` spans with model name, token usage (prompt, completion, total), and operation duration
- **Metrics**: Token usage counters and operation duration histograms following [gen_ai semantic conventions](https://opentelemetry.io/docs/specs/semconv/gen-ai/)
- **Logs/Events**: Prompt and completion content (when `OTEL_INSTRUMENTATION_GENAI_CAPTURE_MESSAGE_CONTENT` is enabled)
- **Custom telemetry**: A `chat.invocations` counter and custom span attributes on the `/chat` endpoint

## Requirements

- [Docker](https://docs.docker.com/get-docker/) and [Docker Compose](https://docs.docker.com/compose/install/)
- [A New Relic account](https://one.newrelic.com/)
- [A New Relic license key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key)
- [An OpenAI API key](https://platform.openai.com/api-keys)

## Running with Docker Compose

1. Create your `.env` file from the template and update the values:

    ```shell
    cp .env.template .env
    # Edit .env with your New Relic license key and OpenAI API key
    ```

2. Start the application and load generator:

    ```shell
    docker compose up -d
    ```

3. Test the endpoint manually:

    ```shell
    curl "http://localhost:8080/chat?prompt=What+is+OpenTelemetry"
    ```

4. View application logs:

    ```shell
    docker compose logs -f app
    ```

5. When finished, stop and remove the containers:

    ```shell
    docker compose down
    ```

## Running locally (without Docker)

1. Set the following environment variables:

    ```shell
    export OTEL_SERVICE_NAME=ai-chatcompletion-python
    export OTEL_RESOURCE_ATTRIBUTES=service.instance.id=123
    export OTEL_PYTHON_LOGGING_AUTO_INSTRUMENTATION_ENABLED=true
    export OTEL_EXPORTER_OTLP_ENDPOINT=https://otlp.nr-data.net
    export OTEL_EXPORTER_OTLP_HEADERS=api-key=<your_new_relic_license_key>
    export OTEL_EXPORTER_OTLP_COMPRESSION=gzip
    export OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
    export OTEL_EXPORTER_OTLP_METRICS_TEMPORALITY_PREFERENCE=delta
    export OTEL_ATTRIBUTE_VALUE_LENGTH_LIMIT=4095
    export OTEL_INSTRUMENTATION_GENAI_CAPTURE_MESSAGE_CONTENT=span_only
    export OTEL_SEMCONV_STABILITY_OPT_IN=gen_ai_latest_experimental
    export OPENAI_API_KEY=<your_openai_api_key>
    ```

    * If your New Relic account is based in the EU, set the endpoint to: `https://otlp.eu01.nr-data.net`

2. Install dependencies:

    ```shell
    python -m pip install -r requirements.txt
    opentelemetry-bootstrap -a install
    ```

3. Run the application:

    ```shell
    opentelemetry-instrument --logs_exporter otlp python3 app.py
    ```

4. Open [http://localhost:8080/chat?prompt=Hello](http://localhost:8080/chat?prompt=Hello) in your browser.

## Viewing your data in New Relic

Navigate to **New Relic > All Entities > Services - OpenTelemetry** and find the service named `ai-chatcompletion-python`.

You can also use these NRQL queries to verify data is flowing:

```sql
-- Verify traces, metrics, and logs are being received
FROM Span, Metric, Log
SELECT
  filter(count(*), WHERE eventType() = 'Log') as 'log_record_count',
  filter(count(*), WHERE eventType() = 'Metric') as 'metric_point_count',
  filter(count(*), WHERE eventType() = 'Span') as 'span_count'
WHERE service.name = 'ai-chatcompletion-python'
SINCE 5 minutes ago

-- View gen_ai spans with token usage
FROM Span
SELECT gen_ai.usage.input_tokens, gen_ai.usage.output_tokens, gen_ai.request.model, duration
WHERE service.name = 'ai-chatcompletion-python' AND span.kind = 'client'
SINCE 5 minutes ago

-- View chat invocation metrics
FROM Metric
SELECT count(chat.invocations)
WHERE service.name = 'ai-chatcompletion-python'
SINCE 5 minutes ago
FACET chat.valid.prompt
```

For AI-specific monitoring, check the **AI Monitoring** section in New Relic for detailed insights into model performance, token usage, and response quality.
