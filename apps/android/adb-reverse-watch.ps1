$ErrorActionPreference = "SilentlyContinue"

# Keep the physical-device development tunnel alive. Android removes reverse
# mappings whenever the USB/ADB transport reconnects, so re-apply only when a
# device is online. The backend remains bound to localhost and is never exposed
# to the LAN.
$mutex = New-Object System.Threading.Mutex($false, "CereBroAdbReverse8000")
if (-not $mutex.WaitOne(0)) {
    exit 0
}

try {
    while ($true) {
        $state = (& adb get-state 2>$null)
        if ($state -eq "device") {
            & adb reverse tcp:8000 tcp:8000 2>$null | Out-Null
        }
        Start-Sleep -Seconds 2
    }
}
finally {
    $mutex.ReleaseMutex()
    $mutex.Dispose()
}
