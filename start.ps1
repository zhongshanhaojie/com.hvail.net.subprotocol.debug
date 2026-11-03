# LVCC 设备模拟器一键启动（在本脚本所在目录运行）
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

# ====== 按需改这里 ======
$HostName = if ($env:LVCC_HOST) { $env:LVCC_HOST } else { '127.0.0.1' }
$Port     = if ($env:LVCC_PORT) { [int]$env:LVCC_PORT } else { 29001 }
$DeviceId = if ($env:LVCC_DEVICE_ID) { $env:LVCC_DEVICE_ID } else { '868089057672499' }
$Ports    = if ($env:LVCC_PORTS) { [int]$env:LVCC_PORTS } else { 10 }
$Heartbeat= if ($env:LVCC_HEARTBEAT) { [double]$env:LVCC_HEARTBEAT } else { 30 }
$Power    = if ($env:LVCC_POWER) { [double]$env:LVCC_POWER } else { 220 }
# ========================

$py = Get-Command python -ErrorAction SilentlyContinue
if (-not $py) {
  Write-Host '未找到 python，请先安装 Python 3.10+ 并加入 PATH。' -ForegroundColor Red
  exit 1
}

Write-Host "启动 LVCC 模拟器 -> ${HostName}:${Port}  device=$DeviceId"
& python -m lvcc_sim `
  --host $HostName `
  --port $Port `
  --device-id $DeviceId `
  --ports $Ports `
  --heartbeat $Heartbeat `
  --power $Power `
  @args
exit $LASTEXITCODE
