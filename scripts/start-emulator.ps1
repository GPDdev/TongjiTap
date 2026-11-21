param([ValidateSet(26, 36)][int]$Api = 26)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$env:ANDROID_HOME = Join-Path (Get-Location) 'tools/android-sdk'
$env:ANDROID_USER_HOME = Join-Path (Get-Location) 'tools/android-user'
$env:ANDROID_AVD_HOME = Join-Path (Get-Location) 'tools/avds'
$port = if ($Api -eq 26) { 5556 } else { 5558 }
$devices = & ./tools/android-sdk/platform-tools/adb.exe devices
if ($devices -match "emulator-$port\s") { throw "Port $port already has an emulator. No device was changed." }
$emulatorPath = Join-Path (Get-Location) 'tools/android-sdk/emulator/emulator.exe'
$emulator = Start-Process -FilePath $emulatorPath -ArgumentList '-avd',"LightTap$Api",'-port',"$port",'-no-window','-no-audio','-no-boot-anim','-gpu','swiftshader','-no-snapshot','-camera-back','none','-camera-front','none' -WindowStyle Hidden -RedirectStandardOutput "tools/emulator$Api.log" -RedirectStandardError "tools/emulator$Api.err.log" -PassThru
Write-Output "Started LightTap$Api as emulator-$port, PID $($emulator.Id). Wait for boot before running check-device.py."
