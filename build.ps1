# Compiles everything in src\ to out\, failing on any warning.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (Test-Path out) { Remove-Item -Recurse -Force out }
New-Item -ItemType Directory out | Out-Null
javac -Xlint:all -Werror --release 17 -d out (Get-ChildItem src\jongrai\*.java).FullName
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host "Built out\ - run: java -cp out jongrai.WebServer"
