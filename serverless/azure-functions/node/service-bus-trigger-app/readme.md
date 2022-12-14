# Azure Functions: Azure Service Bus Queue Trigger Example

An example Azure Function App that includes an Azure Service Bus queue trigger Azure Function instrumented using Open Telemetry.

When messages are available to be consumed for the queue name defined in `QueueTriggerExample/function.json`, the function will be invoked and will then make a web request to the URL configured in `EXTERNAL_URL` with details from the message body. The message body details will determine downstream behavior if making a call to the http trigger example function. Distributed Tracing (DT) data will be extracted from the `applicationProperties` and applied to the function span. DT headers will also be attached to the outgoing web request (as well as a span created) by the `http` auto instrumentation.

This example does require a queue to be created either via the web UI or via the Azure VS Code extension + Storage extension.

## Telemetry

This example currently only exports trace data and does not have metric examples.

Traces are buffered using a `BatchSpanProcessor`. The batch span processor will export when either the timeout is hit, the buffer size limit is hit, or the function completes execution via a call to force flush.

This does not yet currently add additional Pub/Sub attributes from the semantic conventions.

## Required/Recommended Configuration

`QueueTriggerExample/function.json` contains the reference to the queue name as well as the setting key for the connection string in the `connection` field. The queue name currently defaults to: `otel-example-queue-items`.

The following settings are recommended to be set in your Azure Function App configuration and local settings (`local.settings.json`) or ENV vars. The first time you debug an example, VS Code will automatically create `local.settings.json` for you and will prompt you to choose/create a storage account. This file is not committed to source control as it may contain secrets.

* `API_KEY` [required]: Must be set to allow data to be sent to new relic. Requires a new relic ingest license key: https://one.newrelic.com/launcher/api-keys-ui.launcher.

* `APPLICATION_QUEUE_STORAGE` [required]: Connection string for the Azure Service Bus Queue to connect to. Should take the form `Endpoint=sb://<some resource name>/;SharedAccessKeyName=<some access key name>;SharedAccessKey=<some access key value>`. For the example to work with the timer trigger function, this needs to be the same as configured there. See [Creating a Queue](#creating-a-queue) and [Getting the Connection String](#getting-the-connection-string).

* `EXTERNAL_URL`: URL to make external calls to. If using with the downstream http trigger example, this needs to point to that available endpoint. This can be grabbed via the Azure extension for the http trigger project by using the command pallet: `Command + Shift + P` -> `Azure Functions: Copy Function Url` once that Azure Function App has been deployed.

## Optional Configuration

* `SERVICE_NAME`: Set to override the name of the service which will map to an entity in New Relic. The service name will default to the Azure Function App name from the `WEBSITE_SITE_NAME` ENV populated by Azure.

* `EXPORT_CONSOLE`: Set to any casing of 'true' or '1' to output Open Telemetry data to the console, via `ConsoleSpanExporter`, for viewing in Azure log streams.

* `EXPORT_URL`: Set to override the URL for OTLP data export.

* `EXPORT_BUFFER_TIMEOUT`: Set to override the default buffer timeout that triggers flushing data prior to function completing invocation. 5000ms is the default in this example.

* `EXPORT_BUFFER_SIZE`: Set to override the default buffer size that triggers flushing data prior to function completing invocation. 500 spans is the default in this example.

## Creating a Queue

You'll need an Azure Service Bus queue for the function to run against. This will need to match the value configured in `QueueTriggerExample/function.json`. The queue name currently defaults to: `otel-example-queue-items`.

You can create a new queue through the UI or via the Azure Storage extension. With the extension, you can click through the appropriate resources/menus or use the command pallet (`Command + Shift + P`) -> `Azure Storage: Create Queue...`.

## Getting the Connection String

To get the connection string, navigate to your service bus resource in the web portal. In the left nav, select `settings` -> `Shared access policies`. Select the policy and then copy the `Primary Connection String`.

Set this value to the `APPLICATION_QUEUE_STORAGE` setting.

## Local Execution

You'll likely need to setup/point-to an Azure Storage account your first time debugging. Multiple files in `.vscode` have been committed to source control to aid in debugging the examples using VS Code, which includes the debug launch settings.

Use `npm run dev` to run locally, without a debugger, with multiple azure functions apps running at the same time. otherwise, they will conflict on port when launching.

## Deployment

### Create a new Azure Function App.

Creating a new Azure Function App can be done through the website or via the Azure extension. Below describes creating through the Azure extension in VS Code.

[`Resources` -> `Function App` -> right-click] OR [command pallet (`Command + Shift + P`)] -> `Azure Functions: Create Function App in Azure....`

Enter a name, a Node.js runtime and a region. This current version of the example has been tested with Node 16 in Azure and Node 18 locally.

### Add Application Settings

Add required application settings as well as any desired optional settings. These will become ENV variables available to the the function.

An easy way to get started is through the Azure plugin. Select the function app -> `Application Settings` -> right-click -> `Upload local settings` OR use the command pallet (`Command + Shift + P`) -> `Azure Functions: Upload Local Settings`... And then modify any different settings you need in Azure. Modification can also be done through the Azure extension.

## Deploy to the Azure Function App

Now in your primary application folder, deploy the application to the Azure Function App.

[right-click] OR [command pallet (`Command + Shift + P`)] -> `Azure Functions: Deploy to Function App...` and choose the Function App you created earlier.
