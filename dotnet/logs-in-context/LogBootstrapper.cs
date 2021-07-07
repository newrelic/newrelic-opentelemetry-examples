using System;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using Serilog;
using OpenTelemetry.Logs;
using OpenTelemetry;
using OpenTelemetry.Resources;
using Microsoft.Extensions.DependencyInjection;
using OpenTelemetry.Trace;

namespace logs_in_context
{
    public static class LogBootstrapper
    {
        private static LoggingScenario _loggingScenario;
        private static ResourceBuilder _resourceBuilder;

        static LogBootstrapper()
        {
            var scenarioFromEnvironment = System.Environment.GetEnvironmentVariable("LOGGING_SCENARIO");
            if (scenarioFromEnvironment != null && Enum.TryParse<LoggingScenario>(scenarioFromEnvironment, true, out var loggingScenario))
            {
                _loggingScenario = loggingScenario;
            }
            else
            {
                _loggingScenario = LoggingScenario.Unspecified;
            }

            _resourceBuilder = ResourceBuilder.CreateDefault().AddService(GetEnvironmentVariableValueOrNull("SERVICE_NAME"));
        }

        static public void ConfigureGlobalLoggerForExample()
        {
            switch (_loggingScenario)
            {
                case LoggingScenario.Unspecified:
                case LoggingScenario.Serilog:
                    Log.Logger = new LoggerConfiguration()
                        .Enrich.FromLogContext()
                        .Enrich.With<OTelEnricher>()
                        .WriteTo.Console(new OTelFormatter())
                        .CreateLogger();
                    break;
            }
        }

        static public IHostBuilder ConfigureAspNetCoreLoggingForExample(this IHostBuilder hostBuilder)
        {
            switch (_loggingScenario)
            {
                case LoggingScenario.Unspecified:
                case LoggingScenario.Serilog:
                    return hostBuilder.UseSerilog();
                case LoggingScenario.OTelSdk:
                    return hostBuilder.ConfigureLogging((context, builder) =>
                        {
                            builder.ClearProviders();

                            builder.AddOpenTelemetry(options =>
                                {
                                    options.SetResourceBuilder(_resourceBuilder);
                                    options.IncludeScopes = true;
                                    options.ParseStateValues = true;
                                    options.IncludeFormattedMessage = true;
                                    options.AddProcessor(new BatchLogRecordExportProcessor(new OTelLogExporter()));
                                });
                        });
            }

            return hostBuilder;
        }

        public static IServiceCollection ConfigureTracingForExample(this IServiceCollection services)
        {
            // This example uses an insecure gRPC service. In a production system you should use a secure service instead.
            // See: https://docs.microsoft.com/aspnet/core/grpc/troubleshoot#call-insecure-grpc-services-with-net-core-client
            AppContext.SetSwitch("System.Net.Http.SocketsHttpHandler.Http2UnencryptedSupport", true);

            return services.AddOpenTelemetryTracing(
                (builder) => builder
                    .SetResourceBuilder(_resourceBuilder)
                    .AddAspNetCoreInstrumentation()
                    .AddHttpClientInstrumentation()
                    .AddOtlpExporter(o =>
                        {
                            var collectorUrlString = GetEnvironmentVariableValueOrNull("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT", "OTEL_EXPORTER_OTLP_ENDPOINT");
                            if (collectorUrlString != null)
                            {
                                o.Endpoint = new Uri("http://otel-collector:4317");
                            }
                        })
                    );
        }

        private static string GetEnvironmentVariableValueOrNull(params string[] environmentVariableNames)
        {
            foreach (var variable in environmentVariableNames)
            {
                var valueFromEnvironment = System.Environment.GetEnvironmentVariable(variable);
                if (valueFromEnvironment != null)
                {
                    return valueFromEnvironment;
                }
            }

            return null;
        }
    }
}