@echo off
setlocal
set BASEDIR=%~dp0
set DIST=%BASEDIR%dist
set JDK_MSI=%BASEDIR%OpenJDK8U-jdk_x64_windows_hotspot_8u472b08.msi
set MYSQL_MSI=%BASEDIR%mysql-8.0.45-winx64.msi

if not exist "%DIST%" mkdir "%DIST%"

if not exist "%JDK_MSI%" (
  echo [ERROR] 未找到 JDK 安装包：%JDK_MSI%
  exit /b 1
)
if not exist "%MYSQL_MSI%" (
  echo [ERROR] 未找到 MySQL 安装包：%MYSQL_MSI%
  exit /b 1
)

echo [INFO] 解压 JDK 到 runtime\jre ...
msiexec /a "%JDK_MSI%" /qn TARGETDIR="%BASEDIR%runtime\jre"

echo [INFO] 解压 MySQL 到 runtime\mysql ...
msiexec /a "%MYSQL_MSI%" /qn TARGETDIR="%BASEDIR%runtime\mysql"

set JAR=%BASEDIR%village-admin-system\target\village-admin-system-0.1.0-SNAPSHOT-shaded.jar
where mvn >nul 2>nul
if %ERRORLEVEL%==0 (
  echo [INFO] 构建后端 Jar ...
  mvn -f "%BASEDIR%village-admin-system\pom.xml" package -DskipTests
) else if not exist "%JAR%" (
  echo [ERROR] 未找到后端 Jar 且未检测到 Maven，请先构建后再打包。
  exit /b 1
)

echo [INFO] 生成离线包 zip ...
powershell -NoProfile -Command "$root='%BASEDIR%'.TrimEnd('\\'); $dist=Join-Path $root 'dist'; $zip=Join-Path $dist 'countryside-offline.zip'; if (Test-Path $zip) { Remove-Item $zip -Force }; $items=Get-ChildItem -Path $root -Force | Where-Object { $_.Name -notin @('dist','.git') } | ForEach-Object { $_.FullName }; Compress-Archive -Path $items -DestinationPath $zip -Force;"

echo [INFO] 完成：%DIST%\countryside-offline.zip
endlocal
