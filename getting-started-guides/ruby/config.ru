# frozen_string_literal: true

require 'bundler'

Bundler.require

require './opentelemetry'
require './fibonacci'
require './app'

Rack::Handler.default.run(App, :Host => "0.0.0.0", :Port => 8080)
