$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $root 'assistant-server\target\assistant-server-1.0.0.jar'
if (-not (Test-Path -LiteralPath $jar)) {
    & (Join-Path $PSScriptRoot 'build.ps1')
}

$envFile = Join-Path $root '.env'
if (Test-Path -LiteralPath $envFile) {
    Get-Content -LiteralPath $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith('#')) {
            $parts = $line.Split('=', 2)
            if ($parts.Count -eq 2 -and -not [Environment]::GetEnvironmentVariable($parts[0])) {
                [Environment]::SetEnvironmentVariable($parts[0], $parts[1])
            }
        }
    }
}

foreach ($name in @('DB_HOST', 'DB_USER', 'DB_PASSWORD')) {
    if (-not [Environment]::GetEnvironmentVariable($name)) {
        throw "缺少 $name，请复制 .env.example 为 .env 并填写数据库连接信息。"
    }
}
if (-not $env:DB_NAME) { $env:DB_NAME = 'myyuyinzhushou' }
if (-not $env:DB_PORT) { $env:DB_PORT = '3306' }
if (-not $env:VOSK_MODEL_PATH) { $env:VOSK_MODEL_PATH = '.\models\vosk-model-small-cn' }
if (-not $env:TTS_OUTPUT_DIR) { $env:TTS_OUTPUT_DIR = Join-Path $env:TEMP 'myyuyin-tts' }
java -jar $jar
