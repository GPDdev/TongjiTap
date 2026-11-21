$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
$env:GRADLE_USER_HOME = Join-Path (Get-Location) 'tools/gradle-home'
$env:ANDROID_USER_HOME = Join-Path (Get-Location) 'tools/android-user'
if (Test-Path './gradlew.bat') {
    & ./gradlew.bat @args
} else {
    & ./tools/gradle-8.13/bin/gradle.bat @args
}
exit $LASTEXITCODE
