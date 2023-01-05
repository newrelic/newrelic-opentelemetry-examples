using System.Diagnostics;
using System.Diagnostics.Metrics;
using Microsoft.AspNetCore.Mvc;
using OpenTelemetry.Trace;

namespace dotnet.Controllers;

[ApiController]
[Route("[controller]")]
public class FibonacciController : ControllerBase
{
    private const string AttributeNameN = "fibonacci.n";
    private const string AttributeNameResult = "fibonacci.result";
    private const string AttributeNameValid = "fibonacci.valid.n";

    private static ActivitySource activitySource = new ActivitySource(nameof(dotnet));
    private static Meter meter = new Meter(nameof(dotnet));
    private static Counter<long> fibonacciInvocations = meter.CreateCounter<long>(
        name: "fibonacci.invocations",
        unit: null,
        description: "Measures the number of times the fibonacci method is invoked.");

    private readonly ILogger<FibonacciController> logger;

    public FibonacciController(ILogger<FibonacciController> logger)
    {
        this.logger = logger;
    }

    [HttpGet]
    public IActionResult Get(long n)
    {
        try
        {
            return Ok(new { n = n, result = Fibonacci(n) });
        }
        catch (ArgumentOutOfRangeException ex)
        {
            Activity.Current.SetStatus(Status.Error.WithDescription(ex.Message));
            return BadRequest(new { message = ex.Message });
        }
    }

    private long Fibonacci(long n)
    {
        using var activity = activitySource.StartActivity(nameof(Fibonacci));
        activity?.SetTag(AttributeNameN, n);

        try
        {
            if (n < 1 || n > 90)
            {
                throw new ArgumentOutOfRangeException(nameof(n), n, "n must be between 1 and 90");
            }

            var result = 1L;
            if (n > 2)
            {
                var a = 0L;
                var b = 1L;

                for (var i = 1; i < n; i++)
                {
                    result = a + b;
                    a = b;
                    b = result;
                }
            }

            activity?.SetTag(AttributeNameResult, result);
            fibonacciInvocations.Add(1, new KeyValuePair<string, object?>(AttributeNameValid, true));
            logger.LogInformation("Computed fib({n}) = {result}.", n, result);
            return result;
        }
        catch (ArgumentOutOfRangeException ex)
        {
            activity?.SetStatus(Status.Error.WithDescription(ex.Message));
            activity?.RecordException(ex);
            fibonacciInvocations.Add(1, new KeyValuePair<string, object?>(AttributeNameValid, false));
            logger.LogInformation("Failed to compute fib({n}).", n);
            throw;
        }
    }
}
