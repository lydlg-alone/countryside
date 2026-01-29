@echo off
setlocal
set BASEDIR=%~dp0
set JAVA_HOME=
set MYSQL_HOME=
set JAVA_EXE=
set MYSQLD_EXE=
set MYSQL_BIN=
set MYSQL_ROOT=%BASEDIR%runtime\mysql
set MYSQL_DATA=%MYSQL_ROOT%\data

for /r "%BASEDIR%runtime\jre" %%f in (java.exe) do (
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

for /r "%MYSQL_ROOT%" %%f in (mysqld.exe) do (
  set MYSQLD_EXE=%%f
  goto found_mysql
)
:found_mysql
if not defined MYSQLD_EXE (
  echo [ERROR] 未找到 MySQL 便携版，请将 MySQL 解压到 runtime\mysql
  exit /b 1
)
for %%d in ("%MYSQLD_EXE%") do set MYSQL_BIN=%%~dpd
for %%d in ("%MYSQL_BIN%..") do set MYSQL_HOME=%%~fd

set PATH=%JAVA_HOME%\bin;%MYSQL_BIN%;%PATH%

if not exist "%MYSQL_DATA%\mysql" (
  echo [INFO] 初始化 MySQL 数据目录...
  "%MYSQLD_EXE%" --initialize-insecure --basedir="%MYSQL_HOME%" --datadir="%MYSQL_DATA%"
)

echo [INFO] 启动 MySQL...
start "village-mysql" /B "%MYSQLD_EXE%" --defaults-file="%MYSQL_ROOT%\my.ini" --basedir="%MYSQL_HOME%" --datadir="%MYSQL_DATA%"

set /a RETRY=0
:wait_mysql
"%MYSQL_BIN%\mysqladmin.exe" -h 127.0.0.1 -P 3306 -u root ping >nul 2>nul
if %ERRORLEVEL%==0 goto mysql_ready
set /a RETRY+=1
if %RETRY% GEQ 20 goto mysql_fail
timeout /t 1 /nobreak >nul
goto wait_mysql

:mysql_fail
echo [ERROR] MySQL 启动失败，请检查 runtime\mysql\logs\mysql.err
exit /b 1

:mysql_ready
echo [INFO] 初始化数据库与账号...
"%MYSQL_BIN%\mysql.exe" -h 127.0.0.1 -P 3306 -u root < "%BASEDIR%runtime\scripts\init.sql"

set DB_HOST=127.0.0.1
set DB_PORT=3306
set DB_NAME=village_db
set DB_USER=village
set DB_PASS=villagepass

set JAR=%BASEDIR%village-admin-system\target\village-admin-system-0.1.0-SNAPSHOT-shaded.jar
if not exist "%JAR%" (
  echo [ERROR] 未找到后端 Jar，请先构建：
  echo mvn -f "%BASEDIR%village-admin-system\pom.xml" package -DskipTests
  exit /b 1
)

echo [INFO] 启动后端...
start "village-app" /B "%JAVA_HOME%\bin\java.exe" -jar "%JAR%"

echo [INFO] 后端已启动：http://localhost:8080
echo [INFO] 前端请手动打开：%BASEDIR%frontend\index.html
endlocal
