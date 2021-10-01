using System.Diagnostics;
using Serilog.Core;
using Serilog.Events;

namespace logs_in_context
{
    public class OTelEnricher : ILogEventEnricher
    {
        public void Enrich(LogEvent logEvent, ILogEventPropertyFactory propertyFactory)
        {
            var activity = Activity.Current;

            if (activity != null)
            {
                logEvent.AddOrUpdateProperty(propertyFactory.CreateProperty("trace.id", activity.TraceId));
                logEvent.AddOrUpdateProperty(propertyFactory.CreateProperty("span.id", activity.SpanId));
            }
        }
    }
}