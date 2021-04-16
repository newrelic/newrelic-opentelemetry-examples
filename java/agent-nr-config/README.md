This project demonstrates a simple Java application running with the OpenTelemetry Java Agent configured to write data to New Relic. New Relic expects metric data in delta aggregation temporality, whereas the default for OpenTelemetry is cumulative. When running the OpenTelemetry Java Agent, delta aggregation metrics must be configured via the SPI (service provider interface), which enables configuration of some options that are not available via system property or environment variable based configuration. If your app is not using the agent, delta aggregation metrics can be configured directly using the OpenTelemetry SDK.

The project consists of two modules:

1. [application](./application): Contains a simple Spring Boot application configured to run with OpenTelemetry.
2. [otel-initializer](./otel-initializer): Contains SPI configuration code. The contents are packaged as a shadow jar, which the `application` module is configured to use as an initializer jar.
   
The application is configured to export data via OTLP to a collector running at `http://localhost:4317`.

You can adjust where data is exported to, or you can run a collector instance locally via docker by following the [nr-exporter-docker](../../collector/nr-exporter-docker/README.md) example.

After running the collector, run the application via:
```
./gradlew bootRun
```

The `bootRun` command will:
- Download the OpenTelemetry Java agent.
- Build the `otel-initializer` shadow jar.
- Build the application executable jar.
- Run the application executable jar with jvmArgs and environment variables that configure OpenTelemetry. See the `bootRun` task config in `./application/build.gradle` to see the jvmArg environment variable configuration.

The application exposes a simple endpoint at `http://localhost:8080/ping`.

Invoke it via: `curl http://localhost:8000/ping` to generate trace and metric data.

Check your collector logs to confirm data is flowing.