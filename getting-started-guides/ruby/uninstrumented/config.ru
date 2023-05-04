# frozen_string_literal: true

require 'bundler'

Bundler.require

require 'dotenv/load'
require './fibonacci'
require './myapp'

run MyApp
