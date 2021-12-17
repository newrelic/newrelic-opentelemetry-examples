FROM otel/opentelemetry-collector-contrib:0.40.0 AS base

FROM bash:5.1.12

COPY --from=base /otelcontribcol .
COPY collector.yaml .

ARG OTEL_EXPORTER_OTLP_ENDPOINT
ARG NEW_RELIC_API_KEY
ENV OTEL_EXPORTER_OTLP_ENDPOINT=${OTEL_EXPORTER_OTLP_ENDPOINT}
ENV NEW_RELIC_API_KEY=${NEW_RELIC_API_KEY}

CMD ["/otelcontribcol", "--config=collector.yaml"]
