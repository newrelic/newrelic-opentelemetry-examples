# Python services instrumented with OpenTelemetry 

This repo contains two simple Python services that have been instrumented with [OpenTelemetry-Python](https://github.com/open-telemetry/opentelemetry-python) that you can configure to export traces to your New Relic account!

The service `main.py` simply creates spans, while `app.py` does the same but includes [Flask](https://flask.palletsprojects.com/en/2.0.x/).

## Prerequisites

- Sign up for a [free New Relic account](https://newrelic.com/signup).

- Copy your New Relic [account ingest license key](https://one.newrelic.com/launcher/api-keys-ui.launcher).

- This assumes you have `pip` and `python3` installed on your machine. 

## Run

1. Run `pip install -r requirements.txt`.

2. Set the following environment variable:

   ```shell
   export OTEL_EXPORTER_OTLP_HEADERS=api-key=<your_license_key_here>
   ```
   - Replace `<your_license_key_here>` with your New Relic account ingest license key.

   **Note: `OTEL_EXPORTER_OTLP_ENDPOINT` has been set to `https://otlp.nr-data.net:4317` in the code. This is why we do not need to export it.**

**You have two options**
    
1. To run the application that doesn't have Flask:

   ```shell
   python3 main.py
   ```

    - Run that command as many times as you like to generate trace data. 
    - This application produces trace data reporting to a service called `python-app`. 
    
2. To run the application that contains Flask:

    ```shell
   python3 app.py
   ```
    
    - This application exposes a simple endpoint at `http://127.0.0.1:8080`. You can either open it in a browser and refresh as many times as you like to generate trace data, or you can invoke it by running `curl http://l127.0.0.1:8080`. 
    - This application produces trace data reporting to a service called `python-flask-app`.

## View your data in the New Relic UI

Depending on which app you invoked, you should see either `python-app` or `python-flask-app` under `Services - OpenTelemetry` in your New Relic account. 