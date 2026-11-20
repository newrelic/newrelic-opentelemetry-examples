#!/usr/bin/env python3
"""Print the JSON build matrix for the "Build examples" workflow.

Every directory with a Dockerfile is built with `docker build`. Examples
without a Dockerfile are listed in NATIVE. A build runs when a changed file is
under one of its paths; with no changed files given, everything runs.

Usage: plan-builds.py [--changed FILE ...]
"""
import json
import os
import subprocess
import sys

# Dockerfiles whose build context isn't their own directory.
CONTEXT = {
    "reference-architectures/operator/apps/java": "getting-started-guides/java",
    "reference-architectures/operator/apps/python": "getting-started-guides/python",
}

# Examples without a Dockerfile, built in the image their toolchain needs.
NATIVE = [
    {
        "name": "other-examples/serverless/aws-lambda/java",
        "dir": "other-examples/serverless/aws-lambda/java/ExampleFunction",
        # The Gradle 5 wrapper doesn't run on JDK 17+.
        "image": "eclipse-temurin:11",
        "command": "sh gradlew --no-daemon build",
    },
    {
        "name": "other-examples/serverless/aws-lambda/dotnet",
        "dir": "other-examples/serverless/aws-lambda/dotnet/src",
        "image": "mcr.microsoft.com/dotnet/sdk:10.0",
        "command": "dotnet build",
    },
    {
        "name": "other-examples/serverless/azure-functions/node/http-trigger-app",
        "dir": "other-examples/serverless/azure-functions/node/http-trigger-app",
        "image": "node:24",
        "command": "npm ci",
    },
]

# Builds that are known to fail. They still run, but don't fail the workflow.
# Remove an entry once the example is fixed.
BROKEN = {
    # .NET 6's Debian bullseye base image can no longer install packages.
    "other-examples/dotnet/agent-nr-config",
    # Mixes OpenTelemetry JS 2.x and 3.x packages, and uses the 1.x API.
    "other-examples/serverless/azure-functions/node/http-trigger-app",
}


def builds():
    dockerfiles = subprocess.run(
        ["git", "ls-files", "Dockerfile", "**/Dockerfile"], capture_output=True, text=True, check=True
    ).stdout.split()
    for df in sorted(dockerfiles):
        d = os.path.dirname(df)
        context = CONTEXT.get(d, d)
        yield {
            "name": d,
            "paths": sorted({d, context}),
            "image": "",
            "dir": ".",
            "command": f"docker build --file {df} {context}",
        }
    for n in NATIVE:
        yield {"paths": [n["dir"]], **n}


def main():
    changed = sys.argv[2:] if sys.argv[1:2] == ["--changed"] else None
    # Changes to the workflow or its scripts rebuild everything.
    rebuild_all = changed is None or any(f.startswith(".github/") for f in changed)
    matrix = []
    for b in builds():
        if rebuild_all or any(f == p or f.startswith(p + "/") for f in changed for p in b["paths"]):
            matrix.append({**b, "broken": b["name"] in BROKEN})
    print(json.dumps(matrix))


if __name__ == "__main__":
    main()
