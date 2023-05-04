# frozen_string_literal: true

module Fibonacci
  class RangeError < StandardError
    MESSAGE = <<~MSG
      Please select a number between 1 and 90.
      Example URL: http://localhost:9292/fibonacci?n=1
    MSG

    def initialize(msg = MESSAGE)
      current_span = OpenTelemetry::Trace.current_span
      current_span.status = OpenTelemetry::Trace::Status.error(MESSAGE)
      current_span.record_exception(self)
      super
    end
  end

  def self.calculate(n)
    MY_APP_TRACER.in_span('Fibonacci.calculate') do
      first_num, second_num = [0, 1]

      (n - 1).times do
        first_num, second_num = second_num, first_num + second_num
      end

      current_span = OpenTelemetry::Trace.current_span
      current_span.add_attributes({
        'n' => n,
        'result' => first_num
      })

      first_num
    end
  end
end
