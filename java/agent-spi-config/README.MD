This project demonstrates how to run a Java application with the OpenTelemetry Agent configured
via SPI (service provider interface), which enables configuration of some options that are not
available via system property or environment variable based configuration.

It consists of two modules:

1. `application`: Contains a simple Spring Boot application configured to run with OpenTelemetry.
2. `otel-initializer`: Contains SPI configuration code. The contents are packaged as a shadow jar, 
which the `application` module is configured to use as an initializer jar.
   
The application is configured to export data via OTLP to a collector running at `http://localhost:4317`.

You can adjust where data is exported to, or you can run a collector instance locally via docker.
The `./collector/docker-compose.yaml` file contains a simple collector instance configured to
receive OTLP data and write it to logs. Run it via:
```
docker-compose -f ./collector/docker-compose.yaml up
```

In a separate shell, run the application via:
```
./gradlew bootRun
```

The `bootRun` command will:
- Download the OpenTelemetry Java agent.
- Build the `otel-initializer` shadow jar.
- Build the application executable jar.
- Run the application executable jar with jvmArgs and environment variables that configure 
  OpenTelemetry. See the `bootRun` task config in `./application/build.gradle` to see the jvmArg
  environment variable configuration.

The application exposes a simple endpoint at `http://localhost:8080/ping`.

Invoke it via: `curl http://localhost:8000/ping` to generate trace and metric data.

Head back shell running the collector to view the data. 

