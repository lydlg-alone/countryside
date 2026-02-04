@echo off
setlocal
pushd "%~dp0"
set "PORT=8081"
set "PY_CMD="

where python >nul 2>nul
if %errorlevel%==0 set "PY_CMD=python"

if not defined PY_CMD (
	where py >nul 2>nul
	if %errorlevel%==0 set "PY_CMD=py -3"
)

if not defined PY_CMD (
	echo [ERROR] 未找到 Python，请安装 Python 3 并确保 python 或 py 可用
	popd
	endlocal
	exit /b 1
)

echo [INFO] 启动前端静态服务：http://localhost:%PORT%/frontend/index.html
echo [INFO] 关闭窗口将停止前端服务
start "" "http://localhost:%PORT%/frontend/index.html"
%PY_CMD% -m http.server %PORT%
popd
endlocal
