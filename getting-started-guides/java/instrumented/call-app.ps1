Write-Output "GET $ENDPOINT/fibonacci?n=5"
Invoke-WebRequest -Uri "$ENDPOINT/fibonacci?n=5" | Select-Object -Expand Content

Write-Output "GET $ENDPOINT/fibonacci?n=283"
Invoke-WebRequest -Uri "$ENDPOINT/fibonacci?n=283" | Select-Object -Expand Content

Write-Output "GET $ENDPOINT/fibonacci?n=10"
Invoke-WebRequest -Uri "$ENDPOINT/fibonacci?n=10" | Select-Object -Expand Content

Write-Output "GET $ENDPOINT/fibonacci?n=90"
Invoke-WebRequest -Uri "$ENDPOINT/fibonacci?n=90" | Select-Object -Expand Content

Write-Output "GET $ENDPOINT/fibonacci?n=0"
Invoke-WebRequest -Uri "$ENDPOINT/fibonacci?n=0" | Select-Object -Expand Content
