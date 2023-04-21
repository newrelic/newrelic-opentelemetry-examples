# frozen_string_literal: true

require 'json'
require 'sinatra/base'
require_relative 'fibonacci'

class MyApp < Sinatra::Base
  set :show_exceptions, :after_handler

  get '/fibonacci' do
    content_type :json
    n = params['n'].to_i

    if n.between?(1, 90)
      result = Fibonacci.calculate(n)
    else
      raise Fibonacci::RangeError
    end

    JSON.generate({n: n, result: result})
  end

  error Fibonacci::RangeError do
    status 400
    env['sinatra.error'].message
  end
end
