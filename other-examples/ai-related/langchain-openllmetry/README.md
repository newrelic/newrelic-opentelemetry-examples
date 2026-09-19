# LangChain + OTel + genainormalizerprocessor sample (OpenLLMetry)

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
2. [`app/run_openllmetry.py`](app/run_openllmetry.py) wraps that agent
   with `Traceloop.init()` from
   [`traceloop-sdk`](https://pypi.org/project/traceloop-sdk/) — a
   zero-code auto-instrumentor that patches LangChain's callback system and
   emits OpenLLMetry span attributes (`traceloop.entity.name`,
   `traceloop.span.kind`, `gen_ai.tool.name`, ...) with no manual mapping
   code. It also stamps a permanent `instrumentation.source: openllmetry`
   resource attribute.
3. That script exports OTLP/HTTP to a local OTel Collector
   ([`otel-collector-config.yaml`](otel-collector-config.yaml)) running the
   [`genainormalizerprocessor`](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/processor/genainormalizerprocessor)
   (config key: `gen_ai_normalizer`), configured with the `openllmetry`
   source. The processor renames the vendor-specific attributes into the
   standard `gen_ai.*` names and drops the originals (`remove_originals:
   true`).

   The currently pinned `opentelemetry-instrumentation-langchain` release
   already emits most `gen_ai.*` attributes directly — `gen_ai_normalizer`'s
   `openllmetry` mapping table mostly has nothing left to rename on this
   app's spans. What it (and the raw instrumentor output) **doesn't** get
   right on its own is `gen_ai.agent.name`: the collector's documented
   `traceloop.entity.name → gen_ai.agent.name` rule fires unconditionally,
   and the instrumentor sets `traceloop.entity.name` on every LangGraph
   task/tool span, not just real agent spans. A small
   `transform/fix_tool_agent_conflict` OTTL processor cleans this up — see
   `DOCUMENTATION.md` Section 8 for the full story.

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

python3.12 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt

docker compose up -d
```

`langchain.agents.create_agent` requires a modern LangChain (`>=0.3.0`) and
was developed/verified against **Python 3.12** — a much older interpreter
(e.g. the macOS system Python 3.9) may not resolve compatible wheels.

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
another OTel Collector locally, one of you needs to either stop the other
container or remap ports in `docker-compose.yml` **and** update
`OTEL_EXPORTER_OTLP_ENDPOINT` in `.env` to match — the entry script
defaults to `http://localhost:4318`, so if the collector isn't actually
listening there, traces silently go nowhere (or to someone else's
collector) with no error printed by the Python script.

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
python app/run_openllmetry.py "Plan a trip to Berlin — tell me the weather and the best places to visit"
```

## Verify normalization

```bash
docker compose logs otel-collector | grep "gen_ai\."
```

You should see `gen_ai.request.model`, `gen_ai.tool.name`,
`gen_ai.agent.name`, `gen_ai.operation.name`, `gen_ai.usage.input_tokens`,
`gen_ai.input.messages` / `gen_ai.output.messages`, etc. on the spans.
`gen_ai.tool.name` should appear on exactly the four tool spans
(`get_weather`, `get_places`, `ask_weather_agent`, `ask_places_agent`);
`gen_ai.agent.name` on exactly the three agent spans (`TripPlannerAgent`,
`WeatherAgent`, `PlacesAgent`) — both show up correctly, side by side, in
New Relic's AI Monitoring UI. If you instead see `gen_ai.agent.name` on
spans named `model` or `tools` (LangGraph's internal graph nodes), the
`transform/fix_tool_agent_conflict` processor isn't running — see
Troubleshooting below.

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
  listening where `OTEL_EXPORTER_OTLP_ENDPOINT` (or its
  `http://localhost:4318` default) points. Check `docker ps` for the actual
  port mapping.
- **`SSL_ERROR_SSL: ... WRONG_VERSION_NUMBER` / TLS handshake failures on
  metrics export** — this is a real bug in `traceloop-sdk`'s metrics
  exporter (it never sets `insecure=True` for gRPC, unlike its trace
  exporter). Stick to the HTTP OTLP endpoint (`http://localhost:4318`, the
  default in `run_openllmetry.py`) rather than a bare `grpc://` endpoint,
  which sidesteps the bug entirely for both traces and metrics.
- **`gen_ai.agent.name` shows up on spans named `model` or `tools`, or
  collides with `gen_ai.tool.name` on a tool-execution span** — confirm
  `transform/fix_tool_agent_conflict` is present in
  `otel-collector-config.yaml`'s `processors` section **and** listed in the
  `traces` pipeline's `processors` list. This has been observed to get
  edited out by IDE autoformatting/autosave — see `DOCUMENTATION.md` Section 8 for
  why the rule is mandatory, not optional.
- **No errors anywhere, but nothing shows up in New Relic** — check you're
  sending to the right endpoint for your key's environment (production vs.
  staging vs. EU), and that you're looking at the matching account/UI.
