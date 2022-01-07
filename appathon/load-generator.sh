#!/bin/bash

while :
do
  echo "Calling appathon-dotnet"
  ./call-app.sh http://localhost:8080 || true
  echo

  echo "Calling appathon-java"
  ./call-app.sh http://localhost:8081 || true
  echo

  sleep 2
done