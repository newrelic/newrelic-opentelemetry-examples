# frozen_string_literal: true

require 'opentelemetry/sdk'
require 'opentelemetry-metrics-sdk'
require 'opentelemetry-exporter-otlp-metrics'
require 'opentelemetry/exporter/otlp'
# require 'opentelemetry/instrumentation/all'

module MetricsPatch
  def metrics_configuration_hook
    OpenTelemetry.meter_provider = OpenTelemetry::SDK::Metrics::MeterProvider.new(resource: @resource)
    OpenTelemetry.meter_provider
      .add_metric_reader(
        OpenTelemetry::SDK::Metrics::Export::PeriodicMetricReader.new(
          exporter: OpenTelemetry::Exporter::OTLP::Metrics::MetricsExporter.new(),
          export_interval_millis: 3000,
          export_timeout_millis: 10000))
  end
end

OpenTelemetry::SDK::Configurator.prepend(MetricsPatch)

OpenTelemetry::SDK.configure do |c|
  # c.use_all() # enables all instrumentation!
  c.use 'OpenTelemetry::Instrumentation::Rack', { send_metrics: true }
  c.use 'OpenTelemetry::Instrumentation::Sinatra'
  # c.use 'OpenTelemetry::Instrumentation::Net::HTTP'
end

APP_TRACER = OpenTelemetry.tracer_provider.tracer('AppTracer')
