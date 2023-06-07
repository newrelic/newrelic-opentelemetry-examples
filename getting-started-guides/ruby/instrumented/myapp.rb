# frozen_string_literal: true

require 'json'
require 'sinatra/base'
require_relative 'fibonacci'

class MyApp < Sinatra::Base
  MIN = 1
  MAX = 90

  set :show_exceptions, :after_handler

  get '/' do
    redirect '/fibonacci'
  end

  get '/fibonacci' do
    content_type :json

    n = params['n'].nil? ? rand(MIN..MAX) : params['n'].to_i

    if n.between?(MIN, MAX)
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
