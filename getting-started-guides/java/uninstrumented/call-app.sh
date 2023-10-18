#!/bin/bash

echo "GET ${1}/fibonacci?n=5"
curl "${1}/fibonacci?n=5" || true
echo

echo "GET ${1}/fibonacci?n=283"
curl "${1}/fibonacci?n=283" || true
echo

echo "GET ${1}/fibonacci?n=10"
curl "${1}/fibonacci?n=10" || true
echo

echo "GET ${1}/fibonacci?n=90"
curl "${1}/fibonacci?n=90" || true
echo

echo "GET ${1}/fibonacci?n=0"
curl "${1}/fibonacci?n=0" || true
echo