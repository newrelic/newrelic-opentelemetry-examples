using Microsoft.AspNetCore.Mvc;

namespace dotnet.Controllers;

[ApiController]
[Route("[controller]")]
public class FibonacciController : ControllerBase
{
    [HttpGet(Name = "GetFibonacci")]
    public long Get(long n)
    {
        if (n < 1 || n > 1000)
        {
            throw new ArgumentOutOfRangeException(nameof(n), n, "Must be between 1 and 1000");
        }

        if (n <= 2)
        {
            return 1;
        }

        var a = 0;
        var b = 1;
        var c = 0;

        for (var i = 1; i < n; i++)
        {
            c = checked(a + b);
            a = b;
            b = c;
        }

        return c;
    }
}
