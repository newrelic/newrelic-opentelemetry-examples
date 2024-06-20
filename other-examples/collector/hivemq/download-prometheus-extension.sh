#!/usr/bin/env bash

curl --silent --location https://github.com/hivemq/hivemq-prometheus-extension/releases/download/4.0.10/hivemq-prometheus-extension-4.0.10.zip \
  --output ./hivemq-prometheus-extension.zip
unzip ./hivemq-prometheus-extension.zip -d .
rm ./hivemq-prometheus-extension.zip
