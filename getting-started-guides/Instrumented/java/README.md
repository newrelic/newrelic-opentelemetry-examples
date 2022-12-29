# Getting Started Guide - Java

This is the solution (completely instrumented with OpenTelemetry) for the Java demo application used in the Getting Started Guide - Java doc. 

Requires:

* Java 17+
* [A New Relic account](https://one.newrelic.com/)

To run this demo app via the CLI:

1. Switch to the `java` directory
2. Export the following environment variable (replace `<your_license_key>` with your [New Relic ingest license key](https://docs.newrelic.com/docs/apis/intro-apis/new-relic-api-keys/#license-key)):
```
export newrelicLicenseKey=<your_license_key>
```

Optionally, you can also set the following to a different endpoint; this app has been configured to export data to this endpoint by default. 
```
export newrelicOtlpEndpoint=https://otlp.nr-data.net:4317
```

3. Run the following command

```shell
./gradlew bootRun
```

4. To generate traffic, in a new terminal tab run the following command
```shell
./load-generator.sh
```

5. To shut down the program, run the following in both shells or terminal tabs: `ctrl + c`. 