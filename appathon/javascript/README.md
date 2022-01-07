# Summary

JavaScript implementation of the appathon app. 

## Run via docker

No dependencies needed besides docker.

Replace <your_license_key> with your New Relic license key.

  ```
  docker build -t appathon-javascript . \
    --env OTEL_EXPORTER_OTLP_ENDPOINT=grpc://otlp.nr-data.net:4317 \
    --env OTEL_EXPORTER_OTLP_HEADERS=api-key=<your_license_key_here> \
    appathon-javascript
  ```

## Run locally

1. Run `npm install`

2. Set the following environment variables:

    ```
    export OTEL_EXPORTER_OTLP_ENDPOINT=grpc://otlp.nr-data.net:4317
    export OTEL_EXPORTER_OTLP_HEADERS=api-key=<your_license_key_here>
    ```

    + Replace <your_license_key> with your New Relic license key.

3. Run the application. 

    ```
      npm start
    ```

    + This is a script to load the tracing code before the application code.

4. Invoke endpoint with curl: 

  ```
  curl "http://localhost:8080/fibonacci?n=num"
  ```
  + Replace `num` with any number between 1-90.
  + Replace `num` with any number less than 1 to proeduce an error or exclude the parameter completely. 