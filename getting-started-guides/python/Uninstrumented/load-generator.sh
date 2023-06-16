#!/bin/bash

while :
do
  echo "Calling getting-started-python"
  ./call-app.sh http://localhost:8080 || true
  echo

  sleep 2
done