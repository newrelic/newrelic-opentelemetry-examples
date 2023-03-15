# Instrumented Python Application
```
cd ./getting-started-guides/python/Instrumented/
```

## Summary
1. Install `virtualenv`, if not already done
2. Create a virtual environment for the application, if needed
3. Activate virtual environment
4. Upgrade `pip` in virtual environment
5. Install dependencies listed in `requirements.txt` or install manually

| Step | Windows (PowerShell)                     | Linux / MacOS (bash)                      |
|------|------------------------------------------|-------------------------------------------|
| 1    | `pip install virtualenv`                 | `pip3 install virtualenv`                 |
| 2    | `python -m venv venv`                    | `python3 -m venv venv`                    |
| 3    | `.\venv\Scripts\Activate.ps1`            | `source venv/bin/activate`                |
| 4    | `python -m pip install --upgrade pip`    | `python3 -m pip install --upgrade pip`    |
| 5    | `pip install -r requirements.txt`        | `pip install -r requirements.txt`         |
| 6    | Set OTEL environment variables `$Env:`   | Set OTEL environment variables `export`   |
| 7    | `python app.py`                          | `python3 app.py`                          |

Set the Application Name and New Relic Ingest - License Key.

## Details
1. To send telemetry data to New Relic, set the following environment variables

Windows (PowerShell)
```
$Env:OTEL_PYTHON_LOG_CORRELATION="true"
$Env:OTEL_LOGS_EXPORTER="otlp"
$Env:OTEL_LOG_LEVEL="debug"
$Env:OTEL_EXPORTER_OTLP_ENDPOINT="https://otlp.nr-data.net:4317"
$Env:OTEL_EXPORTER_OTLP_HEADERS="api-key=XXXX...NRAL"
$Env:OTEL_SERVICE_NAME="otel-python-instrumented"
$Env:OTEL_RESOURCE_ATTRIBUTES="service.instance.id=localhost-pc,tags.team=newrelic"
```

Linux / macOS
```
export OTEL_PYTHON_LOG_CORRELATION="true"
export OTEL_LOGS_EXPORTER="otlp"
export OTEL_LOG_LEVEL="debug"
export OTEL_EXPORTER_OTLP_ENDPOINT="https://otlp.nr-data.net:4317"
export OTEL_EXPORTER_OTLP_HEADERS="api-key=XXXX...NRAL"
export OTEL_SERVICE_NAME="otel-python-uninstrumented"
export OTEL_RESOURCE_ATTRIBUTES="service.instance.id=localhost-pc,tags.team=newrelic"
```

2. Install the following packages to your virtual environment
```
pip install flask
pip install opentelemetry-instrumentation-logging
pip install opentelemetry-instrumentation-flask
pip install opentelemetry-exporter-otlp
pip install opentelemetry-distro
```

3. Make the following changes to `app.py` to instrument manually.