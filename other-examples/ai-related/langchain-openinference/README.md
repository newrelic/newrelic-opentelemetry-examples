# LangChain + OTel + genainormalizerprocessor sample

Demonstrates a LangChain multi-agent trip planner whose spans are
normalized into
[OTel GenAI semantic conventions](https://github.com/open-telemetry/semantic-conventions-genai)
almost entirely by the collector — no manual `gen_ai.*` attribute-setting
code in the app itself.

## How it works

1. [`app/agent.py`](app/agent.py) defines a small multi-agent trip
   planner (`TripPlannerAgent` delegating to `WeatherAgent` /
   `PlacesAgent`, each with its own tool) with **no OTel imports at all**.
   See [`DOCUMENTATION.md`](DOCUMENTATION.md) for the full
   agent/tool graph.
2. [`app/run_openinference.py`](app/run_openinference.py) wraps that agent
   with a zero-code auto-instrumentor —
   [`openinference-instrumentation-langchain`](https://github.com/Arize-ai/openinference),
   which emits `llm.*` / `tool.*` attributes. It also stamps a permanent
   `instrumentation.source: openinference` resource attribute.
3. That script exports OTLP/gRPC to a local OTel Collector
   ([`otel-collector-config.yaml`](otel-collector-config.yaml)) running the
   [`genainormalizerprocessor`](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/processor/genainormalizerprocessor)
   (config key: `gen_ai_normalizer`), configured with the `openinference`
   source. The processor renames the vendor-specific attributes into the
   standard `gen_ai.*` names (`gen_ai.request.model`, `gen_ai.tool.name`,
   `gen_ai.usage.input_tokens`, `gen_ai.input.messages`, ...) and drops the
   originals (`remove_originals: true`).

   `gen_ai_normalizer` doesn't set `gen_ai.agent.name` for this
   instrumentor (a real gap in `openinference-instrumentation-langchain` —
   see `DOCUMENTATION.md` §8.6), so a small `transform/backfill_missing`
   OTTL processor fills that in, along with a couple of other fields
   (`gen_ai.response.finish_reasons`, `gen_ai.response.id`,
   `gen_ai.response.model`) that the instrumentor emits but the built-in
   mapping table doesn't cover.

   **`gen_ai_normalizer` is `alpha` stability and only shipped in
   `otel/opentelemetry-collector-contrib` starting at `v0.159.0`.**
   [`docker-compose.yml`](docker-compose.yml) pins that exact tag — do not
   move it back to `:latest` without first confirming the pulled image
   actually contains `gen_ai_normalizer` (`docker run --rm <image>
   components | grep gen_ai_normalizer`), otherwise the collector will
   crash-loop on startup with `unknown type: "gen_ai_normalizer"`.
4. The collector also runs `memory_limiter` and a `resource` processor
   (upserts `service.name` / `deployment.environment`), then forwards
   normalized spans to the console (`debug` exporter) and to New Relic
   (`otlphttp/newrelic`). Metrics and logs pipelines are wired the same way
   (minus `gen_ai_normalizer`/`transform`, which only apply to traces).

## Setup

```bash
cp .env.example .env
# fill in OPENAI_API_KEY and NEW_RELIC_LICENSE_KEY in .env

python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt

docker-compose up -d
```

### Choosing a New Relic endpoint

[`otel-collector-config.yaml`](otel-collector-config.yaml) sets the
`otlphttp/newrelic` exporter endpoint. Pick the one that matches your
license key/account:

| Target | Endpoint |
|---|---|
| Production (US) | `https://otlp.nr-data.net` |
| Production (EU) | `https://otlp.eu01.nr-data.net` |
| Staging | `https://staging-otlp.nr-data.net:4318` |

A production license key sent to the staging endpoint (or vice versa) will
generally fail auth or simply never appear in the UI you're looking at —
there's no cross-environment fallback.

### Port conflicts

The collector listens on `4317` (OTLP gRPC), `4318` (OTLP HTTP), and `55679`
(ZPages, `http://localhost:55679/debug/tracez`). If you're also running
another OTel Collector locally (e.g. a different sample app), one of you
needs to either stop the other container or remap ports in
`docker-compose.yml` **and** update `OTEL_EXPORTER_OTLP_ENDPOINT` in `.env`
to match — the entry script defaults to `http://localhost:4317`, so if the
collector isn't actually listening there, traces silently go nowhere (or to
someone else's collector) with no error printed by the Python script.

## Run

### Quickest path: `run.sh`

```bash
./run.sh
```

This brings up the collector, installs dependencies, and fires 4 sample
trip-planning prompts automatically, 5s apart. It exits non-zero and copies
`.env.example` → `.env` if `.env` doesn't exist yet — fill in the keys and
re-run.

### Manual / one-off prompts

```bash
python app/run_openinference.py "Plan a trip to Berlin — tell me the weather and the best places to visit"
```

## Verify normalization

```bash
docker-compose logs otel-collector | grep "gen_ai\."
```

You should see `gen_ai.request.model`, `gen_ai.tool.name`,
`gen_ai.agent.name`, `gen_ai.operation.name`, `gen_ai.usage.input_tokens`,
`gen_ai.input.messages` / `gen_ai.output.messages`, etc. on the spans, and
no leftover `llm.*` attributes (the processor removed them via
`remove_originals: true`). `gen_ai.tool.name` appears on the four tool
spans (`get_weather`, `get_places`, `ask_weather_agent`,
`ask_places_agent`); `gen_ai.agent.name` on the three agent spans
(`TripPlannerAgent`, `WeatherAgent`, `PlacesAgent`) — both show up
correctly, side by side, in New Relic's AI Monitoring UI (see
`DOCUMENTATION.md` §8.6 for the backstory).

If `NEW_RELIC_LICENSE_KEY` is set and points at the right
environment/endpoint (see above), the same normalized spans also appear
under **APM & Services** and **AI Monitoring** in New Relic. New services
can take a minute or two to first appear after ingest.

## Troubleshooting

- **Collector container keeps restarting** — check
  `docker logs <container>` for `unknown type: "gen_ai_normalizer"`. This
  means the pulled image predates the processor's release; confirm the
  `docker-compose.yml` image tag is `0.159.0` or newer.
- **`Connection refused` from the Python script** — the collector isn't
  listening where `OTEL_EXPORTER_OTLP_ENDPOINT` (or its `localhost:4317`
  default) points. Check `docker ps` for the actual port mapping.
- **No errors anywhere, but nothing shows up in New Relic** — check you're
  sending to the right endpoint for your key's environment (production vs.
  staging vs. EU), and that you're looking at the matching account/UI.
