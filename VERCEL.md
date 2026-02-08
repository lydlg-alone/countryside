# 部署到 Vercel（前端 + API 反向代理）

本项目当前结构是：
- `frontend/`：纯静态前端（HTML/JS/CSS）
- Java 后端：`HttpServer + JDBC + MySQL`（不适合直接部署到 Vercel 的 Serverless）

因此推荐方案是：
1. 前端部署在 Vercel
2. 后端与数据库部署在其它支持常驻进程/数据库的环境（例如自建服务器、云主机、Render/Fly.io 等）
3. Vercel 通过 `api/[...path].js` 把同域 `/api/*` 请求转发到后端（避免 CORS）

## 1) 代码已做的适配

- `frontend/config.js`：运行时决定 `API_BASE`
  - 本地（localhost）：默认 `http://localhost:8080`
  - 线上（Vercel）：默认空字符串（同域 `/api/*`）
- `api/[...path].js`：把 Vercel 的 `/api/*` 代理到环境变量 `BACKEND_URL`
- `vercel.json`：把根路径 `/` 重定向到 `/frontend/`

## 2) 在 Vercel 上创建项目

1. Import Git 仓库到 Vercel
2. Framework Preset 选择 `Other`
3. Build Command 留空（或 `null`）
4. Output Directory 留空
5. 在 Project Settings → Environment Variables 添加：
   - `BACKEND_URL`：你的后端地址（示例：`https://your-backend.example.com` 或 `http://x.x.x.x:8080`）

部署后访问：
- `https://<your-vercel-domain>/frontend/`

## 3) 可选：切换 API_BASE（无需重新部署）

如果你不想走 Vercel 代理，也可以直接让前端访问后端（需要后端开启 CORS）。

- 临时：在 URL 上加 `?api=https://your-backend.example.com`
- 固化：在浏览器控制台执行：
  - `localStorage.setItem('VILLAGE_API_BASE','https://your-backend.example.com')`
  - 刷新页面

