# frozen_string_literal: true

module Fibonacci
  class RangeError < StandardError
    MESSAGE = JSON.generate(message: 'n must be 1 <= n <= 90.')

    def initialize(msg = MESSAGE)
      super
    end
  end

  def self.calculate(n)
    first_num, second_num = [0, 1]

    (n - 1).times do
      first_num, second_num = second_num, first_num + second_num
    end

    first_num
  end
end
