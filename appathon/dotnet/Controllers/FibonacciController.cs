using Microsoft.AspNetCore.Mvc;

namespace dotnet.Controllers;

[ApiController]
[Route("[controller]")]
public class FibonacciController : ControllerBase
{
    [HttpGet(Name = "GetFibonacci")]
    public object Get(long n)
    {
        if (n < 1 || n > 90)
        {
            throw new ArgumentOutOfRangeException(nameof(n), n, "Must be between 1 and 90");
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

        return new {
            n = n,
            result = result
        };
    }
}
