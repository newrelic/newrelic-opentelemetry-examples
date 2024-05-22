# syntax=docker/dockerfile:1

FROM eclipse-temurin:17 as build

COPY . /source

WORKDIR /source

RUN ./gradlew bootJar

FROM eclipse-temurin:17 as final

WORKDIR /app

COPY --from=build /source/build/libs/getting-started-java.jar .
COPY --from=build /source/build/agent/opentelemetry-javaagent.jar .

ENTRYPOINT ["java", "-javaagent:opentelemetry-javaagent.jar", "-jar", "getting-started-java.jar"]

EXPOSE 8080
