#!/bin/bash

while :
do
  echo "Calling fibonacci-ruby"
  ./call-app.sh http://localhost:8080 || true
  echo

  sleep 2
done
