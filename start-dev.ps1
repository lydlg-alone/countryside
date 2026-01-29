<#
Start-dev.ps1
在本地以开发模式启动前后端（单一 Node 进程）：
- 启动静态文件服务（来自 `frontend`）
- 启动模拟后端 API (`/api/*`)

用法：在项目根目录以 PowerShell 运行：
    .\start-dev.ps1
#>

$Root = Split-Path -Parent $MyInvocation.MyCommand.Definition
Write-Host "项目根: $Root"

Push-Location $Root\mock-server
if (-not (Test-Path node_modules)) {
    Write-Host "检测到未安装依赖，正在运行 npm install..." -ForegroundColor Yellow
    npm install
}

Write-Host "启动本地开发服务器（前端静态 + 模拟后端 API）..." -ForegroundColor Green
node server.js

Pop-Location
