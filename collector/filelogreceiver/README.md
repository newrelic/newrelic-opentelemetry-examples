1. Set envirioment variables for OTLP export.
```shell
export OTEL_EXPORTER_OTLP_ENDPOINT=https://otlp.nr-data.net:4317
export NEW_RELIC_API_KEY=your_api_key
```

2. Build and run:
```shell
docker-compose up --build
```

2. Write lines to logfile.txt.

3. See lines exported via the logging exporter and OTLP exporter.
