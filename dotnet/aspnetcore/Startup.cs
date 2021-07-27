using System;
using System.Collections.Generic;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using OpenTelemetry.Resources;
using OpenTelemetry.Trace;

namespace aspnetcore
{
    public class Startup
    {
        public Startup(IConfiguration configuration)
        {
            Configuration = configuration;
        }

        public IConfiguration Configuration { get; }

        public void ConfigureServices(IServiceCollection services)
        {
            // Configure the OpenTelemetry SDK for tracing
            services.AddOpenTelemetryTracing(builder =>
            {
                // Step 1. Define a resource
                // A resource represents a collection of attributes describing the
                // service. This collection of attributes is associated with all
                // telemetry generated from this service.
                var resourceBuilder = ResourceBuilder
                    .CreateDefault()
                    .AddService("OpenTelemetry-Dotnet-Example")
                    .AddAttributes(new Dictionary<string, object> {
                        { "environment", "production" }
                    })
                    .AddTelemetrySdk();

                builder.SetResourceBuilder(resourceBuilder);

                // Step 2. Set a sampler (optional)
                // Defaults to ParentBasedSampler
                // (i.e. for each span ask: if my parent was sampled then I am sampled)
                builder.SetSampler(new AlwaysOnSampler());

                // Step 3. Configure the SDK to listen to the following auto-instrumentation
                builder
                    .AddAspNetCoreInstrumentation(options =>
                    {
                        options.RecordException = true;
                        options.Filter = (context) =>
                        {
                            return context.Request.Method == "GET";
                        };
                    })
                    .AddHttpClientInstrumentation();

                // Step 4. Configure the SDK to listen to my custom instrumentation
                builder
                    .AddSource("WeatherForecast");

                // Step 5. Configure custom span processors (optional)
                // Span processors enable you to further enrich or filter
                // span data as it is generated.
                builder
                    .AddProcessor(new MySpanProcessor());

                // Step 6. Configure the OTLP exporter to export to New Relic
                //     The OTEL_EXPORTER_OTLP_ENDPOINT environment variable should be set to New Relic's OTLP endpoint
                //     The OTEL_EXPORTER_OTLP_HEADERS environment variable should be set to: "api-key=<YOUR_API_KEY_HERE>"
                builder
                    .AddOtlpExporter(options =>
                    {
                        options.Endpoint = new Uri($"{Environment.GetEnvironmentVariable("OTEL_EXPORTER_OTLP_ENDPOINT")}");
                        options.Headers = Environment.GetEnvironmentVariable("OTEL_EXPORTER_OTLP_HEADERS");
                    });
            });

            services.AddControllers();
        }

        public void Configure(IApplicationBuilder app, IWebHostEnvironment env)
        {
            if (env.IsDevelopment())
            {
                app.UseDeveloperExceptionPage();
            }

            app.UseHttpsRedirection();

            app.UseRouting();

            app.UseAuthorization();

            app.UseEndpoints(endpoints =>
            {
                endpoints.MapControllers();
            });
        }
    }
}
