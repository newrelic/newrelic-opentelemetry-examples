#!/bin/bash

echo "GET ${1}/fibonacci?num=5"
curl "${1}/fibonacci?num=5" || true
echo

echo "GET ${1}/fibonacci?num=283"
curl "${1}/fibonacci?num=283" || true
echo

echo "GET ${1}/fibonacci?num=10"
curl "${1}/fibonacci?num=10" || true
echo

echo "GET ${1}/fibonacci?num=90"
curl "${1}/fibonacci?num=90" || true
echo

echo "GET ${1}/fibonacci?num=0"
curl "${1}/fibonacci?num=0" || true
echo
