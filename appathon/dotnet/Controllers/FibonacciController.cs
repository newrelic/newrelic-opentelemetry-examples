using System.Diagnostics;
using Microsoft.AspNetCore.Mvc;
using OpenTelemetry.Trace;

namespace dotnet.Controllers;

[ApiController]
[Route("[controller]")]
public class FibonacciController : ControllerBase
{
    public const string ActivitySourceName = "FibonacciService";
    private ActivitySource activitySource = new ActivitySource(ActivitySourceName);

    [HttpGet]
    public IActionResult Get(long n)
    {
        try
        {
            return Ok(new {
                n = n,
                result = Fibonacci(n)
            });
        }
        catch (ArgumentOutOfRangeException ex)
        {
            return BadRequest(new { message = ex.Message });
        }
    }

    private long Fibonacci(long n)
    {
        using var activity = activitySource.StartActivity(nameof(Fibonacci));
        activity?.SetTag("fibonacci.n", n);

        try
        {
            ThrowIfOutOfRange(n);
        }
        catch (ArgumentOutOfRangeException ex)
        {
            activity?.SetStatus(Status.Error.WithDescription(ex.Message));
            activity?.RecordException(ex);
            throw;
        }

        var result = 0L;
        if (n <= 2)
        {
            result = 1;
        }
        else
        {
            var a = 0L;
            var b = 1L;

            for (var i = 1; i < n; i++)
            {
                result = checked(a + b);
                a = b;
                b = result;
            }
        }

        activity?.SetTag("fibonacci.result", result);
        return result;
    }

    private void ThrowIfOutOfRange(long n)
    {
        if (n < 1 || n > 90)
        {
            throw new ArgumentOutOfRangeException(nameof(n), n, "Must be between 1 and 90");
        }
    }
}
