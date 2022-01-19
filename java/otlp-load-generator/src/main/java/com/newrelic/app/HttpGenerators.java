package com.newrelic.app;

import static com.newrelic.app.Utils.randomFromList;
import static com.newrelic.app.Utils.runInSpanScope;
import static com.newrelic.app.Utils.safeSleep;
import static java.util.stream.Collectors.joining;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;

public class HttpGenerators {

  private static final Random RANDOM = new Random();
  private static final List<String> PATH_PATTERNS =
      List.of("/user/${id}", "/role/${id}", "/permission/${id}");
  private static final List<String> METHODS = List.of("GET", "POST", "PUT", "DELETE");
  private static final List<Long> STATUS_CODES = List.of(200L, 201L, 202L, 400L, 404L, 500L);
  private static final List<String> PEER_IPS = List.of("127.0.0.1", "127.0.0.2");

  private HttpGenerators() {}

  static class ServerGenerator implements Runnable {

    private final List<Runnable> outboundGenerators;
    private final Tracer tracer;
    private final LongHistogram durationRecorder;
    private final AtomicLong runCount = new AtomicLong();

    ServerGenerator(List<Runnable> outboundGenerators) {
      this.outboundGenerators = outboundGenerators;
      this.tracer = GlobalOpenTelemetry.getTracer(HttpGenerators.class.getName());
      Meter meter = GlobalOpenTelemetry.getMeter(HttpGenerators.class.getName());
      this.durationRecorder = meter.histogramBuilder("http.server.duration").ofLongs().build();
    }

    @Override
    public void run() {
      var rr = randomRequestResponse();

      var span =
          tracer
              .spanBuilder(rr.path(false))
              .setAttribute(SemanticAttributes.HTTP_METHOD, rr.method)
              .setAttribute(SemanticAttributes.HTTP_FLAVOR, rr.flavor)
              .setAttribute(SemanticAttributes.HTTP_TARGET, rr.target(true))
              .setAttribute(SemanticAttributes.HTTP_HOST, rr.host())
              .setAttribute(SemanticAttributes.HTTP_SERVER_NAME, rr.serverName)
              .setAttribute(SemanticAttributes.NET_HOST_PORT, rr.port)
              .setAttribute(SemanticAttributes.HTTP_SCHEME, rr.scheme)
              .setAttribute(SemanticAttributes.HTTP_ROUTE, rr.pathPattern)
              .setAttribute(SemanticAttributes.HTTP_STATUS_CODE, rr.statusCode)
              .setAttribute(SemanticAttributes.HTTP_REQUEST_CONTENT_LENGTH, rr.requestContentLength)
              .setAttribute(
                  SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH, rr.responseContentLength)
              .setSpanKind(SpanKind.SERVER)
              .setNoParent()
              .startSpan();

      runInSpanScope(
          span,
          () -> {
            durationRecorder.record(
                rr.duration,
                Attributes.builder()
                    .put(SemanticAttributes.HTTP_METHOD.getKey(), rr.method)
                    .put(SemanticAttributes.HTTP_HOST.getKey(), rr.host())
                    .put(SemanticAttributes.HTTP_SCHEME.getKey(), rr.scheme)
                    .put(
                        SemanticAttributes.HTTP_STATUS_CODE.getKey(), String.valueOf(rr.statusCode))
                    .put(SemanticAttributes.HTTP_FLAVOR.getKey(), rr.flavor)
                    .put(SemanticAttributes.HTTP_SERVER_NAME.getKey(), rr.serverName)
                    .put(SemanticAttributes.HTTP_TARGET.getKey(), rr.target(false))
                    .build());

            safeSleep(rr.duration);
            randomFromList(outboundGenerators).run();
            span.setStatus(rr.statusCode < 400 ? StatusCode.UNSET : StatusCode.ERROR);

            long count = runCount.incrementAndGet();
            if (count % 10 == 0) {
              System.out.printf("%s http server spans have been produced.%n", count);
            }
          });
    }
  }

  static class ClientGenerator implements Runnable {

    private final Tracer tracer;
    private final AtomicLong runCount = new AtomicLong();

    ClientGenerator() {
      this.tracer = GlobalOpenTelemetry.getTracer(HttpGenerators.class.getName());
    }

    @Override
    public void run() {
      var rr = randomRequestResponse();

      var span =
          tracer
              .spanBuilder(rr.path(false))
              .setAttribute(SemanticAttributes.HTTP_METHOD, rr.method)
              .setAttribute(SemanticAttributes.HTTP_FLAVOR, rr.flavor)
              .setAttribute(SemanticAttributes.HTTP_URL, rr.url(true))
              .setAttribute(SemanticAttributes.HTTP_STATUS_CODE, rr.statusCode)
              .setAttribute(SemanticAttributes.NET_PEER_IP, rr.peerIp)
              .setSpanKind(SpanKind.CLIENT)
              .startSpan();

      runInSpanScope(
          span,
          () -> {
            safeSleep(rr.duration);
            span.setStatus(rr.statusCode < 400 ? StatusCode.UNSET : StatusCode.ERROR);

            long count = runCount.incrementAndGet();
            if (count % 10 == 0) {
              System.out.printf("%s http client spans have been produced.%n", count);
            }
          });
    }
  }

  private static RequestResponse randomRequestResponse() {
    var rr = new RequestResponse();

    rr.method = randomFromList(METHODS);
    rr.scheme = "http";
    rr.serverName = "localhost";
    rr.port = 8080L;
    rr.pathPattern = randomFromList(PATH_PATTERNS);
    rr.pathArgs = Map.of("id", UUID.randomUUID().toString().substring(0, 4));
    rr.queryParams = Map.of();
    rr.fragment = "";
    rr.flavor = "1.1";
    rr.requestContentLength = rr.method.equals("GET") ? 0L : RANDOM.nextInt(1000);
    rr.peerIp = randomFromList(PEER_IPS);

    rr.statusCode = randomFromList(STATUS_CODES);
    rr.responseContentLength =
        rr.method.equals("POST") || rr.method.equals("DELETE") ? 0L : RANDOM.nextInt(1000);
    rr.duration = RANDOM.nextInt(1000);

    return rr;
  }

  private static class RequestResponse {
    // Request fields
    private String method; // GET
    private String scheme; // https
    private String serverName; // localhost
    private long port; // 8080
    private String pathPattern; // /users/${id}
    private Map<String, Object> pathArgs; // Map.of("id", "123")
    private Map<String, String> queryParams; // Map.of("foo", "bar")
    private String fragment; // foo
    private String flavor; // 1.1
    private long requestContentLength; // 1024
    private String peerIp;

    // Response fields
    private long statusCode; // 200
    private long responseContentLength; // 2048
    private long duration; // 1000

    private String host() {
      return String.format("%s:%s", serverName, port);
    }

    private String path(boolean parameterized) {
      return parameterized ? new StringSubstitutor(pathArgs).replace(pathPattern) : pathPattern;
    }

    private String queryString(boolean parameterized) {
      if (queryParams.isEmpty()) {
        return "";
      }
      return queryParams.entrySet().stream()
          .map(
              entry ->
                  String.format("%s=%s", entry.getKey(), parameterized ? entry.getValue() : "{}"))
          .collect(joining("&", "?", ""));
    }

    private String target(boolean parameterized) {
      var target = new StringBuilder(path(parameterized)).append(queryString(parameterized));
      if (!StringUtils.isBlank(fragment)) {
        target.append("#").append(fragment);
      }
      return target.toString();
    }

    private String url(boolean parameterized) {
      return scheme + "://" + host() + target(parameterized);
    }
  }
}
