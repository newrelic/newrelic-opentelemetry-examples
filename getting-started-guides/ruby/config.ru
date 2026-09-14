# frozen_string_literal: true

require 'bundler'

Bundler.require

require './opentelemetry'
require './fibonacci'
require './app'

run App
