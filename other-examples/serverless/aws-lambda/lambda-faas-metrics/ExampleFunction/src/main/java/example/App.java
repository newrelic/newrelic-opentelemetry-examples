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
    private static final Tracer tracer = GlobalOpenTelemetry.getTracer("example");

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

        // Thrown rather than returned as a 500, so Lambda itself records the
        // invocation as failed: the Telemetry API reports it, CloudWatch counts
        // it in Errors, and the invocation span ends with an error status.
        if (shouldFail(input.getQueryStringParameters())) {
            throw new RuntimeException("Failing on purpose because the request asked for fail=true");
        }

        return new APIGatewayProxyResponseEvent()
                .withHeaders(headers)
                .withStatusCode(200)
                .withBody(String.format("{\"sleepMs\": %d}", sleepMillis));
    }

    // Runs inside a manually created child span, separate from the
    // auto-instrumented SERVER span, so the example has a span that isn't the
    // invocation. The collector's spanmetrics pipeline must ignore it.
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

    static boolean shouldFail(Map<String, String> queryStringParameters) {
        return queryStringParameters != null && "true".equals(queryStringParameters.get("fail"));
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
