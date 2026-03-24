# syntax=docker/dockerfile:1

FROM eclipse-temurin:17 as build

COPY . /source

WORKDIR /source

RUN ./gradlew bootJar

FROM eclipse-temurin:17 as final

WORKDIR /app

COPY --from=build /source/build/libs/ignored-errors.jar .
COPY --from=build /source/build/agent/opentelemetry-javaagent.jar .

ENTRYPOINT ["java", "-javaagent:opentelemetry-javaagent.jar", "-jar", "ignored-errors.jar"]

EXPOSE 8080
