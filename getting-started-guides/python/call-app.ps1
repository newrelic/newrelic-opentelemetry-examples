Write-Output "GET $ENDPOINT/fibonacci/5"
Invoke-WebRequest -Uri "$ENDPOINT/fibonacci/5" | Select-Object -Expand Content

Write-Output "GET $ENDPOINT/fibonacci/283"
Invoke-WebRequest -Uri "$ENDPOINT/fibonacci/283" | Select-Object -Expand Content

Write-Output "GET $ENDPOINT/fibonacci/10"
Invoke-WebRequest -Uri "$ENDPOINT/fibonacci/10" | Select-Object -Expand Content

Write-Output "GET $ENDPOINT/fibonacci/90"
Invoke-WebRequest -Uri "$ENDPOINT/fibonacci/90" | Select-Object -Expand Content

Write-Output "GET $ENDPOINT/fibonacci/0"
Invoke-WebRequest -Uri "$ENDPOINT/fibonacci/0" | Select-Object -Expand Content
