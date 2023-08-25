using OpenTelemetry.Resources;
using OpenTelemetry.Trace;
using OpenTelemetry.Metrics;
using OpenTelemetry.Logs;
using System.Diagnostics.Metrics;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddControllers();

// Build a resource configuration action to set service information.
// A resource represents a collection of attributes describing the
// service. This collection of attributes will be associated with all
// telemetry generated from this service (traces, metrics, logs).
Action<ResourceBuilder> configureResource = r => r.AddService(
    serviceName: "getting-started-dotnet",
    serviceVersion: typeof(Program).Assembly.GetName().Version?.ToString() ?? "unknown",
    serviceInstanceId: Environment.MachineName);

// Configure the OpenTelemetry SDK for traces and metrics
builder.Services.AddOpenTelemetry()
    .ConfigureResource(configureResource
        .AddTelemetrySdk())
    .WithTracing(tracerProviderBuilder =>
    {
        tracerProviderBuilder
            .AddSource(nameof(dotnet))
            .AddAspNetCoreInstrumentation()
            .AddOtlpExporter();
    })
    .WithMetrics(meterProviderBuilder =>
    {
        meterProviderBuilder
            .AddMeter(nameof(dotnet))
            .AddAspNetCoreInstrumentation()
            .AddRuntimeInstrumentation()
            .AddView(instrument =>
            {
                return instrument.GetType().GetGenericTypeDefinition() == typeof(Histogram<>)
                    ? new Base2ExponentialBucketHistogramConfiguration()
                    : null;
            })
            .AddOtlpExporter((exporterOptions, metricReaderOptions) =>
            {
                metricReaderOptions.TemporalityPreference = MetricReaderTemporalityPreference.Delta;
            });

    });

// Configure the OpenTelemetry SDK for logs
builder.Logging.ClearProviders();
builder.Logging.AddOpenTelemetry(options =>
{
    var resourceBuilder = ResourceBuilder.CreateDefault();
    configureResource(resourceBuilder);
    options.SetResourceBuilder(resourceBuilder);

    options.IncludeFormattedMessage = true;
    options.ParseStateValues = true;
    options.IncludeScopes = true;
    options
        .AddOtlpExporter();
});

var app = builder.Build();

app.MapControllers();

app.Run();
