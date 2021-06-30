using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using Serilog.Events;
using Serilog.Formatting;
using Serilog.Formatting.Json;

namespace logs_in_context
{
    public class OTelFormatter : ITextFormatter
    {
        private const char JsonOpen = '{';
        private const char JsonClose = '}';
        private const char JsonDelim = ',';
        private const char JsonColon = ':';
        private const char JsonAtSign = '@';

        private static readonly ScalarValue JsonNull = new ScalarValue(null);

        private static readonly Dictionary<LogEventLevel, string> _cacheLogLevelNames =
            Enum.GetValues(typeof(LogEventLevel))
            .Cast<LogEventLevel>()
            .ToDictionary(x => x, x => x.ToString());

        private readonly JsonValueFormatter _valueFormatter = new JsonValueFormatter();

        private readonly string[] _reservedProperties = new[]
        {
            "timestamp",
            "error.message",
            "error.class",
            "error.stack",
            "message",
            "message.template",
            "log.level",
        };

        public void Format(LogEvent logEvent, TextWriter output)
        {
            if (logEvent == null)
            {
                throw new ArgumentNullException(nameof(logEvent));
            }

            if (output == null)
            {
                throw new ArgumentNullException(nameof(output));
            }

            output.Write(JsonOpen);

            WriteIntrinsicProperties(logEvent, output);
            WriteExceptionProperties(logEvent.Exception, output);
            WriteUserProperties(logEvent, output);

            output.Write(JsonClose);
            output.WriteLine();
        }

        private void WriteIntrinsicProperties(LogEvent logEvent, TextWriter output)
        {
            WriteTimestamp(logEvent.Timestamp, output); // do this first to make commas in JSON easier
            WriteFormattedJsonData("log.level", _cacheLogLevelNames[logEvent.Level], output);
            WriteFormattedJsonData("message.template", logEvent.MessageTemplate, output);
            WriteFormattedJsonData("message", logEvent.MessageTemplate.Render(logEvent.Properties), output);
        }

        private void WriteExceptionProperties(Exception exception, TextWriter output)
        {
            if (exception == null)
            {
                return;
            }

            WriteFormattedJsonData("error.class", exception.GetType().FullName, output);

            if (!string.IsNullOrWhiteSpace(exception.Message))
            {
                WriteFormattedJsonData("error.message", exception.Message, output);
            }

            if (!string.IsNullOrWhiteSpace(exception.StackTrace))
            {
                WriteFormattedJsonData("error.stack", exception.StackTrace, output);
            }
        }

        private void WriteUserProperties(LogEvent logEvent, TextWriter output)
        {
            foreach (var kvp in logEvent.Properties)
            {
                var key = kvp.Key;
                if (string.IsNullOrWhiteSpace(key))
                {
                    continue;
                }
                if (key[0] == JsonAtSign && key.Length >= 2 && key[1] != JsonAtSign)
                {
                    key = JsonAtSign + key;
                }

                if (!_reservedProperties.Any(p => kvp.Key == p))
                {
                    WriteFormattedJsonData(key, kvp.Value, output);
                }
            }
        }

        private void WriteTimestamp(DateTimeOffset timestamp, TextWriter output)
        {
            JsonValueFormatter.WriteQuotedJsonString("timestamp", output);
            output.Write(JsonColon);
            output.Write(timestamp.ToString("o"));
        }

        private void WriteFormattedJsonData(string key, LogEventPropertyValue value, TextWriter output)
        {
            if (string.IsNullOrEmpty(key))
            {
                return;
            }

            output.Write(JsonDelim);
            JsonValueFormatter.WriteQuotedJsonString(key, output);
            output.Write(JsonColon);
            _valueFormatter.Format(value, output);
        }

        private void WriteFormattedJsonData(string key, object value, TextWriter output)
        {
            if (string.IsNullOrEmpty(key))
            {
                return;
            }

            output.Write(JsonDelim);
            JsonValueFormatter.WriteQuotedJsonString(key, output);
            output.Write(JsonColon);

            if (value == null)
            {
                _valueFormatter.Format(JsonNull, output);
            }
            else
            {
                JsonValueFormatter.WriteQuotedJsonString(value.ToString(), output);
            }
        }
    }
}