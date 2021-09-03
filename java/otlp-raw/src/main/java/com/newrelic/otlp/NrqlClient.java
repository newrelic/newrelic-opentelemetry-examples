package com.newrelic.otlp;

import static com.newrelic.otlp.Common.deserializeFromJson;
import static com.newrelic.otlp.Common.serializeToJson;

import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

public class NrqlClient {

  private final OkHttpClient client;
  private final String graphQlEndpoint;
  private final String accountId;
  private final String apiKey;

  NrqlClient(String graphQlEndpoint, String accountId, String apiKey) {
    this.graphQlEndpoint = graphQlEndpoint;
    this.client = new OkHttpClient();
    this.accountId = accountId;
    this.apiKey = apiKey;
  }

  List<Map<String, Object>> postNrql(String nrqlQuery) {
    String nerdGraphQuery = nerdGraphNrqlQuery(nrqlQuery);
    return extractResults(postGraphQl(nerdGraphQuery));
  }

  private Map<String, Object> postGraphQl(String nerdGraphQuery) {
    var requestBodyJson = serializeToJson(Map.of("query", nerdGraphQuery));
    var request =
        new Request.Builder()
            .url(graphQlEndpoint)
            .post(RequestBody.create(requestBodyJson, MediaType.parse("application/json")))
            .header("Api-Key", apiKey)
            .build();

    try (var response = client.newCall(request).execute()) {
      String responseBodyStr = "";
      ResponseBody responseBody = response.body();
      if (responseBody != null) {
        responseBodyStr = responseBody.string();
      }
      if (response.code() == 200) {
        return deserializeFromJson(responseBodyStr, new TypeReference<>() {});
      }
      throw new IllegalStateException(
          "Request failed with code " + response.code() + ", response body: " + responseBodyStr);
    } catch (IOException e) {
      throw new IllegalStateException("Request to execute request.", e);
    }
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> extractResults(Map<String, Object> responseMap) {
    var dataMap = (Map<String, Object>) responseMap.get("data");
    var actorMap = (Map<String, Object>) dataMap.get("actor");
    var accountMap = (Map<String, Object>) actorMap.get("account");
    var nrqlMap = (Map<String, Object>) accountMap.get("nrql");
    return (List<Map<String, Object>>) nrqlMap.get("results");
  }

  private String nerdGraphNrqlQuery(String nrqlQuery) {
    var nrql = String.format("nrql(query: \"%s\") { results }", nrqlQuery);
    var actor = String.format("account(id: %s){ %s }", accountId, nrql);
    return String.format("{ actor { %s } }", actor);
  }
}
