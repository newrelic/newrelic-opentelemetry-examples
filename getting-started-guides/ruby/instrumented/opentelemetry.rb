# frozen_string_literal: true

require 'opentelemetry/sdk'
require 'opentelemetry/exporter/otlp'
require 'opentelemetry/instrumentation/all'

OpenTelemetry::SDK.configure do |c|
  c.service_name = 'getting-started-ruby'
  c.use_all() # enables all instrumentation!
end

MY_APP_TRACER = OpenTelemetry.tracer_provider.tracer('MyAppTracer')
