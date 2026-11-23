$ErrorActionPreference = 'Stop'
& $PSScriptRoot/gradle.ps1 :app:testDebugUnitTest --console plain
exit $LASTEXITCODE
