# Uninstrumented .NET demo app

This is the uninstrumented version of the .NET demo application used in the Getting Started Guide - .NET tutorial. Follow along with the steps to instrument it with OpenTelemetry and send traces, metrics, and logs to your New Relic account. 

Requires:

* .NET 6
* [A New Relic account](https://one.newrelic.com/)

To run the uninstrumented dotnet app via the CLI, switch to the `dotnet` directory and run:

```shell
dotnet run
```

To exercise, in a new shell:
```shell
./load-generator.sh
```

To shut down the program, run the following in both shells or terminal tabs: `ctrl + c`. 