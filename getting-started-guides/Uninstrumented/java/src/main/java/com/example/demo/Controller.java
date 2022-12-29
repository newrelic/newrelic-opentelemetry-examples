package com.example.demo;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class Controller {

    @GetMapping(value = "/fibonacci")
    public Map<String, Object> getFibonacci(@RequestParam(required = true, name = "n") long n) {
        return Map.of("n", n, "result", fibonacci(n));
    }

    /**
     * Compute the fibonacci number for {@code n}.
     *
     * @param n must be >=1 and <= 90.
     */
    private long fibonacci(long n) {
        try {
            if (n < 1 || n > 90) {
                throw new IllegalArgumentException("n must be 1 <= n <= 90.");
            }

            // Base cases
            if (n == 1) {
                return 1;
            }
            if (n == 2) {
                return 1;
            }

            long lastLast = 1;
            long last = 2;
            for (long i = 4; i <= n; i++) {
                long cur = last + lastLast;
                lastLast = last;
                last = cur;
            }
            return last;
        } catch (IllegalArgumentException e) {
            throw e;
        }
    }

    @ControllerAdvice
    private static class ErrorHandler {

        @ExceptionHandler({
                IllegalArgumentException.class,
                MissingServletRequestParameterException.class,
                HttpRequestMethodNotSupportedException.class
        })
        public ResponseEntity<Object> handleException(Exception e) {
            return new ResponseEntity<>(Map.of("message", e.getMessage()), HttpStatus.BAD_REQUEST);
        }

    }
}
