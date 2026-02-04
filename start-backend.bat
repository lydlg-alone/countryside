@echo off
setlocal
pushd "%~dp0"
set "BASEDIR=%~dp0"
set "JAVA_HOME="
set "JAVA_EXE="
set "APP_LOGS=%BASEDIR%runtime\logs"
set "APP_OUT=%APP_LOGS%\app.log"
set "APP_ERR=%APP_LOGS%\app.err"

for /f "delims=" %%f in ('dir /b /s "%BASEDIR%\runtime\jre\bin\java.exe" 2^>nul') do (
  set JAVA_EXE=%%f
  goto found_java
)
for /f "delims=" %%f in ('dir /b /s "%BASEDIR%\runtime\jre\java.exe" 2^>nul') do (
  set JAVA_EXE=%%f
  goto found_java
)
:found_java
if not defined JAVA_EXE (
  echo [ERROR] 未找到 JRE，请将 JRE 解压到 runtime\jre
  call :fail
)
if not exist "%JAVA_EXE%" (
  echo [ERROR] 未找到 java.exe：%JAVA_EXE%
  call :fail
)
for %%d in ("%JAVA_EXE%") do set JAVA_BIN=%%~dpd
for %%d in ("%JAVA_BIN%..") do set JAVA_HOME=%%~fd

if not exist "%APP_LOGS%" mkdir "%APP_LOGS%"

set "JAR=%BASEDIR%village-admin-system\target\village-admin-system-0.1.0-SNAPSHOT-shaded.jar"
if not exist "%JAR%" (
  set "JAR=%BASEDIR%village-admin-system\target\village-admin-system-0.1.0-SNAPSHOT.jar"
)
if not exist "%JAR%" (
  echo [ERROR] 未找到后端 Jar，请先构建：
  echo mvn -f "%BASEDIR%\village-admin-system\pom.xml" package -DskipTests
  call :fail
)

echo [INFO] 停止旧后端...
powershell -NoProfile -Command "Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -match 'village-admin-system-0.1.0-SNAPSHOT(-shaded)?\.jar' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }" >nul 2>nul

echo [INFO] 启动后端...
set DB_HOST=127.0.0.1
set DB_PORT=3306
set DB_NAME=village_db
set DB_USER=village
set DB_PASS=villagepass

echo [INFO] 检查 MySQL 端口 %DB_PORT%...
powershell -NoProfile -Command "$c=Test-NetConnection -ComputerName '%DB_HOST%' -Port %DB_PORT%; if(-not $c.TcpTestSucceeded){ exit 1 }" >nul 2>nul
if not %ERRORLEVEL%==0 (
  echo [ERROR] MySQL 未启动或端口不可用，请先启动 MySQL（或使用 start-local.bat）
  call :fail
)

echo [INFO] 后端将前台运行，关闭窗口会停止服务
echo [INFO] 标准输出日志：runtime\logs\app.log
echo [INFO] 错误输出日志：runtime\logs\app.err
echo [INFO] JAVA_EXE=%JAVA_EXE%
echo [INFO] JAR=%JAR%
echo [INFO] 可使用 Ctrl+C 结束后端进程
"%JAVA_EXE%" -jar "%JAR%" 1>>"%APP_OUT%" 2>>"%APP_ERR%"

echo [INFO] 后端日志：runtime\logs\app.log
echo [INFO] 错误日志：runtime\logs\app.err
echo [INFO] 后端已退出，窗口将继续保持
:wait_exit
pause >nul
goto wait_exit
popd
endlocal

goto :eof

:fail
echo.
echo [INFO] 请修复上述问题后重试
:fail_wait
pause
goto fail_wait
