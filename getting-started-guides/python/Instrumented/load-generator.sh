#!/bin/bash

while :
do
  echo "Calling fibonacci-java"
  ./call-app.sh http://localhost:5000 || true
  echo

  sleep 2
done