#!/usr/bin/env ruby
# frozen_string_literal: true

require 'net/http'
require 'uri'

def call_app(endpoint)
  inputs = [5, 283, 10, 90, 0]

  inputs.each do |input|
    uri = URI("#{endpoint}/fibonacci?n=#{input}")
    puts "GET #{uri}"
    res = Net::HTTP.get_response(uri)
    puts res.code
  end
end

while true
  puts 'Calling getting-started-ruby'
  call_app('http://localhost:9292')
  sleep(2)
end
