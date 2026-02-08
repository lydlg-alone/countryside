@echo off
chcp 65001 >nul
setlocal EnableExtensions
set BASEDIR=%~dp0
set MYSQL_HOME=
set MYSQL_BIN=
for /f "delims=" %%f in ('dir /b /s "%BASEDIR%runtime\mysql\mysqladmin.exe" 2^>nul') do (
  set MYSQL_BIN=%%~dpf
  goto found_mysql
)
:found_mysql
if not defined MYSQL_BIN (
  echo [WARN] 未找到 mysqladmin.exe，跳过停止 MySQL
  goto end
)
for %%d in ("%MYSQL_BIN%..") do set MYSQL_HOME=%%~fd
set PATH=%MYSQL_BIN%;%PATH%

echo [INFO] 停止后端...
powershell -NoProfile -Command "Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -match 'village-admin-system-.*\.jar' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }" >nul 2>nul

echo [INFO] 停止 MySQL...
"%MYSQL_BIN%\mysqladmin.exe" -h 127.0.0.1 -P 3306 -u root shutdown >nul 2>nul

:end
endlocal
