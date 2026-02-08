@echo off
chcp 65001 >nul
setlocal
pushd %~dp0
set BASEDIR=%CD%

set MYSQL_ROOT=%BASEDIR%\runtime\mysql
set MYSQL_BIN=
for /f "delims=" %%f in ('dir /b /s "%MYSQL_ROOT%\mysql.exe" 2^>nul') do (
  set MYSQL_EXE=%%f
  goto found_mysql
)
:found_mysql
if not defined MYSQL_EXE (
  echo [ERROR] 未找到 MySQL 客户端，请先将 MySQL 解压到 runtime\mysql
  exit /b 1
)
for %%d in ("%MYSQL_EXE%") do set MYSQL_BIN=%%~dpd

set PATH=%MYSQL_BIN%;%PATH%

echo [INFO] 检测 MySQL 是否已启动...
"%MYSQL_BIN%\mysqladmin.exe" -h 127.0.0.1 -P 3306 -u root ping >nul 2>nul
if not %ERRORLEVEL%==0 (
  echo [ERROR] MySQL 未启动，请先运行 start-local.bat（或自行启动 MySQL）
  exit /b 1
)

if not exist "%BASEDIR%\runtime\scripts\reset-admin-password.sql" (
  echo [ERROR] 找不到脚本 runtime\scripts\reset-admin-password.sql
  exit /b 1
)

echo [INFO] 重置账号 admin 的登录密码为 123456...
"%MYSQL_EXE%" -h 127.0.0.1 -P 3306 -u root < "%BASEDIR%\runtime\scripts\reset-admin-password.sql"
if not %ERRORLEVEL%==0 (
  echo [ERROR] 执行失败，请检查 MySQL 日志与连接信息
  exit /b 1
)

echo [INFO] 完成：admin/123456
popd
endlocal

