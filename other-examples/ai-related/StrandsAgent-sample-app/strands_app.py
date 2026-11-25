from strands import Agent, tool
from strands.telemetry import StrandsTelemetry
from strands.models.openai import OpenAIModel
from fastapi import FastAPI
from pydantic import BaseModel
from dotenv import load_dotenv, find_dotenv

# Load environment variables (override existing shell env vars)
_ = load_dotenv(find_dotenv(), override=True)


# --- Define tools ---

@tool
def add_numbers(a: int, b: int) -> str:
    """Add two numbers together and return the result.

    Args:
        a: The first number.
        b: The second number.
    """
    return f"{a} + {b} = {a + b}"


@tool
def subtract_numbers(a: int, b: int) -> str:
    """Subtract b from a and return the result.

    Args:
        a: The number to subtract from.
        b: The number to subtract.
    """
    return f"{a} - {b} = {a - b}"


@tool
def multiply_numbers(a: int, b: int) -> str:
    """Multiply two numbers together and return the result.

    Args:
        a: The first number.
        b: The second number.
    """
    return f"{a} * {b} = {a * b}"


@tool
def divide_numbers(a: int, b: int) -> str:
    """Divide a by b and return the result. Returns error if b is zero.

    Args:
        a: The dividend.
        b: The divisor.
    """
    if b == 0:
        return "Error: Cannot divide by zero"
    return f"{a} / {b} = {a / b}"


# --- Set up telemetry ---

strands_telemetry = StrandsTelemetry()
strands_telemetry.setup_otlp_exporter(endpoint="http://localhost:4318/v1/traces")
strands_telemetry.setup_meter(
    enable_console_exporter=False,
    enable_otlp_exporter=True,
)

# --- Initialize FastAPI app ---

app = FastAPI(
    title="Strands Math Tutor Agent",
    description="Math tutor agent with Strands tools and OpenTelemetry instrumentation",
)

# --- Request/Response models ---

class PromptRequest(BaseModel):
    prompt: str

class PromptResponse(BaseModel):
    response: str

# --- System prompt ---

system_prompt = """You are a helpful math tutor who can perform calculations using the provided tools.
When asked a math question, use the appropriate tool (add, subtract, multiply, divide) to compute the answer.
Always show your work and explain the steps clearly."""

# --- Create agent with tools ---

tools = [add_numbers, subtract_numbers, multiply_numbers, divide_numbers]

openai_model = OpenAIModel(model_id="gpt-4o")

agent = Agent(
    model=openai_model,
    system_prompt=system_prompt,
    tools=tools,
)


@app.get("/")
async def health_check():
    """Health check endpoint."""
    return {"status": "ok"}
    

@app.post("/prompt", response_model=PromptResponse)
async def prompt_agent(request: PromptRequest):
    """Send a prompt to the math tutor agent and get a response."""
    response = agent(request.prompt)
    return PromptResponse(response=str(response))


