package example;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppTest {

    @Test
    void explicitSleepMsIsUsedVerbatim() {
        Map<String, String> params = new HashMap<>();
        params.put("sleepMs", "250");

        long result = App.resolveSleepMillis(params, new Random());

        assertEquals(250L, result);
    }

    @Test
    void missingSleepMsAtBottomOfRangeFallsBackToMin() {
        Random fixed = new Random() {
            @Override
            public int nextInt(int bound) {
                return 0;
            }
        };

        long result = App.resolveSleepMillis(new HashMap<>(), fixed);

        assertEquals(App.MIN_SLEEP_MILLIS, result);
    }

    @Test
    void missingSleepMsAtTopOfRangeFallsBackToMax() {
        Random fixed = new Random() {
            @Override
            public int nextInt(int bound) {
                return bound - 1;
            }
        };

        long result = App.resolveSleepMillis(new HashMap<>(), fixed);

        assertEquals(App.MAX_SLEEP_MILLIS, result);
    }

    @Test
    void nullQueryStringParametersFallsBackToRandomWithinRange() {
        long result = App.resolveSleepMillis(null, new Random());

        assertTrue(result >= App.MIN_SLEEP_MILLIS && result <= App.MAX_SLEEP_MILLIS);
    }

    @Test
    void negativeSleepMsThrows() {
        Map<String, String> params = new HashMap<>();
        params.put("sleepMs", "-5");

        assertThrows(IllegalArgumentException.class,
                () -> App.resolveSleepMillis(params, new Random()));
    }

    @Test
    void nonNumericSleepMsThrows() {
        Map<String, String> params = new HashMap<>();
        params.put("sleepMs", "soon");

        assertThrows(IllegalArgumentException.class,
                () -> App.resolveSleepMillis(params, new Random()));
    }

    @Test
    void handleRequestReturns400ForInvalidSleepMs() {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        Map<String, String> params = new HashMap<>();
        params.put("sleepMs", "not-a-number");
        event.setQueryStringParameters(params);

        APIGatewayProxyResponseEvent response = new App().handleRequest(event, null);

        assertEquals(400, response.getStatusCode());
    }

    @Test
    void handleRequestReturns200AndEchoesSleepMs() {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        Map<String, String> params = new HashMap<>();
        params.put("sleepMs", "15");
        event.setQueryStringParameters(params);

        APIGatewayProxyResponseEvent response = new App().handleRequest(event, null);

        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("\"sleepMs\": 15"));
    }
}
