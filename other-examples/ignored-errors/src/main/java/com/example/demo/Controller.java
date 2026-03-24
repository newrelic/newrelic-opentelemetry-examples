package com.example.demo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class Controller {
  @GetMapping(value = "/httpStatusCode/{statusCode}")
  public ResponseEntity<String> httpStatusCode(@PathVariable("statusCode") int statusCode) {
    return ResponseEntity.status(statusCode).body(String.format("HTTP status code received: %d", statusCode));
  }
}
