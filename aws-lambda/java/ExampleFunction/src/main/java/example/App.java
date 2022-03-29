package example;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.okhttp.v3_0.OkHttpTracing;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import org.jetbrains.annotations.NotNull;

import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.model.Bucket;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Handler for requests to Lambda function.
 */
public class App implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Meter sampleMeter = GlobalMeterProvider.get().get("aws-otel");
    private static final LongUpDownCounter bucketCounter =
            sampleMeter
                    .upDownCounterBuilder("queueSizeChange")
                    .setDescription("Queue Size change")
                    .setUnit("one")
                    .build();

    private static final AttributeKey<String> API_NAME = AttributeKey.stringKey("apiName");
    private static final AttributeKey<String> STATUS_CODE = AttributeKey.stringKey("statuscode");

    private static final Attributes METRIC_ATTRIBUTES = Attributes.builder()
                    .put(API_NAME, "opentelemetry-java")
                    .put(STATUS_CODE, "200")
                    .build();

    private static final Tracer tracer = GlobalOpenTelemetry.getTracer("myTracer");
    private static final Logger log = LoggerFactory.getLogger(App.class);


    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {

        System.out.println("Running handleRequest Method");
        log.info("Running handleRequest Method");

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("TraceID", System.getenv()
                .getOrDefault("_X_AMZN_TRACE_ID", "UNDEFINED"));

        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withHeaders(headers);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(OkHttpTracing.create(GlobalOpenTelemetry.get()).newInterceptor())
                .build();
        Request request = new Request.Builder().url("https://icanhazip.com").build();

        String tableName = System.getenv("DYNAMODB_TABLE");
        String body;
        int statusCode;
        try (S3Client s3Client = S3Client.create();
             DynamoDbClient dynamoDbClient = DynamoDbClient.create();
             Response okhttpResponse = client.newCall(request).execute()) {

            //get IP
            String ipVal = Objects.requireNonNull(okhttpResponse.body()).string();
            System.out.printf("IP: %s%n", ipVal);
            log.info(String.format("IP: %s", ipVal));

            //list S3 Buckets
            ListBucketsResponse listBucketsResponse = getS3BucketsResponse(s3Client);

            //get table item
            Map<String, AttributeValue> item = getDynamoDbItem(tableName, dynamoDbClient, ipVal);
            System.out.printf("Item contents %s%n", item.toString());
            log.info(String.format("Item contents %s", item));
            if (!item.isEmpty()) {
                updateDynamoDbItem(tableName, dynamoDbClient, item);
            } else {
                createDynamoDbItem(tableName, dynamoDbClient, ipVal);
            }

            // Generate a sample counter metric using the OpenTelemetry Java Metrics API
            int bucketCount = listBucketsResponse.buckets().size();
            bucketCounter.add(bucketCount, METRIC_ATTRIBUTES);

            //run custom span with parent and child span
            customSpan();

            statusCode = 200;
            body = String.format("{ \"bucket_count\": %d, \"ip\": \"%s\" }", bucketCount, ipVal);
        } catch (ResourceNotFoundException e) {
            statusCode = 500;
            String message = String.format("Error: The Amazon DynamoDB table \"%s\" can't be found.\n", tableName);
            body = String.format("{\"error\": \"%s\"}", message);
            System.out.println(body);
            log.info(body);
            System.out.println("Be sure that it exists and that you've typed its name correctly!");
            log.info("Be sure that it exists and that you've typed its name correctly!");
        }  catch (DynamoDbException e) {
            statusCode = 500;
            body = String.format("{\"error\": \"%s\"}", e.getMessage());
            System.out.printf("DynamoDB Exception %s%n", e.getMessage());
            log.info(String.format("DynamoDB Exception %s", e.getMessage()));
        } catch (Exception e) {
            statusCode = 500;
            body = String.format("{\"error\": \"%s\"}", e.getMessage());
            System.out.printf("Exception %s%n", e.getMessage());
            log.info(String.format("Exception %s%n", e.getMessage()));
            e.printStackTrace();
        }

        return response
                .withStatusCode(statusCode)
                .withBody(body);
    }

    private void customSpan() throws InterruptedException {
        Span parentSpan = tracer.spanBuilder("Parent Span")
                .setAttribute("ParentAttribute1", "Mom")
                .setAttribute("ParentAttribute2", "Dad")
                .startSpan();

        //The OpenTelemetry API offers also an automated way to propagate the parent span on the current thread:
        try(Scope scope = parentSpan.makeCurrent()) {
            Thread.sleep(1000);
            childMethod();
        } finally {
            parentSpan.end();
        }
    }

    private void childMethod() throws InterruptedException {
        Span childSpan = tracer.spanBuilder("Child Span")
                .setAttribute("ChildAttribute1", "Son")
                .setAttribute("ChildAttribute2", "Daughter")
                .startSpan();

        try(Scope scope = childSpan.makeCurrent()) {
            Thread.sleep(1500);
        } finally {
            childSpan.end();
        }
    }

    private void createDynamoDbItem(String tableName, DynamoDbClient dynamoDbClient, String ipVal) {
        System.out.println("Creating new dynamoDB item...");
        log.info("Creating new dynamoDB item...");
        Map<String, AttributeValue> addItem = new HashMap<>();

        addItem.put("ip", AttributeValue.builder().s(ipVal).build());
        addItem.put("count", AttributeValue.builder().n("0").build());

        PutItemRequest pir = PutItemRequest.builder()
                .tableName(tableName)
                .item(addItem)
                .build();
        dynamoDbClient.putItem(pir);
    }

    private void updateDynamoDbItem(String tableName, DynamoDbClient dynamoDbClient, Map<String, AttributeValue> item) {
        System.out.println("Updating dynamoDB item...");
        log.info("Updating dynamoDB item...");

        Map<String,AttributeValue> itemKey = new HashMap<>();
        Map<String, AttributeValueUpdate> updatedValues = new HashMap<>();

        itemKey.put("ip", AttributeValue.builder().s(item.get("ip").s()).build());

        // Update the column specified by name with updatedVal
        int incr = Integer.parseInt(item.get("count").n()) + 1;

        updatedValues.put("count", AttributeValueUpdate.builder()
                .value(AttributeValue.builder().n(Integer.toString(incr)).build())
                .action(AttributeAction.PUT)
                .build());
        UpdateItemRequest uir = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(itemKey)
                .attributeUpdates(updatedValues)
                .build();

        dynamoDbClient.updateItem(uir);
    }

    private Map<String, AttributeValue> getDynamoDbItem(String tableName, DynamoDbClient dynamoDbClient, String ipVal) {

        Map<String, AttributeValue> keyToGet = new HashMap<>();
        keyToGet.put("ip", AttributeValue.builder()
                .s(ipVal)
                .build());

        System.out.println("Getting dynamoDB item...");
        log.info("Getting dynamoDB item...");
        GetItemRequest gir = GetItemRequest.builder()
                .key(keyToGet)
                .tableName(tableName)
                .build();

        return dynamoDbClient.getItem(gir).item();
    }

    @NotNull
    private ListBucketsResponse getS3BucketsResponse(S3Client s3Client) {
        //print out buckets
        ListBucketsResponse listBucketsResponse = s3Client.listBuckets();
        System.out.println("Printing buckets...");
        log.info("Printing buckets...");
        for (Bucket bucket : listBucketsResponse.buckets()) {
            String output = String.format("{\"name\": \"%s\", \"created_at\": \"%s\"}", bucket.name(), bucket.creationDate().toString());
            System.out.println(output);
        }
        return listBucketsResponse;
    }
}
