$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $root 'assistant-client\target\assistant-client-1.0.0-jar-with-dependencies.jar'
if (-not (Test-Path -LiteralPath $jar)) {
    & (Join-Path $PSScriptRoot 'build.ps1')
}
java -jar $jar
