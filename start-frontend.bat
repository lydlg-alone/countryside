@echo off
chcp 65001 >nul
setlocal EnableExtensions
pushd %~dp0
set PORT=8081
echo [INFO] 启动前端静态服务：http://localhost:%PORT%/frontend/index.html
echo [INFO] 关闭窗口将停止前端服务
start "" http://localhost:%PORT%/frontend/index.html
python -m http.server %PORT%
popd
endlocal
