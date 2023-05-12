Write-Output "GET $ENDPOINT/fibonacci?num=5"
Invoke-WebRequest -Uri "$ENDPOINT/fibonacci?num=5" | Select-Object -Expand Content

Write-Output "GET $ENDPOINT/fibonacci?num=283"
Invoke-WebRequest -Uri "$ENDPOINT/fibonacci?num=283" | Select-Object -Expand Content

Write-Output "GET $ENDPOINT/fibonacci?num=10"
Invoke-WebRequest -Uri "$ENDPOINT/fibonacci?num=10" | Select-Object -Expand Content

Write-Output "GET $ENDPOINT/fibonacci?num=90"
Invoke-WebRequest -Uri "$ENDPOINT/fibonacci?num=90" | Select-Object -Expand Content

Write-Output "GET $ENDPOINT/fibonacci?num=0"
Invoke-WebRequest -Uri "$ENDPOINT/fibonacci?num=0" | Select-Object -Expand Content
