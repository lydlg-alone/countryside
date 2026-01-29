前端演示说明

- 目录: `frontend/` 包含静态前端文件。
- 本地打开: 直接用浏览器打开 `frontend/index.html`（注意跨域：若需要与后端交互，请使用本机 HTTP 服务或在 docker 中运行 nginx）。

快速本地启动（推荐）:
```bash
# 在项目根或 frontend 目录运行（需要 Python 3）
cd frontend
python -m http.server 3000
# 然后访问 http://localhost:3000
```

使用 docker-compose 运行（已在项目 `docker-compose.yml` 中添加 `frontend` 服务）：
```bash
docker compose up -d --build
```

说明:
- 前端页面会尝试请求 `http://localhost:8080/` 来检查后端状态（默认后端在容器中）。如果你的后端不在本机，请修改 `app.js` 中 URL。
