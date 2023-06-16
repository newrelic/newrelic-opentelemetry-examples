# Get Started Guide - Python

This is the solution (completely instrumented with OpenTelemetry) for the Python demo application used in the Get Started Guide - Python doc. 

Requires: 
* Python
* [A New Relic account](https://one.newrelic.com/)

To run this demo app via the CLI, follow the steps below. **For steps 2-10, refer to the [table](#commands-table) below to find the commands to use for your environment.** 

1. Switch to the working directory: 
```shell
cd ./getting-started-guides/python/instrumented/
```
2. Install `virtualenv`, if not already done
3. Create a virtual environment for the application
4. Activate virtual environment
5. Upgrade `pip` in virtual environment
6. Install dependencies listed in `requirements.txt` 
7. To send telemetry data to New Relic, set the following environment variables
```shell
OTEL_EXPORTER_OTLP_ENDPOINT="https://otlp.nr-data.net:4317"
OTEL_EXPORTER_OTLP_HEADERS="api-key=[YOUR_INGEST_KEY]"
```
* Make sure to use your [ingest license key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key)
* If your account is based in the EU, set the endpoint to: https://otlp.eu01.nr-data.net:4317
8. Set this environment variable to name the demo app:
```shell
OTEL_SERVICE_NAME=getting-started-python
```
9. Run the app
10. To generate traffic, in a new terminal tab run the load generator
11. To shut down the program, run the following in both shells or terminal tabs: `ctrl + c`. 

## Commands table

| Step | Windows (PowerShell)                     | Linux / MacOS (bash)                      |
|------|------------------------------------------|-------------------------------------------|
| 2    | `pip install virtualenv`                 | `pip3 install virtualenv`                 |
| 3    | `python -m venv venv`                    | `python3 -m venv venv`                    |
| 4    | `.\venv\Scripts\Activate.ps1`            | `source venv/bin/activate`                |
| 5    | `python -m pip install --upgrade pip`    | `python3 -m pip install --upgrade pip`    |
| 6    | `pip install -r requirements.txt`        | `pip install -r requirements.txt`         |
| 7-8  | Set OTEL environment variables `$Env:`   | Set OTEL environment variables `export`   |
| 9    | `python app.py`                          | `python3 app.py`                          |
| 10   | `.\load-generator.ps1`                   | `./load-generator.sh`                     |
