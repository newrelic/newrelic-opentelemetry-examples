FROM mcr.microsoft.com/dotnet/sdk:6.0 AS build-env
WORKDIR /app

COPY *.csproj ./
RUN dotnet restore

COPY . ./
RUN dotnet publish -c Release -o out

FROM mcr.microsoft.com/dotnet/aspnet:6.0-bullseye-slim
WORKDIR /app
COPY --from=build-env /app/out .
ENV OTEL_EXPORTER_OTLP_ENDPOINT = ${OTEL_EXPORTER_OTLP_ENDPOINT}
ENV OTEL_EXPORTER_OTLP_HEADERS = ${OTEL_EXPORTER_OTLP_HEADERS}

EXPOSE 80
ENTRYPOINT ["dotnet", "minimal-api.dll"]
