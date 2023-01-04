#!/bin/bash

while :
do
  echo "Calling fibonacci-dotnet"
  ./call-app.sh http://localhost:5251 || true
  echo

  sleep 2
done
