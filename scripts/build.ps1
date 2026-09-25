$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
mvn -DskipTests package
Write-Host "服务端: $root\assistant-server\target\assistant-server-1.0.0.jar"
Write-Host "客户端: $root\assistant-client\target\assistant-client-1.0.0-jar-with-dependencies.jar"
