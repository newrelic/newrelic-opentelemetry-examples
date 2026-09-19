"""
Run the shared agent with OpenLLMetry (Traceloop) auto-instrumentation.

Traceloop.init() patches LangChain's callback system and emits Traceloop/
OpenLLMetry span attributes (llm.usage.prompt_tokens, llm.request.model,
traceloop.entity.name, ...) with zero manual mapping code. The OTel
Collector's genainormalizerprocessor (source: openllmetry) renames these
into the OTel GenAI semantic conventions (gen_ai.*) before export — see
otel-collector-config.yaml.
"""

import os
import sys

from dotenv import load_dotenv
from traceloop.sdk import Traceloop

load_dotenv()

Traceloop.init(
    app_name=os.getenv("SERVICE_NAME", "langchain-openllmetry-demo"),
    disable_batch=True,
    api_endpoint=os.getenv("OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4318"),
    resource_attributes={"instrumentation.source": "openllmetry"},
)

from agent import run  # noqa: E402  (import after instrumentation is wired up)

if __name__ == "__main__":
    query = " ".join(sys.argv[1:]) or "What's the weather in Berlin?"
    print(f"> {query}")
    print(run(query))
