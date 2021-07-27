using System.Diagnostics;
using OpenTelemetry;

namespace aspnetcore
{
    internal class MySpanProcessor : BaseProcessor<Activity>
    {
        public override void OnStart(Activity span)
        {
            // Do something when each span starts
        }

        public override void OnEnd(Activity span)
        {
            // Do something when each span ends
        }
    }
}
