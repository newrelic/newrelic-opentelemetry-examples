# Getting Started Guides

This repo holds the source code for the demo applications used in the Getting Started Guides. The demo app, available in a variety of languages, is a simple fibonacci app that caluclates the nth number in the fibonacci sequence, but only accepts an n value between 1-90. Input outside of that range is considered invalid and an exception will be thrown. 

In the Getting Started Guides, you will learn about the platform capabilities New Relic offers for OTLP data and how to configure the OpenTelemetry SDK to optimize your observability experience in New Relic. This guide is designed so that you can choose one of the following paths:

* Learn how to add OpenTelemetry instrumentation by using the uninstrumented version of the demo app and following the tutorial
* Inspect the code and apply relevant sections to your own apps

Each language directory contains the following sub-directories:

* Instrumented

  The instrumented versions of the demo app are instrumented with OpenTelemetry and configured according to our [best practices](https://docs.newrelic.com/docs/more-integrations/open-source-telemetry-integrations/opentelemetry/best-practices/opentelemetry-best-practices-overview/) to generate and export metrics, logs, and traces. 

  Run the app of your preferred language following the README steps to export data to your New Relic account, or view a screenshot tour of New Relic features for OpenTelemetry data [here](https://developer.newrelic.com/collect-data/opentelemetry-manual/view/). Then, check out the guide for the relevant language to learn about the custom instrumentation and SDK configuration in the demo app that lights up these features. 

* Uninstrumented
  
  Contains the uninstrumented versions of the apps. Use these to follow along with the guide and learn how to add OpenTelemetry instrumentation. Then, check out the guide for the relevant language to learn about the custom instrumentation and SDK configuration in the demo app that lights up these features in your New Relic account. 