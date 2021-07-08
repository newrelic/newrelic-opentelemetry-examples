using Microsoft.AspNetCore.Hosting;
using Microsoft.Extensions.Hosting;

namespace logs_in_context
{
    public class Program
    {
        public static void Main(string[] args)
        {
            LogBootstrapper.ConfigureGlobalLoggerForExample();

            CreateHostBuilder(args).Build().Run();
        }

        public static IHostBuilder CreateHostBuilder(string[] args) =>
            Host.CreateDefaultBuilder(args)
                .ConfigureAspNetCoreLoggingForExample()
                .ConfigureWebHostDefaults(webBuilder =>
                {
                    webBuilder.UseStartup<Startup>();
                });
    }
}
