"""
Shared LangChain multi-agent trip planner — no OTel or instrumentation
imports here.

run_openllmetry.py imports `run()` from this module and wraps it with the
OpenLLMetry (Traceloop) auto-instrumentor. Keeping this file
instrumentation-agnostic is what proves the gen_ai.* attribute mapping is
coming entirely from the collector's genainormalizerprocessor, not from
manual code here.

The agent/tool graph is deliberately shaped to exercise all four
span-relationship patterns:
  - agent -> tool:  WeatherAgent -> get_weather, PlacesAgent -> get_places
  - tool -> tool:   get_weather -> _celsius_to_fahrenheit (plain helper call)
  - agent -> agent: TripPlannerAgent delegates to WeatherAgent / PlacesAgent
  - tool -> agent:  ask_weather_agent / ask_places_agent are tools whose
                     bodies invoke a sub-agent — the standard LangChain
                     idiom for exposing an agent as a callable tool
"""

import os

from langchain.agents import create_agent
from langchain_core.tools import tool
from langchain_openai import ChatOpenAI


def _celsius_to_fahrenheit(celsius: float) -> float:
    return celsius * 9 / 5 + 32


@tool
def get_weather(city: str) -> str:
    """Look up the current weather for a city."""
    celsius = 22
    fahrenheit = _celsius_to_fahrenheit(celsius)
    return f"{city}: {celsius}C ({fahrenheit:.0f}F), sunny"


@tool
def get_places(city: str) -> str:
    """Look up popular points of interest for a city."""
    return f"{city}: old town square, riverside promenade, central museum"


def _build_llm() -> ChatOpenAI:
    return ChatOpenAI(model=os.getenv("LLM_MODEL", "gpt-4o"), temperature=0)


def build_weather_agent():
    return create_agent(
        model=_build_llm(),
        tools=[get_weather],
        system_prompt="You are a weather specialist. Use the get_weather tool to answer.",
        name="WeatherAgent",
    )


def build_places_agent():
    return create_agent(
        model=_build_llm(),
        tools=[get_places],
        system_prompt="You are a local guide. Use the get_places tool to answer.",
        name="PlacesAgent",
    )


weather_agent = build_weather_agent()
places_agent = build_places_agent()


@tool
def ask_weather_agent(query: str) -> str:
    """Delegate a weather question to the weather specialist agent."""
    result = weather_agent.invoke({"messages": [{"role": "user", "content": query}]})
    return result["messages"][-1].content


@tool
def ask_places_agent(query: str) -> str:
    """Delegate a points-of-interest question to the local guide agent."""
    result = places_agent.invoke({"messages": [{"role": "user", "content": query}]})
    return result["messages"][-1].content


def build_agent():
    return create_agent(
        model=_build_llm(),
        tools=[ask_weather_agent, ask_places_agent],
        system_prompt=(
            "You are a trip planning supervisor. For weather questions, delegate to "
            "ask_weather_agent. For points-of-interest questions, delegate to "
            "ask_places_agent. Combine their answers into a helpful trip summary."
        ),
        name="TripPlannerAgent",
    )


def run(query: str) -> str:
    agent = build_agent()
    result = agent.invoke({"messages": [{"role": "user", "content": query}]})
    return result["messages"][-1].content
