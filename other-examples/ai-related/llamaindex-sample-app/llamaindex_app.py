"""
LlamaIndex FastAPI Application with OpenInference auto-instrumentation.
Sends telemetry data to an OpenTelemetry (OTel) Collector.
"""

import os
import json
from dotenv import load_dotenv, find_dotenv
from fastapi import FastAPI
from pydantic import BaseModel

from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import SimpleSpanProcessor
from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter

from openinference.instrumentation.llama_index import LlamaIndexInstrumentor

# Load environment variables (override existing shell env vars)
_ = load_dotenv(find_dotenv(), override=True)

# --- 1. Set up Telemetry ---
prov = TracerProvider()
exporter = OTLPSpanExporter(endpoint="http://localhost:4318/v1/traces")
prov.add_span_processor(SimpleSpanProcessor(exporter))
trace.set_tracer_provider(prov)

# Auto-instrument LlamaIndex - MUST be called before importing LlamaIndex
LlamaIndexInstrumentor().instrument(tracer_provider=prov)

# Now import LlamaIndex components
from llama_index.core.agent import AgentRunner, FunctionCallingAgentWorker, ReActAgent
from llama_index.core.tools import FunctionTool
from llama_index.llms.openai import OpenAI as _OpenAI


class OpenAI(_OpenAI):
    """Extends OpenAI LLM to emit gen_ai.response.finish_reasons on the current span."""

    async def achat(self, messages, **kwargs):
        response = await super().achat(messages, **kwargs)
        try:
            finish_reason = response.raw.choices[0].finish_reason
            trace.get_current_span().set_attribute(
                "gen_ai.response.finish_reasons", json.dumps([finish_reason])
            )
        except Exception:
            pass
        return response

# Initialize FastAPI app
app = FastAPI(
    title="TEST",
    description="Math tutor agent with OpenInference auto-instrumentation"
)

class PromptRequest(BaseModel):
    prompt: str

class PromptResponse(BaseModel):
    response: str

# Get model name from environment
model_name = os.getenv("LLM_MODEL", "gpt-4")

# Define tool functions
def add_numbers(a: int, b: int) -> int:
    """Add two numbers together and return the result."""
    return a + b

def subtract_numbers(a: int, b: int) -> int:
    """Subtract b from a and return the result."""
    return a - b

def multiply_numbers(a: int, b: int) -> int:
    """Multiply two numbers together and return the result."""
    return a * b

def divide_numbers(a: int, b: int) -> str:
    """Divide a by b and return the result. Returns error if b is zero."""
    if b == 0:
        return "Error: Cannot divide by zero"
    return str(a / b)

# Convert functions to LlamaIndex FunctionTools
add_tool = FunctionTool.from_defaults(fn=add_numbers)
subtract_tool = FunctionTool.from_defaults(fn=subtract_numbers)
multiply_tool = FunctionTool.from_defaults(fn=multiply_numbers)
divide_tool = FunctionTool.from_defaults(fn=divide_numbers)

# Create tools list
tools = [add_tool, subtract_tool, multiply_tool, divide_tool]

# System prompt for the agent
system_prompt = """You are a helpful math tutor who can perform calculations using the provided tools.

When a user asks a math question:
1. Identify what calculation is needed
2. Use the appropriate tool to compute the answer
3. Explain the result clearly

Available tools:
- add_numbers: Add two numbers
- subtract_numbers: Subtract two numbers
- multiply_numbers: Multiply two numbers
- divide_numbers: Divide two numbers
"""

# Create shared LLM instance with token usage tracking
llm = OpenAI(
    model=model_name,
    temperature=0,
    api_key=os.getenv("OPENAI_API_KEY"),
)

# Create FunctionCallingAgent (equivalent of FunctionAgent in 0.10.x)
function_agent = AgentRunner(
    FunctionCallingAgentWorker.from_tools(tools, llm=llm, verbose=True, system_prompt=system_prompt)
)

# Create ReActAgent for reasoning and action steps
react_agent = ReActAgent.from_tools(tools, llm=llm, verbose=True, max_iterations=10)

@app.post("/chat", response_model=PromptResponse)
async def chat(request: PromptRequest):
    """
    Chat endpoint using FunctionAgent with OpenInference auto-instrumentation.

    OpenInference automatically captures:
    - Agent reasoning steps
    - Tool calls and results
    - LLM interactions (prompts, completions, tokens)
    - Embeddings (if used)
    - Full execution trace

    All operations are automatically traced and sent to OTEL collector.
    """
    tracer = trace.get_tracer(__name__)
    with tracer.start_as_current_span("chat") as span:
        try:
            result = await function_agent.achat(request.prompt)
            span.set_attribute("gen_ai.input.messages", json.dumps([{"role": "user", "content": request.prompt}]))
            span.set_attribute("gen_ai.output.messages", json.dumps([{"role": "assistant", "content": result.response}]))
            return PromptResponse(response=result.response)

        except Exception as e:
            return PromptResponse(response=f"Error: {str(e)}")

@app.post("/chat/react", response_model=PromptResponse)
async def chat_react(request: PromptRequest):
    """
    Chat endpoint using ReActAgent with OpenInference auto-instrumentation.

    ReActAgent provides explicit reasoning and action steps:
    - Thought: Agent's reasoning about what to do
    - Action: Tool to call and arguments
    - Observation: Result from the tool
    - Answer: Final response after reasoning

    All operations are automatically traced and sent to OTEL collector.
    """
    tracer = trace.get_tracer(__name__)
    with tracer.start_as_current_span("chat_react") as span:
        try:
            result = await react_agent.achat(request.prompt)
            span.set_attribute("gen_ai.input.messages", json.dumps([{"role": "user", "content": request.prompt}]))
            span.set_attribute("gen_ai.output.messages", json.dumps([{"role": "assistant", "content": result.response}]))
            return PromptResponse(response=result.response)

        except Exception as e:
            return PromptResponse(response=f"Error: {str(e)}")
