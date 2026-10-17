import os
import random
import time
import urllib.request
import urllib.parse

TARGET_URL = os.getenv("TARGET_URL", "http://localhost:8080")

PROMPTS = [
    "What is OpenTelemetry?",
    "Explain observability in one sentence.",
    "What is the difference between metrics, traces, and logs?",
    "How does distributed tracing work?",
    "What is New Relic used for?",
    "Explain the concept of service level objectives.",
    "What are the four golden signals of monitoring?",
    "How do you instrument a Python application with OpenTelemetry?",
    "What is the difference between synthetic and real user monitoring?",
    "Describe the OpenTelemetry Collector in a few sentences.",
]

# Include one invalid (empty) prompt to generate error telemetry
PROMPTS_WITH_ERRORS = PROMPTS + [""]


def send_request(prompt):
    params = urllib.parse.urlencode({"prompt": prompt})
    url = f"{TARGET_URL}/chat?{params}"
    try:
        req = urllib.request.Request(url)
        with urllib.request.urlopen(req, timeout=60) as resp:
            print(f"[{resp.status}] prompt='{prompt[:50]}...'")
    except Exception as e:
        print(f"[ERROR] prompt='{prompt[:50]}': {e}")


if __name__ == "__main__":
    # Wait for app to start
    time.sleep(10)
    print(f"Starting load generator against {TARGET_URL}")

    while True:
        prompt = random.choice(PROMPTS_WITH_ERRORS)
        send_request(prompt)
        # Wait 15-30 seconds between requests to avoid excessive OpenAI API costs
        time.sleep(random.uniform(15, 30))
