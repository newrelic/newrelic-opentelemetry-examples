# Uninstrumented Java demo app

This is the uninstrumented version of the Java demo application used in the Getting Started Guide - Java tutorial. Follow along with the steps to instrument it with OpenTelemetry and send traces, metrics, and logs to your New Relic account. 

Requires:

* Java 17+
* [A New Relic account](https://one.newrelic.com/)

To run the uninstrumented java app via the CLI, switch to the `java` directory and run:

```shell
./gradlew bootRun
```

To exercise, in a new shell:
```shell
./load-generator.sh
```

To shut down the program, run the following in both shells or terminal tabs: `ctrl + c`. 