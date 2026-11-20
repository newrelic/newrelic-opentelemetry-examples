#!/usr/bin/env python3
"""Validate every Collector config in the repo against the image that runs it.

For each config, this runs `<image> validate` and then starts the Collector
briefly to catch deprecation warnings, which `validate` doesn't report.

Configs are found in plain YAML files, Kubernetes ConfigMaps, and
OpenTelemetryCollector resources. The image comes from the same Kubernetes
file, or from the Docker Compose service that mounts the config.

Usage: check-collector-configs.py [--changed FILE ...]
With --changed, only configs in the same directory as a changed file are
checked, which covers edits to the config, its Compose file, its .env, or
the manifest that sets its image. Otherwise every tracked YAML file is.
"""
import os
import re
import subprocess
import sys
import tempfile
import time

import yaml

# Configs that don't run in a Collector image this script can start.
SKIP = {
    "other-examples/serverless/aws-lambda/dotnet/src/collector.yaml": "runs in the OpenTelemetry Lambda layer",
    "other-examples/serverless/aws-lambda/java/collector.yaml": "runs in the OpenTelemetry Lambda layer",
}

COLLECTOR_IMAGE = re.compile(r"(opentelemetry-collector|nrdot-collector)")

# Placeholder values for environment variables a config references without a
# default, chosen by variable name so that validation passes.
PLACEHOLDERS = [
    (r"ENDPOINT", "https://otlp.example.com:443"),
    (r"(_IP|_HOST)$", "127.0.0.1"),
    (r"INTERVAL", "30s"),
    (r"VERBOSITY", "basic"),
    (r"(BROKER|BOOTSTRAP)", "127.0.0.1:9092"),
    (r"RATIO", "0.1"),
    (r"", "placeholder"),
]
ENV_REF = re.compile(r"\$\{(?:env:)?([A-Za-z_][A-Za-z0-9_]*)(:-[^}]*)?\}|\$([A-Za-z_][A-Za-z0-9_]*)")


def is_config(doc):
    return isinstance(doc, dict) and "service" in doc and ("receivers" in doc or "exporters" in doc)


def load_all(path):
    try:
        with open(path) as f:
            return [d for d in yaml.safe_load_all(f) if d]
    except (yaml.YAMLError, UnicodeDecodeError):
        return []


def images_in(docs):
    """Collector images used by containers in a set of Kubernetes documents."""
    found = []

    def walk(node):
        if isinstance(node, dict):
            img = node.get("image")
            if isinstance(img, str) and COLLECTOR_IMAGE.search(img):
                found.append(img)
            for v in node.values():
                walk(v)
        elif isinstance(node, list):
            for v in node:
                walk(v)

    walk(docs)
    return sorted(set(found))


def resolve(value, env):
    """Expand ${VAR:-default} and ${VAR} using env."""
    def sub(m):
        name, default = m.group(1), m.group(2)
        return env.get(name) or (default[2:] if default else "")
    return re.sub(r"\$\{([A-Za-z_][A-Za-z0-9_]*)(:-[^}]*)?\}", sub, value)


def dotenv(directory):
    env = {}
    for name in (".env", ".env.example"):
        p = os.path.join(directory, name)
        if os.path.exists(p):
            for line in open(p):
                m = re.match(r"^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$", line)
                if m and m.group(1) not in env and m.group(2).strip():
                    env[m.group(1)] = m.group(2).strip().strip("'\"")
    return env


def compose_image(path, compose_files):
    """Image of the Compose service that mounts the config at path."""
    target = os.path.realpath(path)
    for cf in compose_files:
        cdir = os.path.dirname(cf)
        for doc in load_all(cf):
            for svc in (doc.get("services") or {}).values():
                for vol in svc.get("volumes") or []:
                    src = vol.split(":")[0] if isinstance(vol, str) else (vol or {}).get("source", "")
                    if src and os.path.realpath(os.path.join(cdir, src)) == target and svc.get("image"):
                        return resolve(svc["image"], dotenv(cdir))
    return None


