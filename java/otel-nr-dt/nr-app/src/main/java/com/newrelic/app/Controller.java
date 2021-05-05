package com.newrelic.app;

import com.newrelic.api.agent.ConcurrentHashMapHeaders;
import com.newrelic.api.agent.HeaderType;
import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.TransportType;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller {

  @Autowired private HttpServletRequest httpServletRequest;

  @GetMapping("/callOtelApp")
  public String callOtel() throws IOException, InterruptedException {
    // Create an HTTP request to the OpenTelemetry App's GET /ping route.
    var client = HttpClient.newHttpClient();
    var requestBuilder = HttpRequest.newBuilder().uri(URI.create("http://localhost:8080/ping"));

    // Grab distributed trace headers from the New Relic API, and inject them into the HTTP request.
    // request's headers
    var distributedTraceHeaders = ConcurrentHashMapHeaders.build(HeaderType.MESSAGE);
    NewRelic.getAgent().getTransaction().insertDistributedTraceHeaders(distributedTraceHeaders);
    distributedTraceHeaders
        .getMapCopy()
        .forEach((name, values) -> values.forEach(value -> requestBuilder.header(name, value)));

    // Send the request and return the response body as the response.
    return client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString()).body();
  }

  @GetMapping("/ping")
  public String ping() {
    // Extract distributed trace headers from the incoming request's headers.
    // NOTE: This illustrates how to extract context, but isn't necessary in this case because the
    // New Relic agent automatically supports extracting W3C trace context.
    var distributedTraceHeaders = ConcurrentHashMapHeaders.build(HeaderType.MESSAGE);
    for (var iter = httpServletRequest.getHeaderNames().asIterator(); iter.hasNext(); ) {
      var header = iter.next();
      distributedTraceHeaders.addHeader(header, httpServletRequest.getHeader(header));
    }

    // Pass the distributed trace headers to the New Relic API.
    NewRelic.getAgent()
        .getTransaction()
        .acceptDistributedTraceHeaders(TransportType.HTTP, distributedTraceHeaders);

    // Return the response
    return "pong";
  }
}
