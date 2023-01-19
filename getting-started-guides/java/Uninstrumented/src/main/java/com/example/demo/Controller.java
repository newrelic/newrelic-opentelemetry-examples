package com.example.demo;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;

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
    if (n < 1 || n > 90) {
      throw new IllegalArgumentException("n must be 1 <= n <= 90.");
    }

    long result = 1;
    if (n > 2) {
      long a = 0;
      long b = 1;

      for (long i = 1; i < n; i++) {
        result = a + b;
        a = b;
        b = result;
      }
    }

    return result;
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
