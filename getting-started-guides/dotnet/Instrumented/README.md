# Getting Started Guide - .NET

This is the solution (completely instrumented with OpenTelemetry) for the .NET demo application used in the Getting Started Guide - .NET tutorial. 

Requires:

* .NET 6
* [A New Relic account](https://one.newrelic.com/)

To run this demo app via the CLI:

1. Switch to the `dotnet` directory
2. Export the following environment variables (replace `<your_license_key>` with your [New Relic ingest license key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key)):
* export OTEL_EXPORTER_OTLP_ENDPOINT=https://otlp.nr-data.net:4317
* export OTEL_EXPORTER_OTLP_HEADERS=api-key=<your_license_key>
3. Run the following command

```shell
dotnet run
```

4. To generate traffic, in a new terminal tab run the following command
```shell
./load-generator.sh
```

5. To shut down the program, run the following in both shells or terminal tabs: `ctrl + c`. 
