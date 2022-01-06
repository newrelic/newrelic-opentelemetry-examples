#!/bin/bash

while :
do
  echo "Calling appathon-dotnet"
  ./call-app.sh http://localhost:8080 || true

  echo "Calling appathon-java"
  ./call-app.sh http://localhost:8081 || true

  sleep 2
done