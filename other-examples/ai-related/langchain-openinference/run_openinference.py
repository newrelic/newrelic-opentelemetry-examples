"""
Run the shared agent with OpenInference auto-instrumentation.

LangChainInstrumentor().instrument() patches LangChain's callback system and
emits OpenInference span attributes (llm.model_name, llm.token_count.*,
llm.input_messages.N.message.*, tool.name, ...) with zero manual mapping
code. The OTel Collector's genainormalizerprocessor (source: openinference)
renames these into the OTel GenAI semantic conventions (gen_ai.*) before
export — see otel-collector-config.yaml.
"""

import os
import sys

from dotenv import load_dotenv
from openinference.instrumentation.langchain import LangChainInstrumentor
from opentelemetry import trace
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor

load_dotenv()

resource = Resource.create(
    {
        "service.name": os.getenv("SERVICE_NAME", "langchain-openinference-demo"),
        "instrumentation.source": "openinference",
    }
)
provider = TracerProvider(resource=resource)
exporter = OTLPSpanExporter(endpoint=os.getenv("OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4317"), insecure=True)
provider.add_span_processor(BatchSpanProcessor(exporter))
trace.set_tracer_provider(provider)

LangChainInstrumentor().instrument()

from agent import run  # noqa: E402  (import after instrumentation is wired up)

if __name__ == "__main__":
    query = " ".join(sys.argv[1:]) or "Plan a trip to Berlin — tell me the weather and the best places to visit"
    print(f"> {query}")
    print(run(query))
    provider.force_flush()
