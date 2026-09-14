# syntax=docker/dockerfile:1
#
# Unlike getting-started-guides/java/Dockerfile, this image does NOT bundle
# the OpenTelemetry Java agent or run with -javaagent. It relies entirely on
# the OpenTelemetry Operator's Instrumentation CR to inject an equivalent
# agent at pod admission time, demonstrating that "no code changes, no image
# changes beyond removing your own agent" is genuinely all that's needed for
# Operator-based auto-injection.
#
# `./gradlew bootJar` still triggers the build.gradle "copyAgent" task as a
# side effect (it copies the agent jar into build/agent/), but that has no
# effect on the app jar's own contents -- it's just not copied into this
# image or referenced by its ENTRYPOINT.
FROM eclipse-temurin:21 as build

COPY . /source

WORKDIR /source

RUN ./gradlew bootJar

FROM eclipse-temurin:21 as final

WORKDIR /app

COPY --from=build /source/build/libs/getting-started-java.jar .

ENTRYPOINT ["java", "-jar", "getting-started-java.jar"]

EXPOSE 8080
