# Getting Started Guide - Java

This is the solution (completely instrumented with OpenTelemetry) for the Java demo application used in the Getting Started Guide - Java tutorial. 

Requires:

* Java 17+
* [A New Relic account](https://one.newrelic.com/)

To run this demo app via the CLI:

1. From this directory, set these two environment variables to send data to your New Relic account:
```
export OTEL_EXPORTER_OTLP_HEADERS=api-key=<your_license_key>
export OTEL_EXPORTER_OTLP_ENDPOINT=https://otlp.nr-data.net:4317
```
* Make sure to use your [ingest license key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key)
* If your account is based in the EU, set the endpoint to: https://otlp.eu01.nr-data.net:4317

2. Set this environment variable to name the demo app:
```
export OTEL_SERVICE_NAME=getting-started-java
```

3. Run the following command

```shell
./gradlew bootRun
```

4. To generate traffic, in a new terminal tab run the following command:
```shell
./load-generator.sh
```

Alternatively, hit the application endpoint in the browser, and modify the `[input]` field to generate both successful computations as well as errors: http://localhost:8080/fibonacci?n=[input]

5. To shut down the program, run the following in both terminal tabs: `ctrl + c`. 
