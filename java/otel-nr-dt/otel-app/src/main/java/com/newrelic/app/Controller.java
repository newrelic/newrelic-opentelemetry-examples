package com.newrelic.app;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {

  private static final HttpServletRequestExtractor EXTRACTOR = new HttpServletRequestExtractor();

  @Autowired private HttpServletRequest httpServletRequest;

  @GetMapping("/callNewRelicApp")
  public String callNewRelic() throws IOException, InterruptedException {
    // Extract the propagated context from the request. In this example, no context will be
    // extracted from the request since this route initializes the trace.
    var extractedContext = extractContext();

    try (var scope = extractedContext.makeCurrent()) {
      // Start a span in the scope of the extracted context.
      var span = serverSpan("/callNewRelicApp", HttpMethod.GET.name());

      // Send the request and return the response body as the response, and end the span.
      try {
        // Create an HTTP request to the New Relic App's GET /ping route.
        var client = HttpClient.newHttpClient();
        var requestBuilder = HttpRequest.newBuilder().uri(URI.create("http://localhost:8081/ping"));

        // Inject the span's content into the request's headers.
        injectContext(span, requestBuilder);

        // Send the HTTP request, and return the response body
        return client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString()).body();
      } finally {
        span.end();
      }
    }
  }

  @GetMapping("/ping")
  public String ping() {
    // Extract the propagated context from the request.
    var extractedContext = extractContext();

    try (var scope = extractedContext.makeCurrent()) {
      // Start a span in the scope of the extracted context.
      var span = serverSpan("/ping", HttpMethod.GET.name());

      // Return the response, and end the span.
      try {
        return "pong";
      } finally {
        span.end();
      }
    }
  }

  /**
   * Extract the propagated context from the {@link #httpServletRequest}.
   *
   * @return the extracted context
   */
  private Context extractContext() {
    return GlobalOpenTelemetry.getPropagators()
        .getTextMapPropagator()
        .extract(Context.current(), httpServletRequest, EXTRACTOR);
  }

  /**
   * Create a {@link SpanKind#SERVER} span, setting the parent context if available from the {@link
   * #httpServletRequest}.
   *
   * @param path the HTTP path
   * @param method the HTTP method
   * @return the span
   */
  private Span serverSpan(String path, String method) {
    return GlobalOpenTelemetry.getTracer(Controller.class.getName())
        .spanBuilder(path)
        .setSpanKind(SpanKind.SERVER)
        .setAttribute(SemanticAttributes.HTTP_METHOD, method)
        .setAttribute(SemanticAttributes.HTTP_SCHEME, "http")
        .setAttribute(SemanticAttributes.HTTP_HOST, "localhost:8080")
        .setAttribute(SemanticAttributes.HTTP_TARGET, path)
        .startSpan();
  }

  /**
   * Inject the {@code span}'s context into the {@code requestBuilder}.
   *
   * @param span the span
   * @param requestBuilder the request builder
   */
  private static void injectContext(Span span, HttpRequest.Builder requestBuilder) {
    var context = Context.current().with(span);
    GlobalOpenTelemetry.getPropagators()
        .getTextMapPropagator()
        .inject(context, requestBuilder, HttpRequest.Builder::header);
  }

  /**
   * A simple {@link TextMapGetter} implementation that extracts context from {@link
   * HttpServletRequest} headers.
   */
  private static class HttpServletRequestExtractor implements TextMapGetter<HttpServletRequest> {
    @Override
    public Iterable<String> keys(HttpServletRequest carrier) {
      return () -> carrier.getHeaderNames().asIterator();
    }

    @Override
    public String get(HttpServletRequest carrier, String key) {
      return carrier.getHeader(key);
    }
  }
}
