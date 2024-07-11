#!/bin/bash

while true;
do
	telemetrygen traces --traces 10 --otlp-insecure --otlp-attributes='recipe="newrelic-tail-sampling"' --otlp-attributes='service.name="critical-service"' &
	telemetrygen traces --traces 10 --otlp-insecure --otlp-attributes='recipe="newrelic-tail-sampling"' --otlp-attributes='service.name="non-critical-service"' &
	sleep 5
done

exit 0


