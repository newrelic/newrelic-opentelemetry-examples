package example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Handler for requests to Lambda function.
 */
public class App implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    static final long MIN_SLEEP_MILLIS = 10;
    static final long MAX_SLEEP_MILLIS = 800;

    private static final Logger log = LoggerFactory.getLogger(App.class);
    private static final Tracer tracer = GlobalOpenTelemetry.getTracer("otel-faas-spanmetrics-poc");

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent input, Context context) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

        long sleepMillis;
        try {
            sleepMillis = resolveSleepMillis(input.getQueryStringParameters(), new Random());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid sleepMs parameter", e);
            return new APIGatewayProxyResponseEvent()
                    .withHeaders(headers)
                    .withStatusCode(400)
                    .withBody(String.format("{\"error\": \"%s\"}", e.getMessage()));
        }

        doWork(sleepMillis);

        return new APIGatewayProxyResponseEvent()
                .withHeaders(headers)
                .withStatusCode(200)
                .withBody(String.format("{\"sleepMs\": %d}", sleepMillis));
    }

    // Runs inside a manually-created child span, separate from the
    // auto-instrumented SERVER span, so the example has a non-invocation
    // span to prove the collector's SERVER-only filter actually filters.
    private void doWork(long sleepMillis) {
        Span childSpan = tracer.spanBuilder("do-work").startSpan();
        try (Scope scope = childSpan.makeCurrent()) {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            childSpan.end();
        }
    }

    static long resolveSleepMillis(Map<String, String> queryStringParameters, Random random) {
        if (queryStringParameters != null) {
            String raw = queryStringParameters.get("sleepMs");
            if (raw != null) {
                long parsed;
                try {
                    parsed = Long.parseLong(raw.trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("sleepMs must be a non-negative integer, got: " + raw, e);
                }
                if (parsed < 0) {
                    throw new IllegalArgumentException("sleepMs must be a non-negative integer, got: " + raw);
                }
                return parsed;
            }
        }
        int range = (int) (MAX_SLEEP_MILLIS - MIN_SLEEP_MILLIS + 1);
        return MIN_SLEEP_MILLIS + random.nextInt(range);
    }
}
