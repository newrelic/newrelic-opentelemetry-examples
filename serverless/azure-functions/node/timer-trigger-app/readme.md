# Azure Functions: Timer Trigger Example

An example Azure Function App that includes a timer trigger Azure Function instrumented using Open Telemetry.

Once every two minutes, the function will be invoked and add a new message to an Azure Service Bus queue. This message will contain Distributed Tracing (DT) metadata in the `applicationProperties`. If you run the Azure Service Bus queue example, the DT data will be extracted to link the two functions and the message body will be used for further downstream actions.

## Telemetry

This example currently only exports trace data and does not have metric examples.

Traces are buffered using a `BatchSpanProcessor`. The batch span processor will export when either the timeout is hit, the buffer size limit is hit, or the function completes execution via a call to force flush.

## Required/Recommended Configuration

The following settings are recommended to be set in your Azure Function App configuration and local settings (`local.settings.json`) or ENV vars. The first time you debug an example, VS Code will automatically create `local.settings.json` for you and will prompt you to choose/create a storage account. This file is not committed to source control as it may contain secrets.

* `API_KEY` [required]: Must be set to allow data to be sent to new relic. Requires a new relic ingest license key: https://one.newrelic.com/launcher/api-keys-ui.launcher.

* `APPLICATION_QUEUE_STORAGE`: Connection string for the Azure Service Bus Queue to connect to. The function will skip sending messages if this is not set. Should take the form `Endpoint=sb://<some resource name>/;SharedAccessKeyName=<some access key name>;SharedAccessKey=<some access key value>`. For the downstream queue example to work, this needs to be the same queue as configured for that example.

## Optional Configuration

* `SERVICE_NAME`: Set to override the name of the service which will map to an entity in New Relic. The service name will default to the Azure Function App name from the `WEBSITE_SITE_NAME` ENV populated by Azure.

* `EXPORT_CONSOLE`: Set to any casing of 'true' or '1' to output Open Telemetry data to the console, via `ConsoleSpanExporter`, for viewing in Azure log streams.

* `EXPORT_URL`: Set to override the URL for OTLP data export.

* `EXPORT_BUFFER_TIMEOUT`: Set to override the default buffer timeout that triggers flushing data prior to function completing invocation. 5000ms is the default in this example.

* `EXPORT_BUFFER_SIZE`: Set to override the default buffer size that triggers flushing data prior to function completing invocation. 500 spans is the default in this example.

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
