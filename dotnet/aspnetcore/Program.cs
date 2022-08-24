using OpenTelemetry.Logs;
using OpenTelemetry.Metrics;
using OpenTelemetry.Resources;
using OpenTelemetry.Trace;

var builder = WebApplication.CreateBuilder(args);

// Define an OpenTelemetry resource
// A resource represents a collection of attributes describing the
// service. This collection of attributes will be associated with all
// telemetry generated from this service (traces, metrics, logs).
var resourceBuilder = ResourceBuilder
    .CreateDefault()
    .AddService("OpenTelemetry-Dotnet-Example")
    .AddTelemetrySdk();

// Configure the OpenTelemetry SDK for traces
builder.Services.AddOpenTelemetryTracing(tracerProviderBuilder =>
{
    // Step 1. Declare the resource to be used by this tracer provider.
    tracerProviderBuilder
        .SetResourceBuilder(resourceBuilder);

    // Step 2. Configure the SDK to listen to the following auto-instrumentation
    tracerProviderBuilder
        .AddAspNetCoreInstrumentation(options =>
        {
            options.RecordException = true;
        })
        .AddHttpClientInstrumentation();

    // Step 3. Configure the SDK to listen to custom instrumentation.
    tracerProviderBuilder
        .AddSource("WeatherForecast");

    // Step 4. Configure the OTLP exporter to export to New Relic
    //     The OTEL_EXPORTER_OTLP_ENDPOINT environment variable should be set to New Relic's OTLP endpoint:
    //         OTEL_EXPORTER_OTLP_ENDPOINT=https://otlp.nr-data.net:4317
    //
    //     The OTEL_EXPORTER_OTLP_HEADERS environment variable should be set to include your New Relic API key:
    //         OTEL_EXPORTER_OTLP_HEADERS=api-key=<YOUR_API_KEY_HERE>
    tracerProviderBuilder
        .AddOtlpExporter(options =>
        {
            options.Endpoint = new Uri($"{Environment.GetEnvironmentVariable("OTEL_EXPORTER_OTLP_ENDPOINT")}");
            options.Headers = Environment.GetEnvironmentVariable("OTEL_EXPORTER_OTLP_HEADERS");
        });
});

// Configure the OpenTelemetry SDK for metrics
builder.Services.AddOpenTelemetryMetrics(meterProviderBuilder =>
{
    // Step 1. Declare the resource to be used by this meter provider.
    meterProviderBuilder
        .SetResourceBuilder(resourceBuilder);

    // Step 2. Configure the SDK to listen to the following auto-instrumentation
    meterProviderBuilder
        .AddRuntimeInstrumentation()
        .AddAspNetCoreInstrumentation()
        .AddHttpClientInstrumentation();

    // Step 3. Configure the OTLP exporter to export to New Relic
    //     The OTEL_EXPORTER_OTLP_ENDPOINT environment variable should be set to New Relic's OTLP endpoint:
    //         OTEL_EXPORTER_OTLP_ENDPOINT=https://otlp.nr-data.net:4317
    //
    //     The OTEL_EXPORTER_OTLP_HEADERS environment variable should be set to include your New Relic API key:
    //         OTEL_EXPORTER_OTLP_HEADERS=api-key=<YOUR_API_KEY_HERE>
    meterProviderBuilder
        .AddOtlpExporter((exporterOptions, metricReaderOptions) =>
        {
            exporterOptions.Endpoint = new Uri($"{Environment.GetEnvironmentVariable("OTEL_EXPORTER_OTLP_ENDPOINT")}");
            exporterOptions.Headers = Environment.GetEnvironmentVariable("OTEL_EXPORTER_OTLP_HEADERS");

            // New Relic requires the exporter to use delta aggregation temporality.
            // The OTLP exporter defaults to using cumulative aggregation temporatlity.
            metricReaderOptions.TemporalityPreference = MetricReaderTemporalityPreference.Delta;
        });
});

// Configure the OpenTelemetry SDK for logs
builder.Logging.ClearProviders();
builder.Logging.AddConsole();

builder.Logging.AddOpenTelemetry(options =>
{
    options.IncludeFormattedMessage = true;
    options.ParseStateValues = true;
    options.IncludeScopes = true;

    options
        .SetResourceBuilder(resourceBuilder)
        .AddOtlpExporter(options =>
        {
            options.Endpoint = new Uri($"{Environment.GetEnvironmentVariable("OTEL_EXPORTER_OTLP_ENDPOINT")}");
            options.Headers = Environment.GetEnvironmentVariable("OTEL_EXPORTER_OTLP_HEADERS");
        });
});

builder.Services.AddControllers();
// Learn more about configuring Swagger/OpenAPI at https://aka.ms/aspnetcore/swashbuckle
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();

var app = builder.Build();

// Configure the HTTP request pipeline.
if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseHttpsRedirection();

app.UseAuthorization();

app.MapControllers();

app.Run();
