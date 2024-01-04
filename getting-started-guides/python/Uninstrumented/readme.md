# Getting Started Guide - Python (Uninstrumented)
This is the solution for the Python demo application used in the Getting Started Guide - Python.

Requires:
- Python 3.8+
- [A New Relic account](https://one.newrelic.com/)

To run this demo app via the CLI, start by switching to the `python/Uninstrumented` directory.

```
cd ./getting-started-guides/python/Uninstrumented/
```

## Summary
1. Create a virtual environment for the application, if needed
2. Activate virtual environment
3. Upgrade `pip` in virtual environment
4. Install dependencies
5. Set the required environment variables
6. Run the application
7. Generate traffic

| Step | Windows (PowerShell)                     | Linux / MacOS (bash)                      |
|------|------------------------------------------|-------------------------------------------|
| 1    | `python -m venv venv`                    | `python3 -m venv venv`                    |
| 2    | `.\venv\Scripts\Activate.ps1`            | `source venv/bin/activate`                |
| 3    | `python -m pip install --upgrade pip`    | `python3 -m pip install --upgrade pip`    |
| 4    | `pip install flask`<br>`pip install opentelemetry-instrumentation-flask`<br>`pip install opentelemetry-exporter-otlp`<br>`pip install opentelemetry-distro` | `pip install flask`<br>`pip install opentelemetry-instrumentation-flask`<br>`pip install opentelemetry-exporter-otlp`<br>`pip install opentelemetry-distro` |
| 5    | Set environment variables with `$Env:`   | Set environment variables with `export`   |
| 6    | `opentelemetry-instrument python app.py` | `opentelemetry-instrument python3 app.py` |
| 7    | `.\load-generator.ps1`                   | `./load-generator.sh`                     |

Set the Application Name and New Relic [Ingest - License Key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key).

## Details
1. To send telemetry data to New Relic, set the following environment variables

    Windows (PowerShell)
    ```
    $Env:OTEL_EXPORTER_OTLP_ENDPOINT="https://otlp.nr-data.net:4317"
    $Env:OTEL_EXPORTER_OTLP_HEADERS="api-key=XXXX...NRAL"
    $Env:OTEL_SERVICE_NAME="getting-started-python"
    $Env:OTEL_RESOURCE_ATTRIBUTES="service.instance.id=localhost"
    ```

    Linux / macOS
    ```
    export OTEL_EXPORTER_OTLP_ENDPOINT="https://otlp.nr-data.net:4317"
    export OTEL_EXPORTER_OTLP_HEADERS="api-key=XXXX...NRAL"
    export OTEL_SERVICE_NAME="getting-started-python"
    export OTEL_RESOURCE_ATTRIBUTES="service.instance.id=localhost"
    ```

2. Install the following packages to your virtual environment.
    ```
    pip install flask
    pip install opentelemetry-instrumentation-flask
    pip install opentelemetry-exporter-otlp
    pip install opentelemetry-distro
    ```

3. When the [auto-instrument](https://opentelemetry.io/docs/instrumentation/python/automatic/) package is used, no changes to the code is needed, just run the app as usual but with `opentelemetry-instrument` before the command to start the application. To get logging data you must also set the OTEL_PYTHON_LOGGING_AUTO_INSTRUMENTATION_ENABLED environment variable to true.

For example:

    ```
    export OTEL_PYTHON_LOGGING_AUTO_INSTRUMENTATION_ENABLED=true
    opentelemetry-instrument python app.py
    ```

4. To generate traffic, in a new terminal tab, run the following command:
   PowerShell:
   ```powershell
   .\load-generator.ps1
   ```
   Bash:
   ```bash
   ./load-generator.sh
   ```

5. This will only collect logs. In order to get traces and metrics you need to [make the following update to app.py](https://opentelemetry-python-contrib.readthedocs.io/en/latest/instrumentation/flask/flask.html#id1)
    ```
    from opentelemetry.instrumentation.flask import FlaskInstrumentor

    app = Flask(__name__)

    FlaskInstrumentor().instrument_app(app)
    ```

Then run the app again with:

    opentelemetry-instrument --logs_exporter otlp python app.py


5. To shut down the program, run the following in both shells or terminal tabs: `CTRL + C`.