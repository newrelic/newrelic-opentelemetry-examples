# syntax=docker/dockerfile:1

FROM eclipse-temurin:21 as build

ARG OTEL_AGENT_VERSION=2.28.1-alpha

COPY . /source

WORKDIR /source

RUN ./gradlew bootJar -PotelAgentVersion=${OTEL_AGENT_VERSION}

FROM eclipse-temurin:21 as final

WORKDIR /app

COPY --from=build /source/build/libs/getting-started-java.jar .
COPY --from=build /source/build/agent/opentelemetry-javaagent.jar .

ENTRYPOINT ["java", "-javaagent:opentelemetry-javaagent.jar", "-jar", "getting-started-java.jar"]

EXPOSE 8080
