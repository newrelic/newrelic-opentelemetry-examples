using OpenTelemetry.Logs;
using OpenTelemetry.Metrics;
using OpenTelemetry.Resources;
using OpenTelemetry.Trace;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddOpenTelemetry()
    // Define an OpenTelemetry resource
    // A resource represents a collection of attributes describing the
    // service. This collection of attributes will be associated with all
    // telemetry generated from this service (traces, metrics, logs).
    .ConfigureResource(resourceBuilder => 
        resourceBuilder
            .AddService("OpenTelemetry-Dotnet-Example")
            .AddTelemetrySdk())
    // Configure the OpenTelemetry SDK for traces
    .WithTracing(traceProviderBuilder => 
    {
        traceProviderBuilder
            // Step 1. Configure the SDK to listen to the following auto-instrumentation
            .AddAspNetCoreInstrumentation(options => 
                {
                    options.RecordException = true;
                })
            .AddHttpClientInstrumentation()
            // Step 2. Configure the SDK to listen to custom instrumentation.
            .AddSource("WeatherForecast")
            // Step 3. Configure the OTLP exporter to export to New Relic
            //     The OTEL_EXPORTER_OTLP_ENDPOINT environment variable should be set to New Relic's OTLP endpoint:
            //         OTEL_EXPORTER_OTLP_ENDPOINT=https://otlp.nr-data.net:4317
            //
            //     The OTEL_EXPORTER_OTLP_HEADERS environment variable should be set to include your New Relic API key:
            //         OTEL_EXPORTER_OTLP_HEADERS=api-key=<YOUR_API_KEY_HERE>
            .AddOtlpExporter(options => 
                {
                    options.Endpoint = new Uri($"{Environment.GetEnvironmentVariable("OTEL_EXPORTER_OTLP_ENDPOINT")}");
                    options.Headers = Environment.GetEnvironmentVariable("OTEL_EXPORTER_OTLP_HEADERS");
                });
    })
    // Configure the OpenTelemetry SDK for metrics
    .WithMetrics(meterProviderBuilder => {
        meterProviderBuilder
            // Step 1. Configure the SDK to listen to the following auto-instrumentation
            .AddRuntimeInstrumentation()
            .AddAspNetCoreInstrumentation()
            .AddHttpClientInstrumentation()
            
            // Step 2. Configure the OTLP exporter to export to New Relic
            //     The OTEL_EXPORTER_OTLP_ENDPOINT environment variable should be set to New Relic's OTLP endpoint:
            //         OTEL_EXPORTER_OTLP_ENDPOINT=https://otlp.nr-data.net:4317
            //
            //     The OTEL_EXPORTER_OTLP_HEADERS environment variable should be set to include your New Relic API key:
            //         OTEL_EXPORTER_OTLP_HEADERS=api-key=<YOUR_API_KEY_HERE>
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
builder.Host.ConfigureLogging(builder =>
{
    builder.ClearProviders();
    builder.AddConsole();
    builder.AddOpenTelemetry(options =>
    {
        options.IncludeFormattedMessage = true;
        options.ParseStateValues = true;
        options.IncludeScopes = true;

        options.AddOtlpExporter(options =>
        {
            options.Endpoint = new Uri($"{Environment.GetEnvironmentVariable("OTEL_EXPORTER_OTLP_ENDPOINT")}");
            options.Headers = Environment.GetEnvironmentVariable("OTEL_EXPORTER_OTLP_HEADERS");
        });
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
