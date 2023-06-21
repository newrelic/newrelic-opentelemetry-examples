# frozen_string_literal: true

require 'bundler'

Bundler.require

require './fibonacci'
require './app'

Rack::Handler.default.run(App, :Port => 8080)
