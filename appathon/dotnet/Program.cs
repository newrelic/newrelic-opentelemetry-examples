using dotnet.Controllers;
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
        .AddSource(FibonacciController.ActivitySourceName)
        .AddAspNetCoreInstrumentation()
        .AddOtlpExporter();
});

var app = builder.Build();

app.MapControllers();

app.Run();
