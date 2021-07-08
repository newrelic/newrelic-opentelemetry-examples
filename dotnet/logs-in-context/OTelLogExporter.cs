using System;
using System.Text;
using System.Text.Json;
using OpenTelemetry;
using OpenTelemetry.Logs;

internal class OTelLogExporter : BaseExporter<LogRecord>
{
    private readonly string name;

    public OTelLogExporter(string name = "OtelLogExporter")
    {
        this.name = name;
    }

    public override ExportResult Export(in Batch<LogRecord> batch)
    {
        // SuppressInstrumentationScope should be used to prevent exporter
        // code from generating telemetry and causing live-loop.
        using var scope = SuppressInstrumentationScope.Begin();

        foreach (var record in batch)
        {
            var sb = new StringBuilder();

            sb.Append($"\"timestamp\":\"{record.Timestamp.ToString("o")}\",");

            sb.Append($"\"log.level\":\"{record.LogLevel.ToString()}\",");

            if (record.FormattedMessage != null)
            {
                sb.Append($"\"message\":{JsonSerializer.Serialize(record.FormattedMessage)},");
            }

            if (record.TraceId != default)
            {
                sb.Append($"\"trace.id\":{JsonSerializer.Serialize(record.TraceId.ToString())},");
            }

            if (record.SpanId != default)
            {
                sb.Append($"\"span.id\":{JsonSerializer.Serialize(record.SpanId.ToString())},");
            }

            if (record.Exception != null)
            {
                sb.Append($"\"error.class\":{JsonSerializer.Serialize(record.Exception.GetType().FullName)},");
                if (!string.IsNullOrWhiteSpace(record.Exception.Message))
                {
                    sb.Append($"\"error.message\":{JsonSerializer.Serialize(record.Exception.Message)},");
                }
                if (!string.IsNullOrWhiteSpace(record.Exception.StackTrace))
                {
                    sb.Append($"\"error.stack\":{JsonSerializer.Serialize(record.Exception.StackTrace)},");
                }
            }

            record.ForEachScope(ProcessScope, sb);

            void ProcessScope(LogRecordScope scope, StringBuilder builder)
            {
                foreach (var scopeItem in scope)
                {
                    builder.Append($"{JsonSerializer.Serialize(scopeItem.Key)}:{JsonSerializer.Serialize(scopeItem.Value)},");
                }
            }

            // We are not including the resource attributes on this console exporter because the fluent forward receiver in the
            // collector will not construct a resource from these log message. A resource processor in the collector is still
            // necessary to add resource attributes.

            Console.WriteLine($"{{{sb.ToString(0, sb.Length - 1)}}}");
        }

        return ExportResult.Success;
    }
}