# Bring the local dev stack up in YOUR terminal, so it survives across sessions.
#
# Why this exists: an assistant that starts the servers as background tasks loses them
# the moment its turn ends — they are tied to that turn's lifecycle. Run this yourself
# and the two services live in their own windows until you close them.
#
#   engine   :8000  (real coaching if services/engine/.env has OPENAI_API_KEY; mock otherwise)
#   platform :8100  (accounts, consent, wellness account side; dev.db holds the walkthrough data)
#
# Then it points the physical device's adb reverse tunnels at both. Usage, from the repo root:
#   ./scripts/dev-up.ps1

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$adb  = "C:/Users/pawan/AppData/Local/Android/Sdk/platform-tools/adb.exe"

# Dev-only shared JWT secret (docker-compose.yml). Both services must agree on it, or the
# platform's tokens won't validate at the engine. NOT a production value.
$jwt = "ZGV2LW9ubHktc2hhcmVkLXNlY3JldC1ub3QtZm9yLXByb2Q="

function Start-Service($name, $dir, $envs, $port) {
    $envLines = ($envs.GetEnumerator() | ForEach-Object { "`$env:$($_.Key)='$($_.Value)'" }) -join "; "
    $cmd = "cd '$dir'; $envLines; " +
           "uv run --python 3.12 --no-sync python -m uvicorn app.main:app --host 0.0.0.0 --port $port"
    Start-Process powershell -ArgumentList "-NoExit", "-Command", $cmd | Out-Null
    Write-Host "  $name -> http://localhost:$port  (own window)"
}

Write-Host "Starting CereBroZen dev stack..." -ForegroundColor Cyan
Start-Service "engine  " "$root/services/engine" `
    @{ PYTHONPATH = ".localdev" } 8000
Start-Service "platform" "$root/services/platform" `
    @{ PYTHONPATH = ".localdev"; JWT_SECRET = $jwt; DATABASE_URL = "sqlite+aiosqlite:///./dev.db" } 8100

Write-Host "Waiting for the engine to answer..." -NoNewline
for ($i = 0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 1
    try {
        if ((Invoke-WebRequest "http://localhost:8000/health" -UseBasicParsing -TimeoutSec 2).StatusCode -eq 200) {
            Write-Host " up." -ForegroundColor Green; break
        }
    } catch { Write-Host "." -NoNewline }
}

# Point the physical device at both services (harmless if no device is attached).
if (Test-Path $adb) {
    & $adb reverse tcp:8100 tcp:8100 2>$null
    & $adb reverse tcp:8000 tcp:8000 2>$null
    $devices = (& $adb devices | Select-String "`tdevice").Count
    if ($devices -gt 0) { Write-Host "Phone tunnels set (8000, 8100)." -ForegroundColor Green }
    else { Write-Host "No device attached — tunnels will apply when one is." -ForegroundColor DarkYellow }
} else {
    Write-Host "adb not found; skipping phone tunnels." -ForegroundColor DarkYellow
}

Write-Host "`nStack up. Close the two windows (or Ctrl+C in each) to stop it." -ForegroundColor Cyan
