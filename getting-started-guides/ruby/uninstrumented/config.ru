# frozen_string_literal: true

require 'bundler'

Bundler.require

require 'dotenv/load'
require './fibonacci'
require './myapp'

Rack::Handler.default.run(MyApp, :Port => 8080)
