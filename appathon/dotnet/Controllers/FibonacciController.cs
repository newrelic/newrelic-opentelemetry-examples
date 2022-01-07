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

    [HttpGet(Name = "GetFibonacci")]
    public object Get(long n)
    {
<<<<<<< HEAD
        long result;
        try
        {
            result = ComputeNthFibonocci(n);
        }
        catch (ArgumentOutOfRangeException ex)
        {
            return BadRequest(ex);
        }

        return new {
            n = n,
            result = result
        };
    }

    private long ComputeNthFibonocci(long n)
    {
        using var activity = activitySource.StartActivity(nameof(ComputeNthFibonocci));
        activity?.SetTag("n", n);

        if (n < 1 || n > 90)
        {
            var message = $"Parameter '{nameof(n)}' must be between 1 and 90";
            var exception = new ArgumentOutOfRangeException(nameof(n), n, "Must be between 1 and 90");

            activity?.SetStatus(ActivityStatusCode.Error, $"Parameter '{nameof(n)}' must be between 1 and 90");
            activity?.RecordException(exception);

            throw exception;
=======
        if (n < 1 || n > 90)
        {
            throw new ArgumentOutOfRangeException(nameof(n), n, "Must be between 1 and 90");
>>>>>>> ec82c90e4fb5afe628da4eca050b9261c6a9a0bb
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

        activity?.SetTag("result", result);
        return result;
    }
}
