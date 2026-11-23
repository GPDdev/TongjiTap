$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
New-Item -ItemType Directory -Force tools/core-tests | Out-Null
$cp = 'tools/junit-4.13.2.jar;tools/hamcrest-core-1.3.jar'
$sources = @(Get-ChildItem app/src/main/java/org/hachile/tongjitap -Filter '*.java' -ErrorAction SilentlyContinue | Where-Object Name -In 'ClickStep.java','ClickPlan.java','SequenceRunner.java','ColorRule.java','ScreenshotSelection.java' | ForEach-Object FullName)
$sources += @(Get-ChildItem app/src/test/java/org/hachile/tongjitap -Filter '*.java' | ForEach-Object FullName)
& javac -encoding UTF-8 -cp $cp -d tools/core-tests @sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java -cp "tools/core-tests;$cp" org.junit.runner.JUnitCore org.hachile.tongjitap.SequenceRunnerTest org.hachile.tongjitap.ColorRuleTest org.hachile.tongjitap.ScreenshotSelectionTest
exit $LASTEXITCODE
