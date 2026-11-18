#!/usr/bin/env python3
# Adapted from getting-started-guides/supporting-files/loadgenerator.py for
# this example's two apps, reached via the port-forwards from the README
# ("Viewing your data") instead of Docker Compose service hostnames.
#
# ~10% of requests use an out-of-range `n` (the apps return an error span for
# n < 1 or n > 90), so error traces exist to check against the sampling
# policy.
import os, random, sys, signal, time

def signal_handler(signal, frame):
    print("\nCtrl-C received. Stopping load generator.")
    sys.exit(0)

signal.signal(signal.SIGINT, signal_handler)

ports = [8081]  # Service A (getting-started-java), which calls Service B (getting-started-python)

while True:
    n = random.randint(1, 100) if random.random() > 0.1 else 0
    for port in ports:
        os.system(f"curl http://localhost:{port}/fibonacci?n={n} > /dev/null 2>&1")
    time.sleep(0.1)
