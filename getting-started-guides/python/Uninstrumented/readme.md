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
1. Install `virtualenv`, if not already done
2. Create a virtual environment for the application, if needed
3. Activate virtual environment
4. Upgrade `pip` in virtual environment
5. Install dependencies listed in `requirements.txt`
6. Set the required environment variables
7. Run the application
8. Generate traffic

| Step | Windows (PowerShell)                     | Linux / MacOS (bash)                      |
|------|------------------------------------------|-------------------------------------------|
| 1    | `pip install virtualenv`                 | `pip3 install virtualenv`                 |
| 2    | `python -m venv venv`                    | `python3 -m venv venv`                    |
| 3    | `.\venv\Scripts\Activate.ps1`            | `source venv/bin/activate`                |
| 4    | `python -m pip install --upgrade pip`    | `python3 -m pip install --upgrade pip`    |
| 5    | `pip install -r requirements.txt`        | `pip install -r requirements.txt`         |
| 6    | Set environment variables with `$Env:`   | Set environment variables with `export`   |
| 7    | `opentelemetry-instrument python app.py` | `opentelemetry-instrument python3 app.py` |
| 8    | `.\load-generator.ps1`                   | `./load-generator.sh`                     |

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

2. Install the following packages to your virtual environment or install from the  `requirements.txt` file.
    ```
    pip install flask
    pip install opentelemetry-instrumentation-flask
    pip install opentelemetry-exporter-otlp
    pip install opentelemetry-distro
    ```

3. No changes to the code is needed, just run the app as usual but with `opentelemetry-instrument` before the command to start the application.
   
4. To generate traffic, in a new terminal tab, run the following command:
   PowerShell:
   ```powershell
   .\load-generator.ps1
   ```
   Bash:
   ```bash
   ./load-generator.sh
   ```
   
5. To shut down the program, run the following in both shells or terminal tabs: `CTRL + C`.