$ENDPOINT="http://localhost:5000"

while ($true) {
    Write-Output "Calling fibonacci-python"
    & ".\call-app.ps1"
    Start-Sleep -s 2
}
