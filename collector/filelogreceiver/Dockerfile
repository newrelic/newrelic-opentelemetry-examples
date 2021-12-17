FROM otel/opentelemetry-collector-contrib:0.40.0 AS base

FROM bash:5.1.12

COPY --from=base /otelcontribcol .
COPY collector.yaml .

RUN touch logfile.txt

CMD ["/otelcontribcol", "--config=collector.yaml"]
