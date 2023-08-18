# frozen_string_literal: true

module Fibonacci
  MIN = 1
  MAX = 90

  class RangeError < StandardError
    MESSAGE = JSON.generate(message: 'n must be 1 <= n <= 90.')

    def initialize(msg = MESSAGE)
      super(msg)
    end
  end

  def self.calculate(n)
    APP_TRACER.in_span('fibonacci', kind: :internal) do
      raise RangeError, RangeError::MESSAGE unless n.between?(MIN, MAX)

      first_num, second_num = [0, 1]

      (n - 1).times do
        first_num, second_num = second_num, first_num + second_num
      end

      current_span = OpenTelemetry::Trace.current_span
      current_span.add_attributes({
        'fibonacci.n' => n,
        'fibonacci.result' => first_num,
      })

      first_num
    end
  end
end
