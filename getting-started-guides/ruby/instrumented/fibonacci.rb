# frozen_string_literal: true

module Fibonacci
  class RangeError < StandardError
    MESSAGE = JSON.generate(message: 'n must be 1 <= n <= 90.')

    def initialize(msg = MESSAGE)
      current_span = OpenTelemetry::Trace.current_span
      current_span.status = OpenTelemetry::Trace::Status.error(MESSAGE)
      current_span.record_exception(self)
      super
    end
  end

  def self.calculate(n)
    APP_TRACER.in_span('fibonacci', kind: :internal) do
      first_num, second_num = [0, 1]

      (n - 1).times do
        first_num, second_num = second_num, first_num + second_num
      end

      current_span = OpenTelemetry::Trace.current_span
      current_span.add_attributes({
        'fibonacci.n' => n,
        'fibonacci.result' => first_num
      })

      first_num
    end
  end
end
