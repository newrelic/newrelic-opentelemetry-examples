FROM eclipse-temurin:17

RUN apt update && apt upgrade -y && apt install -y git

WORKDIR /

# Clone petclinic repo
RUN git clone https://github.com/spring-projects/spring-petclinic.git

WORKDIR /spring-petclinic

# Copy logback config with structured logging
COPY log4j2.xml src/main/resources/log4j2.xml
COPY Log4j2EventLayout.json src/main/resources/Log4j2EventLayout.json

# Modify build.gradle to include dependencies for JSON structured logging
RUN sed -e '/dependencies {/a\  implementation "org.springframework.boot:spring-boot-starter-log4j2"' build.gradle > tmp; mv tmp build.gradle
RUN sed -e '/dependencies {/a\  implementation "org.apache.logging.log4j:log4j-layout-template-json:2.20.0"' build.gradle > tmp; mv tmp build.gradle
RUN echo "configurations.all {\n  exclude module: 'spring-boot-starter-logging'\n}" >> build.gradle

# Build the app
RUN ./gradlew bootJar

# Download the otel java agent
RUN wget https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar

CMD java -javaagent:opentelemetry-javaagent.jar -jar build/libs/*.jar

