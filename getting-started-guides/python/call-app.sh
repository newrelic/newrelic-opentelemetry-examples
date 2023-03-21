#!/bin/bash

echo "GET ${1}/fibonacci/5"
curl "${1}/fibonacci/5" || true
echo

echo "GET ${1}/fibonacci/283"
curl "${1}/fibonacci/283" || true
echo

echo "GET ${1}/fibonacci/10"
curl "${1}/fibonacci/10" || true
echo

echo "GET ${1}/fibonacci/90"
curl "${1}/fibonacci/90" || true
echo

echo "GET ${1}/fibonacci/0"
curl "${1}/fibonacci/0" || true
echo
