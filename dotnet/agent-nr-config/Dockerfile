# syntax=docker/dockerfile:1
FROM mcr.microsoft.com/dotnet/sdk:6.0 AS build
# Download and unpack the agent
# Refer to https://github.com/open-telemetry/opentelemetry-dotnet-instrumentation/releases for the available releases.
RUN apt-get update && apt-get install unzip \
	&& wget "https://github.com/open-telemetry/opentelemetry-dotnet-instrumentation/releases/download/v0.1.0-beta.1/opentelemetry-dotnet-instrumentation-linux-glibc.zip" -O /tracer-home.zip \
	&& unzip -d /tracer-home /tracer-home.zip
WORKDIR /src

COPY ["agent-nr-config.csproj", "."]
RUN dotnet restore "./agent-nr-config.csproj"

# Build the application in a framework dependent mode.
# Automatic instrumentation is not currently supported for self-contained deployments or builds.
COPY . .
RUN dotnet build "agent-nr-config.csproj" -c Release -o /app/build

FROM mcr.microsoft.com/dotnet/aspnet:6.0 AS final
WORKDIR /app
# Copy the built application
COPY --from=build /app/build .
# Install the agent
COPY --from=build /tracer-home /tracer-home
# The most important environment variables are shown in this example.
# For a complete list of options refer to the OpenTelemetry documenations available at
# https://github.com/open-telemetry/opentelemetry-dotnet-instrumentation/blob/47f16b5748218f37dd9bd543a0f133670904f9f7/docs/config.md.
# This env variable points to the installation directory for the agent
ENV OTEL_DOTNET_AUTO_HOME="/tracer-home"
ENV OTEL_DOTNET_AUTO_EXCLUDE_PROCESSES="dotnet,dotnet.exe"
ENV DOTNET_STARTUP_HOOKS="/tracer-home/netcoreapp3.1/OpenTelemetry.AutoInstrumentation.StartupHook.dll"
ENV DOTNET_ADDITIONAL_DEPS="/tracer-home/AdditionalDeps"
ENV DOTNET_SHARED_STORE="/tracer-home/store"
ENV OTEL_TRACES_EXPORTER="otlp"
# This env variable points to the New Relic OTLP endpoint
ENV OTEL_EXPORTER_OTLP_ENDPOINT="https://otlp.nr-data.net:4318"
# This env variable adds your New Relic license key to the OTLP headers.
# You should override this when running the container so that a valid license key is used.
# The instructions in the README for this application shows how to override this value when starting the container.
ENV OTEL_EXPORTER_OTLP_HEADERS="api-key=${NEW_RELIC_LICENSE_KEY}"
# This env variable controls the name New Relic will use for your application
ENV OTEL_SERVICE_NAME="agent-nr-config"
# This env variable controls which libraries should be instrumented.
# For a completed list of available instrumentations refer to
# https://github.com/open-telemetry/opentelemetry-dotnet-instrumentation/blob/47f16b5748218f37dd9bd543a0f133670904f9f7/docs/config.md#instrumented-libraries-and-frameworks
ENV OTEL_DOTNET_AUTO_ENABLED_INSTRUMENTATIONS="AspNet"
# The env variables below can be used if byte-code instrumentation is desired. See
# https://github.com/open-telemetry/opentelemetry-dotnet-instrumentation/blob/47f16b5748218f37dd9bd543a0f133670904f9f7/docs/config.md#instrumented-libraries-and-frameworks
# for the list of available instrumentations and which ones require byte-code support.
# ENV CORECLR_ENABLE_PROFILING=1
# ENV CORECLR_PROFILER="{918728DD-259F-4A6A-AC2B-B85E1B658318}"
# ENV CORECLR_PROFILER_PATH="/tracer-home/OpenTelemetry.AutoInstrumentation.Native.so"
# ENV OTEL_DOTNET_AUTO_INTEGRATIONS_FILE="/tracer-home/integrations.json"
ENTRYPOINT ["dotnet", "agent-nr-config.dll"]
