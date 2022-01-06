using OpenTelemetry.Resources;
using OpenTelemetry.Trace;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddControllers();

var resourceBuilder = ResourceBuilder
    .CreateDefault()
    .AddService("appathon-dotnet")
    .AddTelemetrySdk();

builder.Services.AddOpenTelemetryTracing(tracerProviderBuilder =>
{
    tracerProviderBuilder
        .SetResourceBuilder(resourceBuilder)
        .AddAspNetCoreInstrumentation(options =>
        {
            options.RecordException = true;
        })
        .AddOtlpExporter();
});

var app = builder.Build();

app.MapControllers();

app.Run();
