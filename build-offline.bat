@echo off
chcp 65001 >nul
setlocal EnableExtensions
set BASEDIR=%~dp0
set DIST=%BASEDIR%dist
set JRE_ZIP=%BASEDIR%OpenJDK8U-jre_x64_windows_hotspot_8u472b08.zip
set JDK_ZIP=%BASEDIR%OpenJDK8U-jdk_x64_windows_hotspot_8u472b08.zip
set MYSQL_ZIP=%BASEDIR%mysql-8.0.45-winx64.zip

if not exist "%DIST%" mkdir "%DIST%"

if exist "%JRE_ZIP%" (
  set JAVA_ZIP=%JRE_ZIP%
) else if exist "%JDK_ZIP%" (
  set JAVA_ZIP=%JDK_ZIP%
) else (
  for /f "delims=" %%f in ('dir /b /a-d "%BASEDIR%OpenJDK*windows*hotspot*.zip" 2^>nul') do (
    set JAVA_ZIP=%BASEDIR%%%f
    goto found_java_zip
  )
)
:found_java_zip
if not defined JAVA_ZIP (
  echo [ERROR] 未找到 JRE/JDK ZIP 便携版，请下载 ZIP 包并放到项目根目录。
  exit /b 1
)
echo [INFO] 解压 JRE/JDK ZIP 到 runtime\jre ...
powershell -NoProfile -Command "Expand-Archive -Path '%JAVA_ZIP%' -DestinationPath '%BASEDIR%runtime\jre' -Force"

if exist "%MYSQL_ZIP%" (
  set MYSQL_ZIP_USE=%MYSQL_ZIP%
) else (
  for /f "delims=" %%f in ('dir /b /a-d "%BASEDIR%mysql-*-winx64.zip" 2^>nul') do (
    set MYSQL_ZIP_USE=%BASEDIR%%%f
    goto found_mysql_zip
  )
)
:found_mysql_zip
if not defined MYSQL_ZIP_USE (
  echo [ERROR] 未找到 MySQL ZIP 便携版，请下载 ZIP 包并放到项目根目录。
  exit /b 1
)
echo [INFO] 解压 MySQL ZIP 到 runtime\mysql ...
powershell -NoProfile -Command "Expand-Archive -Path '%MYSQL_ZIP_USE%' -DestinationPath '%BASEDIR%runtime\mysql' -Force"
if not exist "%BASEDIR%runtime\mysql\logs" mkdir "%BASEDIR%runtime\mysql\logs"
if not exist "%BASEDIR%runtime\mysql\my.ini" (
  > "%BASEDIR%runtime\mysql\my.ini" (
    echo [mysqld]
    echo basedir=.
    echo datadir=./data
    echo port=3306
    echo bind-address=127.0.0.1
    echo character-set-server=utf8mb4
    echo collation-server=utf8mb4_general_ci
    echo log-error=./logs/mysql.err
    echo.
    echo [client]
    echo port=3306
    echo default-character-set=utf8mb4
  )
)

set "JAR_DIR=%BASEDIR%village-admin-system\target"
set "JAR="
for /f "delims=" %%f in ('dir /b /a-d /o-d "%JAR_DIR%\village-admin-system-*.jar" 2^>nul ^| findstr /v /i "original-"') do (
  set "JAR=%JAR_DIR%\%%f"
  goto found_jar
)
:found_jar
where mvn >nul 2>nul
if %ERRORLEVEL%==0 (
  echo [INFO] 构建后端 Jar ...
  mvn -f "%BASEDIR%village-admin-system\pom.xml" package -DskipTests
) else if not defined JAR (
  echo [ERROR] 未找到后端 Jar 且未检测到 Maven，请先构建后再打包。
  exit /b 1
)

echo [INFO] 生成离线包 zip ...
powershell -NoProfile -Command "$root='%BASEDIR%'.TrimEnd('\\'); $dist=Join-Path $root 'dist'; $zip=Join-Path $dist 'countryside-offline.zip'; if (Test-Path $zip) { Remove-Item $zip -Force }; $items=Get-ChildItem -Path $root -Force | Where-Object { $_.Name -notin @('dist','.git') } | ForEach-Object { $_.FullName }; Compress-Archive -Path $items -DestinationPath $zip -Force;"

echo [INFO] 完成：%DIST%\countryside-offline.zip
endlocal
