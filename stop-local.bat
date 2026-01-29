@echo off
setlocal
set BASEDIR=%~dp0
set MYSQL_HOME=
set MYSQL_BIN=
for /r "%BASEDIR%runtime\mysql" %%f in (mysqladmin.exe) do (
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
for /f "tokens=2 delims=," %%p in ('wmic process where "CommandLine like '%%village-admin-system-0.1.0-SNAPSHOT-shaded.jar%%'" get ProcessId /format:csv ^| findstr /r /v "^$"') do (
  taskkill /F /PID %%p >nul 2>nul
)

echo [INFO] 停止 MySQL...
"%MYSQL_BIN%\mysqladmin.exe" -h 127.0.0.1 -P 3306 -u root shutdown >nul 2>nul

:end
endlocal
