@echo off
setlocal
pushd %~dp0
set BASEDIR=.
set JAVA_HOME=
set JAVA_EXE=
set APP_LOGS=%BASEDIR%\runtime\logs
set APP_OUT=%APP_LOGS%\app.log
set APP_ERR=%APP_LOGS%\app.err

for /f "delims=" %%f in ('dir /b /s "%BASEDIR%\runtime\jre\java.exe" 2^>nul') do (
  set JAVA_EXE=%%f
  goto found_java
)
:found_java
if not defined JAVA_EXE (
  echo [ERROR] 未找到 JRE，请将 JRE 解压到 runtime\jre
  exit /b 1
)
for %%d in ("%JAVA_EXE%") do set JAVA_BIN=%%~dpd
for %%d in ("%JAVA_BIN%..") do set JAVA_HOME=%%~fd

if not exist "%APP_LOGS%" mkdir "%APP_LOGS%"

set JAR=%BASEDIR%\village-admin-system\target\village-admin-system-0.1.0-SNAPSHOT-shaded.jar
if not exist "%JAR%" (
  echo [ERROR] 未找到后端 Jar，请先构建：
  echo mvn -f "%BASEDIR%\village-admin-system\pom.xml" package -DskipTests
  exit /b 1
)

echo [INFO] 停止旧后端...
powershell -NoProfile -Command "Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -like '*village-admin-system-0.1.0-SNAPSHOT-shaded.jar*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }" >nul 2>nul

echo [INFO] 启动后端...
set DB_HOST=127.0.0.1
set DB_PORT=3306
set DB_NAME=village_db
set DB_USER=village
set DB_PASS=villagepass

echo [INFO] 后端将前台运行，关闭窗口会停止服务
"%JAVA_HOME%\bin\java.exe" -jar "%JAR%"

echo [INFO] 后端日志：runtime\logs\app.log
echo [INFO] 错误日志：runtime\logs\app.err
echo [INFO] 后端已启动：http://localhost:8080
popd
endlocal
