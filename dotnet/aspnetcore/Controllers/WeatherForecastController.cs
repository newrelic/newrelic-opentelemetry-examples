using System.Diagnostics;
using Microsoft.AspNetCore.Mvc;

namespace aspnetcore.Controllers;

[ApiController]
[Route("[controller]")]
public class WeatherForecastController : ControllerBase
{
    private static readonly string[] Summaries = new[]
    {
        "Freezing", "Bracing", "Chilly", "Cool", "Mild", "Warm", "Balmy", "Hot", "Sweltering", "Scorching"
    };

    private readonly ILogger<WeatherForecastController> _logger;
    private static HttpClient _httpClient = new HttpClient();

    public WeatherForecastController(ILogger<WeatherForecastController> logger)
    {
        _logger = logger;
    }

    [HttpGet(Name = "GetWeatherForecast")]
    public async Task<IEnumerable<WeatherForecast>> Get()
    {
        await DoSomeWork();

        var forecast = Enumerable.Range(1, 5).Select(index => new WeatherForecast
        {
            Date = DateTime.Now.AddDays(index),
            TemperatureC = Random.Shared.Next(-20, 55),
            Summary = Summaries[Random.Shared.Next(Summaries.Length)]
        })
        .ToArray();

        _logger.LogInformation("WeatherForecasts generated {count}.", forecast.Length);

        return forecast;
    }

    // An ActivitySource is .NET's term for an OpenTelemetry Tracer.
    // Spans generated from this ActivitySource are associated with the ActivitySource's name and version.
    private static ActivitySource _tracer = new ActivitySource("WeatherForecast", "1.2.3");

    private async Task DoSomeWork()
    {
        // Start a span using the OpenTelemetry API
        using var span = _tracer.StartActivity("DoSomeWork", ActivityKind.Internal);

        // Decorate the span with additional attributes
        span?.AddTag("SomeKey", "SomeValue");

        // Do some work
        await Task.Delay(50);

        // Make an external call
        await _httpClient.GetStringAsync("https://www.newrelic.com");

        // Do some more work
        await Task.Delay(10);
    }
}
