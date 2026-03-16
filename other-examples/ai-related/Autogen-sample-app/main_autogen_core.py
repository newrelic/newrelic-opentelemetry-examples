"""
AutoGen Core FastAPI Application with Built-in OpenTelemetry auto-instrumentation.
Uses AutoGen Core 0.4.2+ native telemetry support exclusively - NO manual spans.
"""

import os
from contextlib import asynccontextmanager
from dotenv import load_dotenv, find_dotenv
from fastapi import FastAPI
from pydantic import BaseModel

# OpenTelemetry imports (minimal setup for tracer provider only)
from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from opentelemetry.sdk.resources import Resource, SERVICE_NAME
from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
from opentelemetry.instrumentation.openai import OpenAIInstrumentor

# Load environment variables
load_dotenv(find_dotenv(), override=True)

otel_endpoint = os.getenv("OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4318/v1/traces")
service_name = os.getenv("OTEL_SERVICE_NAME", "autogen-sample-app")

resource = Resource.create({SERVICE_NAME: service_name})
tracer_provider = TracerProvider(resource=resource)
tracer_provider.add_span_processor(
    BatchSpanProcessor(
        OTLPSpanExporter(endpoint=otel_endpoint),
        max_export_batch_size=10,
        schedule_delay_millis=1000,
    )
)
trace.set_tracer_provider(tracer_provider)
OpenAIInstrumentor().instrument()

# Import AutoGen Core
from autogen_core import SingleThreadedAgentRuntime, CancellationToken, Agent, MessageContext
from autogen_core.models import UserMessage, AssistantMessage, FunctionExecutionResult, FunctionExecutionResultMessage, LLMMessage
from autogen_core.tools import FunctionTool
from autogen_ext.models.openai import OpenAIChatCompletionClient
from autogen_core import AgentId, MessageContext
import httpx
import json

# Request/Response models
class PromptRequest(BaseModel):
    prompt: str

class PromptResponse(BaseModel):
    response: str

# Get config
model_name = os.getenv("LLM_MODEL", "gpt-4")
openai_api_key = os.getenv("OPENAI_API_KEY")

# Define tool functions for math operations
def add_numbers(a: int, b: int) -> int:
    """Add two numbers together and return the result."""
    return a + b

def subtract_numbers(a: int, b: int) -> int:
    """Subtract b from a and return the result."""
    return a - b

def multiply_numbers(a: int, b: int) -> int:
    """Multiply two numbers together and return the result."""
    return a * b

def divide_numbers(a: float, b: float) -> str:
    """Divide a by b and return the result. Returns error if b is zero."""
    if b == 0:
        return "Error: Cannot divide by zero"
    return str(a / b)

# Create AutoGen Core tools
tools = [
    FunctionTool(add_numbers, description="Add two numbers together"),
    FunctionTool(subtract_numbers, description="Subtract b from a"),
    FunctionTool(multiply_numbers, description="Multiply two numbers together"),
    FunctionTool(divide_numbers, description="Divide a by b, returns error if b is zero"),
]

