param(
    [Parameter(Mandatory = $true)]
    [string]$Image,

    [string]$Platforms = 'linux/arm64',
    [string]$Builder = 'myyuyin-builder'
)
$ErrorActionPreference = 'Stop'
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw '未找到 Docker CLI。请先安装 Docker Desktop 或使用已安装 buildx 的构建机。'
}
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$exists = docker buildx inspect $Builder 2>$null
if ($LASTEXITCODE -ne 0) {
    docker buildx create --name $Builder --use | Out-Null
} else {
    docker buildx use $Builder | Out-Null
}
docker buildx build --platform $Platforms -f docker/Dockerfile -t $Image --push .
if ($LASTEXITCODE -ne 0) {
    throw '镜像构建或推送失败。'
}
Write-Host "镜像已推送: $Image"
Write-Host "将 myyuyin-assistant/docker-compose.yml 中的 APP_SERVER_IMAGE 设置为该地址，再提交到你的 Umbrel 应用仓库。该脚本默认只构建树莓派使用的 linux/arm64。"
