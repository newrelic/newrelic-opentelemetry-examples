# syntax=docker/dockerfile:1
#
# Unlike getting-started-guides/python/Dockerfile, this image does NOT bake in
# any zero-code auto-instrumentation: no `opentelemetry-bootstrap -a install`,
# no `opentelemetry-instrument` wrapper. It relies entirely on the
# OpenTelemetry Operator's Instrumentation CR to inject the Python SDK and
# auto-instrumentation packages at pod admission time, demonstrating that
# "no code changes, no image changes beyond removing your own agent" is
# genuinely all that's needed for Operator-based auto-injection.
#
# requirements.txt (from the original app) is still installed, since app.py
# calls the OpenTelemetry API directly (opentelemetry.trace/metrics) -- those
# calls are cheap no-ops until the Operator's injected SDK registers the
# global providers.
ARG PYTHON_VERSION=3.12.2
FROM python:${PYTHON_VERSION}-slim

WORKDIR /app

ARG UID=10001
RUN adduser \
    --disabled-password \
    --gecos "" \
    --home "/nonexistent" \
    --shell "/sbin/nologin" \
    --no-create-home \
    --uid "${UID}" \
    appuser

RUN --mount=type=cache,target=/root/.cache/pip \
    --mount=type=bind,source=requirements.txt,target=requirements.txt \
    python -m pip install -r requirements.txt

COPY app.py .

USER appuser

EXPOSE 8080

CMD ["python3", "app.py"]
