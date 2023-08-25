# frozen_string_literal: true

require 'json'
require 'sinatra/base'
require_relative 'fibonacci'

class App < Sinatra::Base
  set :show_exceptions, :after_handler

  get '/' do
    redirect '/fibonacci'
  end

  get '/fibonacci' do
    content_type :json

    n = params['n'].to_i

    result = Fibonacci.calculate(n)

    JSON.generate({ n: n, result: result })
  end

  error Fibonacci::RangeError do
    status 400
    OpenTelemetry::Trace.current_span.record_exception(env['sinatra.error'])
    env['sinatra.error'].message
  end
end
