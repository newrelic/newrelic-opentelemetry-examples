import json
import random
import time


def handler(event, context):
    params = (event or {}).get("queryStringParameters") or {}
    sleep_ms = int(params.get("sleepMs", random.randint(10, 800)))
    time.sleep(sleep_ms / 1000.0)
    return {
        "statusCode": 200,
        "body": json.dumps({"sleepMs": sleep_ms}),
    }
