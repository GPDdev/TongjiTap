$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
& python scripts/create-signing-key.py
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $PSScriptRoot/gradle.ps1 :app:testDebugUnitTest :app:lintRelease :app:assembleRelease --console plain
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
New-Item -ItemType Directory -Force dist | Out-Null
$apk = Join-Path (Get-Location) 'dist/tongjitap-1.0.0.apk'
Copy-Item -LiteralPath 'app/build/outputs/apk/release/app-release.apk' -Destination $apk
& ./tools/android-sdk/build-tools/35.0.0/apksigner.bat verify --verbose --print-certs $apk
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& ./tools/android-sdk/build-tools/35.0.0/zipalign.exe -c -v 4 $apk | Select-Object -Last 1
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$digest = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
Set-Content -LiteralPath "$apk.sha256" -Value "$digest  tongjitap-1.0.0.apk" -Encoding ascii
Write-Output "Built and verified: $apk"
