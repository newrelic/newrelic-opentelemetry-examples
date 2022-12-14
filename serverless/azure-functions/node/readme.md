# Azure Functions: Node.js Examples

These examples contain Azure Function applications with a variety of trigger types instrumented using Open Telemetry. The applications will send their data to New Relic via OTLP (or you could configure a collector and from there to NR).

To limit bouncing around, all setup and instrumentation code has been included within each example. Each example contains enough information to power the NR One UI but does not necessarily contain all possible to collect data. Additional information to complete the semantic conventions may be desired. The examples are also currently limited to span data.

## Applications

* [Timer trigger](./timer-trigger-app/readme.md): An Azure Function App that contains a timer triggered function which writes a message to an Azure Service Bus queue every two minutes.
* [Service Bus queue trigger](./service-bus-trigger-app/readme.md): An Azure Function App that contains a queue triggered function that makes requests to an external URL with the contents of the consumed message body.
* [HTTP trigger](./http-trigger-app/readme.md): An Azure Function App that contains an HTTP triggered function that makes an external call and then sets an appropriate status, or throws an error, depending on the incoming request params or body.

The timer, queue and HTTP functions have been setup to work together to show Distributed Tracing (DT) connectivity across entities. If all are running, and configured correctly, you will be able to see each connected with NR One.

The flow of execution looks like: Timer trigger -> Queue trigger -> HTTP trigger.

Once every two minutes, the timer trigger function will create a message, apply DT data and publish the message to an Azure Service Bus queue. If configured to the same queue, the queue trigger function will consume these messages by applying the DT data and then making a web request to a URL defined in `EXTERNAL_URL`. If `EXTERNAL_URL` points to the location of the http trigger function, the function will process the incoming request and then set the appropriate status or error out based on the data passed to it.

Please see the readme documentation for each on more information on how to configure and run each example.

## A Note on Service Name

Service name currently controls what gets a unique entity within NR One. This results in the data being grouped all within the single entity and gets displayed in "Services - OpenTelemetry".

In these examples, the service name is set to the name of the Azure Function App as it is easy to identify and auto-name using the available environment information.

You may have multiple Azure functions in a single Azure Function App. In this case, you may not want all of the functions grouped in a single entity. If you do, that's fine. The individual functions span will show up uniquely to separate each and you'll get a summary view across all. You very well might want each function separate, though. In this case, I would recommend setting the service name to follow the same format as the function name attribute which is the Azure Function App name + function name. For example: `MyFunctionApp/MyFunctionName`.
