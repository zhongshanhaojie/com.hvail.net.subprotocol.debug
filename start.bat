@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

REM ====== 按需改这里 ======
set LVCC_HOST=127.0.0.1
set LVCC_PORT=29001
set LVCC_DEVICE_ID=868089057672499
set LVCC_PORTS=10
set LVCC_HEARTBEAT=30
set LVCC_POWER=220
REM ========================

where python >nul 2>nul
if errorlevel 1 (
  echo 未找到 python，请先安装 Python 3.10+ 并加入 PATH。
  pause
  exit /b 1
)

echo 启动 LVCC 模拟器 -^> %LVCC_HOST%:%LVCC_PORT%  device=%LVCC_DEVICE_ID%
python -m lvcc_sim --host %LVCC_HOST% --port %LVCC_PORT% --device-id %LVCC_DEVICE_ID% --ports %LVCC_PORTS% --heartbeat %LVCC_HEARTBEAT% --power %LVCC_POWER% %*
set EXITCODE=%ERRORLEVEL%
if not "%EXITCODE%"=="0" pause
exit /b %EXITCODE%
