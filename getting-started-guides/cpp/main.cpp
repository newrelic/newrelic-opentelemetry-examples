#include "oatpp/web/server/HttpConnectionHandler.hpp"
#include "oatpp/network/Server.hpp"
#include "oatpp/network/tcp/server/ConnectionProvider.hpp"

#include "opentelemetry/exporters/otlp/otlp_http_exporter_factory.h"
#include "opentelemetry/exporters/otlp/otlp_http_exporter_options.h"
#include "opentelemetry/sdk/trace/processor.h"
#include "opentelemetry/sdk/trace/batch_span_processor_factory.h"
#include "opentelemetry/sdk/trace/batch_span_processor_options.h"
#include "opentelemetry/sdk/trace/tracer_provider_factory.h"
#include "opentelemetry/trace/provider.h"
#include "opentelemetry/sdk/trace/tracer_provider.h"

#include "opentelemetry/exporters/otlp/otlp_http_metric_exporter_factory.h"
#include "opentelemetry/exporters/otlp/otlp_http_metric_exporter_options.h"
#include "opentelemetry/metrics/provider.h"
#include "opentelemetry/sdk/metrics/aggregation/default_aggregation.h"
#include "opentelemetry/sdk/metrics/export/periodic_exporting_metric_reader.h"
#include "opentelemetry/sdk/metrics/export/periodic_exporting_metric_reader_factory.h"
#include "opentelemetry/sdk/metrics/meter_context_factory.h"
#include "opentelemetry/sdk/metrics/meter_provider.h"
#include "opentelemetry/sdk/metrics/meter_provider_factory.h"

#include "opentelemetry/exporters/otlp/otlp_http_log_record_exporter_factory.h"
#include "opentelemetry/exporters/otlp/otlp_http_log_record_exporter_options.h"
#include "opentelemetry/logs/provider.h"
#include "opentelemetry/sdk/logs/logger_provider_factory.h"
#include "opentelemetry/sdk/logs/processor.h"
#include "opentelemetry/sdk/logs/simple_log_record_processor_factory.h"

#include <cstdlib>
#include <ctime>
#include <string>

using namespace std;

namespace trace_api = opentelemetry::trace;
namespace trace_sdk = opentelemetry::sdk::trace;

namespace metric_sdk = opentelemetry::sdk::metrics;
namespace metrics_api = opentelemetry::metrics;

namespace logs_api = opentelemetry::logs;
namespace logs_sdk = opentelemetry::sdk::logs;

namespace otlp = opentelemetry::exporter::otlp;

namespace {
  void InitTracer()
  {
    trace_sdk::BatchSpanProcessorOptions bspOpts{};
    otlp::OtlpHttpExporterOptions opts;
    auto exporter  = otlp::OtlpHttpExporterFactory::Create(opts);
    auto processor = trace_sdk::BatchSpanProcessorFactory::Create(std::move(exporter), bspOpts);
    std::shared_ptr<trace_api::TracerProvider> provider = trace_sdk::TracerProviderFactory::Create(std::move(processor));
    trace_api::Provider::SetTracerProvider(provider);
  }

  void InitMetrics()
  {
    otlp::OtlpHttpMetricExporterOptions opts;
    auto exporter = otlp::OtlpHttpMetricExporterFactory::Create(opts);
    metric_sdk::PeriodicExportingMetricReaderOptions reader_options;
    reader_options.export_interval_millis = std::chrono::milliseconds(1000);
    reader_options.export_timeout_millis  = std::chrono::milliseconds(500);
    auto reader = metric_sdk::PeriodicExportingMetricReaderFactory::Create(std::move(exporter), reader_options);
    auto context = metric_sdk::MeterContextFactory::Create();
    context->AddMetricReader(std::move(reader));
    auto u_provider = metric_sdk::MeterProviderFactory::Create(std::move(context));
    std::shared_ptr<metrics_api::MeterProvider> provider(std::move(u_provider));
    metrics_api::Provider::SetMeterProvider(provider);
  }

  void InitLogger()
  {
    otlp::OtlpHttpLogRecordExporterOptions opts;
    auto exporter  = otlp::OtlpHttpLogRecordExporterFactory::Create(opts);
    auto processor = logs_sdk::SimpleLogRecordProcessorFactory::Create(std::move(exporter));
    std::shared_ptr<logs_api::LoggerProvider> provider =
        logs_sdk::LoggerProviderFactory::Create(std::move(processor));
    logs_api::Provider::SetLoggerProvider(provider);
  }

  void CleanupTracer() {
    std::shared_ptr<opentelemetry::trace::TracerProvider> none;
    trace_api::Provider::SetTracerProvider(none);
  }
}

class Handler : public oatpp::web::server::HttpRequestHandler {
public:
  shared_ptr<OutgoingResponse> handle(const shared_ptr<IncomingRequest>& request) override {
    auto tracer = opentelemetry::trace::Provider::GetTracerProvider()->GetTracer("my-app-tracer");
    auto span = tracer->StartSpan("RollDiceServer");
    int low = 1;
    int high = 7;
    int random = rand() % (high - low) + low;
    // Convert a std::string to oatpp::String
    const string response = to_string(random);
    span->End();
    return ResponseFactory::createResponse(Status::CODE_200, response.c_str());
  }
};

void run() {
  auto router = oatpp::web::server::HttpRouter::createShared();
  router->route("GET", "/rolldice", std::make_shared<Handler>());
  auto connectionHandler = oatpp::web::server::HttpConnectionHandler::createShared(router);
  auto connectionProvider = oatpp::network::tcp::server::ConnectionProvider::createShared({"0.0.0.0", 8080, oatpp::network::Address::IP_4});
  oatpp::network::Server server(connectionProvider, connectionHandler);
  OATPP_LOGI("Dice Server", "Server running on port %s", static_cast<const char*>(connectionProvider->getProperty("port").getData()));
  server.run();
}

int main() {
  oatpp::base::Environment::init();
  InitTracer();
  InitMetrics();
  InitLogger();
  srand((int)time(0));
  run();
  oatpp::base::Environment::destroy();
  CleanupTracer();
  return 0;
}
