from flask import Flask, jsonify, request
import logging
import os
from openai import OpenAI
from opentelemetry import metrics, trace
from opentelemetry.trace.status import Status, StatusCode
from opentelemetry.instrumentation.openai_v2 import OpenAIInstrumentor

# Instrument OpenAI SDK - automatically captures gen_ai spans, token metrics, and events
OpenAIInstrumentor().instrument()

chat_counter = metrics.get_meter("opentelemetry.instrumentation.custom").create_counter(
    "chat.invocations",
    unit="1",
    description="Measures the number of times the chat endpoint is invoked.",
)

logging.basicConfig(level=logging.DEBUG)

app = Flask(__name__)
client = OpenAI()

CHAT_MODEL = os.getenv("CHAT_MODEL", "gpt-4o-mini")


@app.route("/chat")
@trace.get_tracer("opentelemetry.instrumentation.custom").start_as_current_span("/chat")
def chat():
    prompt = request.args.get("prompt", "").strip()
    error_message = "prompt must be a non-empty string."
    trace.get_current_span().set_attribute("chat.prompt", prompt)

    try:
        assert len(prompt) > 0, error_message

        response = client.chat.completions.create(
            model=CHAT_MODEL,
            messages=[{"role": "user", "content": prompt}],
        )

        result = response.choices[0].message.content
        model = response.model
        usage = response.usage

        trace.get_current_span().set_attribute("chat.response.model", model)
        trace.get_current_span().set_attribute("chat.response.tokens.prompt", usage.prompt_tokens)
        trace.get_current_span().set_attribute("chat.response.tokens.completion", usage.completion_tokens)
        trace.get_current_span().set_attribute("chat.response.tokens.total", usage.total_tokens)

        chat_counter.add(1, {"chat.valid.prompt": True})
        logging.info("Chat completion for prompt='%s' using model=%s (tokens: %d)", prompt, model, usage.total_tokens)

        return jsonify(
            prompt=prompt,
            response=result,
            model=model,
            usage={
                "prompt_tokens": usage.prompt_tokens,
                "completion_tokens": usage.completion_tokens,
                "total_tokens": usage.total_tokens,
            },
        )

    except AssertionError:
        trace.get_current_span().record_exception(
            exception=Exception(error_message),
            attributes={"exception.type": "AssertionError", "exception.message": error_message},
        )
        trace.get_current_span().set_status(Status(StatusCode.ERROR, error_message))
        chat_counter.add(1, {"chat.valid.prompt": False})
        logging.error("Failed to process chat: empty prompt")
        return jsonify({"message": error_message}), 400

    except Exception as e:
        error_msg = str(e)
        trace.get_current_span().record_exception(
            exception=e,
            attributes={"exception.type": type(e).__name__, "exception.message": error_msg},
        )
        trace.get_current_span().set_status(Status(StatusCode.ERROR, error_msg))
        chat_counter.add(1, {"chat.valid.prompt": False})
        logging.error("Failed to process chat for prompt='%s': %s", prompt, error_msg)
        return jsonify({"message": f"Error: {error_msg}"}), 500


app.run(host="0.0.0.0", port=8080)
