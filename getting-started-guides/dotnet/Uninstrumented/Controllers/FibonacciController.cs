using System.Diagnostics;
using Microsoft.AspNetCore.Mvc;

namespace dotnet.Controllers;

[ApiController]
[Route("[controller]")]
public class FibonacciController : ControllerBase
{

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

        return result;
    }

    private void ThrowIfOutOfRange(long n)
    {
        if (n < 1 || n > 90)
        {
            throw new ArgumentOutOfRangeException(nameof(n), n, "n must be between 1 and 90");
        }
    }
}