# Define a custom agent that uses the model client with tools
class MathTutorAgent(Agent):
    """Math tutor agent with tool calling capabilities - automatically instrumented by runtime."""

    def __init__(
        self,
        model_client: OpenAIChatCompletionClient,
        tools: list[FunctionTool],
        system_message: str,
    ):
        self._model_client = model_client
        self._tools = tools
        self._system_message = system_message
        self._conversation_history: list[LLMMessage] = []

    async def on_message(self, message: UserMessage, ctx: MessageContext) -> AssistantMessage:
        current_span = trace.get_current_span()
        current_span.set_attribute("gen_ai.agent.name", "MathTutorAgent")

        # Add system message if this is the first message
        if not self._conversation_history:
            self._conversation_history.append(
                UserMessage(content=self._system_message, source="system")
            )

        # Add user message to history
        self._conversation_history.append(message)

        # Call LLM with tools
        result = await self._model_client.create(
            messages=self._conversation_history,
            tools=self._tools,
        )

        # Handle tool calls in a loop
        while result.content:
            has_tool_calls = any(
                hasattr(item, 'id') and hasattr(item, 'name')
                for item in (result.content if isinstance(result.content, list) else [result.content])
            )

            if not has_tool_calls:
                break

            tool_results = []
            for item in (result.content if isinstance(result.content, list) else [result.content]):
                if hasattr(item, 'id') and hasattr(item, 'name'):
                    tool_name = item.name
                    tool_args = item.arguments

                    tool_result = None
                    for tool in self._tools:
                        if tool.name == tool_name:
                            cancellation_token = CancellationToken()
                            if isinstance(tool_args, str):
                                tool_args = json.loads(tool_args)
                            with trace.get_tracer(__name__).start_as_current_span(f"tool.{tool_name}") as tool_span:
                                tool_span.set_attribute("gen_ai.tool.name", tool_name)
                                tool_result = await tool.run_json(tool_args, cancellation_token)
                            break

                    tool_results.append(
                        FunctionExecutionResult(
                            content=str(tool_result),
                            call_id=item.id
                        )
                    )

            # If we executed tools, get the next response
            if tool_results:
                # Add assistant message and tool results to conversation
                self._conversation_history.append(AssistantMessage(content=result.content, source="assistant"))
                self._conversation_history.append(FunctionExecutionResultMessage(content=tool_results))

                # Get next response - automatically traced
                result = await self._model_client.create(
                    messages=self._conversation_history,
                    tools=self._tools,
                )
            else:
                break

        # Create final assistant message
        if isinstance(result.content, list):
            response_text = " ".join(str(item) for item in result.content)
        else:
            response_text = str(result.content)

        assistant_message = AssistantMessage(content=response_text, source="assistant")
        self._conversation_history.append(assistant_message)

        return assistant_message

# Global runtime
runtime: SingleThreadedAgentRuntime | None = None
agent_id: AgentId | None = None

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Lifespan context manager for startup and shutdown events."""
    global runtime, agent_id

    # Startup: Create and start runtime with NATIVE INSTRUMENTATION enabled
    runtime = SingleThreadedAgentRuntime(tracer_provider=tracer_provider)

    # System message for the agent
    system_message = """You are a helpful math tutor who can perform calculations using the provided tools.

When a user asks a math question:
1. Identify what calculation is needed
2. Use the appropriate tool to compute the answer
3. Explain the result clearly

Available tools:
- add_numbers: Add two numbers
- subtract_numbers: Subtract two numbers
- multiply_numbers: Multiply two numbers
- divide_numbers: Divide two numbers

Always use the tools to perform calculations. Do not calculate manually."""

    # Factory function that creates a new agent instance each time
    def create_math_agent():
        http_client = httpx.AsyncClient()
        model_client = OpenAIChatCompletionClient(
            model=model_name,
            api_key=openai_api_key,
            http_client=http_client,
        )
        return MathTutorAgent(
            model_client=model_client,
            tools=tools,
            system_message=system_message,
        )

    # Register the agent with the runtime - this enables automatic instrumentation!
    agent_type = "math_tutor"
    await runtime.register_factory(
        type=agent_type,
        agent_factory=create_math_agent,
        expected_class=MathTutorAgent,
    )
    agent_id = AgentId(agent_type, "default")

    # Start the runtime
    runtime.start()

    yield

    # Shutdown: Stop runtime
    if runtime:
        await runtime.stop()

# Initialize FastAPI with lifespan
app = FastAPI(title="AutoGen Core Math Tutor", lifespan=lifespan)

@app.post("/chat", response_model=PromptResponse)
async def chat(request: PromptRequest):
    try:
        user_message = UserMessage(content=request.prompt, source="user")
        response = await runtime.send_message(user_message, agent_id)
        response_text = " ".join(str(i) for i in response.content) if isinstance(response.content, list) else str(response.content)
        return PromptResponse(response=response_text)
    except Exception as e:
        import traceback
        return PromptResponse(response=f"Error: {str(e)}\n{traceback.format_exc()}")