def find_configs(paths, compose_files):
    """Yield (path, config dict, image or None, error or None)."""
    for path in paths:
        docs = load_all(path)
        for doc in docs:
            if is_config(doc):
                yield path, doc, compose_image(path, compose_files), None
            elif isinstance(doc, dict) and doc.get("kind") == "ConfigMap":
                for value in (doc.get("data") or {}).values():
                    try:
                        cfg = yaml.safe_load(value)
                    except yaml.YAMLError:
                        continue
                    if is_config(cfg):
                        # The Collector workload may be defined in a sibling file.
                        imgs = images_in(docs) or images_in(
                            [load_all(os.path.join(os.path.dirname(path), f)) for f in sorted(os.listdir(os.path.dirname(path) or "."))
                             if f.endswith((".yaml", ".yml"))])
                        err = None if len(imgs) == 1 else f"expected one Collector image in the directory, found {imgs or 'none'}"
                        yield path, cfg, imgs[0] if len(imgs) == 1 else None, err
            elif isinstance(doc, dict) and doc.get("kind") == "OpenTelemetryCollector":
                cfg = doc["spec"].get("config")
                cfg = yaml.safe_load(cfg) if isinstance(cfg, str) else cfg
                img = doc["spec"].get("image")
                yield path, cfg, img, None if img else "OpenTelemetryCollector has no spec.image"


def placeholder_env(text):
    env = {}
    for m in ENV_REF.finditer(text):
        name, default = m.group(1) or m.group(3), m.group(2)
        if default or name in env:
            continue
        env[name] = next(v for pat, v in PLACEHOLDERS if re.search(pat, name))
    return env


def docker(args, **kw):
    return subprocess.run(["docker"] + args, capture_output=True, text=True, **kw)


def check(path, cfg, image, workdir):
    """Return a list of problems with one config."""
    text = yaml.safe_dump(cfg)
    cfg_file = os.path.join(workdir, "config.yaml")
    with open(cfg_file, "w") as f:
        f.write(text)
    env_args = [a for k, v in placeholder_env(text).items() for a in ("-e", f"{k}={v}")]
    mounts = ["-v", f"{workdir}:/cfg:ro", "-v", f"{workdir}/hostfs:/hostfs:ro"]

    r = docker(["run", "--rm"] + mounts + env_args + [image, "validate", "--config=/cfg/config.yaml"])
    if r.returncode != 0:
        errors = [l for l in (r.stdout + r.stderr).splitlines() if "rror" in l]
        return ["validate failed: " + " | ".join(errors[-3:] or ["(no output)"])]

    # validate doesn't report deprecations; they're logged at startup.
    name = f"check-collector-{os.getpid()}"
    docker(["run", "-d", "--name", name] + mounts + env_args + [image, "--config=/cfg/config.yaml"])
    try:
        for _ in range(30):
            logs = docker(["logs", name]).stderr + docker(["logs", name]).stdout
            running = docker(["inspect", "-f", "{{.State.Running}}", name]).stdout.strip() == "true"
            if "Everything is ready" in logs or not running:
                break
            time.sleep(1)
        logs = docker(["logs", name])
        logs = logs.stdout + logs.stderr
    finally:
        docker(["rm", "-f", name])
    deprecations = sorted({m.group(0) for m in re.finditer(r"[^\t\n]*deprecated[^\t\n]*", logs)})
    return [f"deprecated: {d}" for d in deprecations]


def main():
    paths = subprocess.run(
        ["git", "ls-files", "*.yaml", "*.yml"], capture_output=True, text=True, check=True
    ).stdout.split()
    paths = [p for p in paths if not p.startswith(".github/")]
    if sys.argv[1:2] == ["--changed"]:
        changed = sys.argv[2:]
        if not any(f.startswith(".github/") for f in changed):
            dirs = {os.path.dirname(f) for f in changed}
            paths = [p for p in paths if os.path.dirname(p) in dirs]
    compose_files = [p for p in subprocess.run(
        ["git", "ls-files", "*compose*.yaml", "*compose*.yml"], capture_output=True, text=True, check=True
    ).stdout.split()]

    failures = 0
    with tempfile.TemporaryDirectory() as workdir:
        os.makedirs(os.path.join(workdir, "hostfs"))
        os.chmod(workdir, 0o755)
        for path, cfg, image, err in find_configs(paths, compose_files):
            if path in SKIP:
                print(f"skip  {path} ({SKIP[path]})")
                continue
            if err or not image:
                problems = [err or "no Docker Compose service with an image mounts this config"]
            else:
                problems = check(path, cfg, image, workdir)
            status = "FAIL" if problems else "ok"
            print(f"{status:5} {path}" + (f" ({image})" if image else ""))
            for p in problems:
                print(f"      {p}")
            failures += bool(problems)
    if failures:
        print(f"\n{failures} config(s) failed")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
