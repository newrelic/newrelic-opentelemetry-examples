#!/bin/bash

while :
do
  echo "Calling appathon-dotnet"
  ./call-app.sh http://localhost:8080 || true
  echo

  echo "Calling appathon-java"
  ./call-app.sh http://localhost:8081 || true
  echo

  echo "Calling appathon-javascript"
  ./call-app.sh http://localhost:8082 || true
  echo

  sleep 2
done