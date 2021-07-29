package com.newrelic.shared;

import static com.newrelic.shared.OtlpUtil.retryServiceConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.Gson;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OtlpUtilTest {

  @Test
  void testRetryServiceConfig() throws URISyntaxException, IOException {
    var json =
        Files.readString(
            Paths.get(
                this.getClass()
                    .getClassLoader()
                    .getResource("otlp_retry_service_config.json")
                    .toURI()));
    Map<?, ?> expectedServiceConfig = new Gson().fromJson(json, Map.class);
    Map<String, ?> actualServiceConfig = retryServiceConfig();
    assertEquals(expectedServiceConfig, actualServiceConfig);
  }
}
