#!/usr/bin/env python3
# Adapted from getting-started-guides/supporting-files/loadgenerator.py for
# this example's two apps, reached via the port-forwards from the README
# ("Viewing your data") instead of Docker Compose service hostnames.
import os, random, sys, signal, time

def signal_handler(signal, frame):
    print("\nCtrl-C received. Stopping load generator.")
    sys.exit(0)

signal.signal(signal.SIGINT, signal_handler)

ports = [8081, 8082]  # getting-started-java, getting-started-python

while True:
    n = random.randint(1, 100)
    for port in ports:
        os.system(f"curl http://localhost:{port}/fibonacci?n={n} > /dev/null 2>&1")
    time.sleep(1)
