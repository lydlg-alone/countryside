## Village 管理系统 — 项目说明

### 项目概述
Village 管理系统是面向村级治理与服务的轻量化管理平台，提供 Java 8 后端（HttpServer + JDBC）与静态前端页面。面向村委、乡镇基层管理员与运维人员，核心目标为：提升治理效率、规范流程、可视化展示与数据留痕。

### 核心功能


- **1. 首页看板（地图 + 村民信息）**
	- 地图优先从项目主目录 `地图.svg` 读取，失败时回退到数据库 SVG。
	- 支持缩放、拖动、分层显示（边界 / 区域名称 / 村民信息）。
	- 管理员可在地图上新增村民点，并支持编辑与删除，数据入库。

- **2. 用户与权限管理**
	- 用户新增、编辑、删除与登录。
	- 角色与权限信息展示。
	- 登录支持验证码校验与密码加密存储。

- **3. 财务与收支管理**
	- 收支记录新增、列表、审核与统计。
	- 前端表单提交后端落库。

- **4. 预警管理**
	- 预警事件、规则、处置日志与统计。
	- 支持处理状态更新与记录追踪。

- **5. 基层治理与任务**
	- 任务发布、签到、验收、积分规则与活动管理。
	- 任务与积分数据持久化。

- **6. 民情反馈与政务公开**
	- 反馈受理、流程与公告管理。
	- 统计报表与流程维护。

- **7. 产业看板**
	- 产业指标展示、趋势分析与预测视图。

- **8. AI 辅助**
	- 智能问答、自动摘要、建议保存。
	- 记录入库并可按类型查询。

- **9. 运维与审计**
	- 监控指标、健康检查、日志中心、备份/恢复与审计查询。
	- 全部子功能已接入数据库。

### 系统架构概要
- 前端：静态 HTML/CSS/JS。
- 后端：Java 8 HttpServer + JDBC（MySQL）。
- 数据库：MySQL（便携版运行）。

### 项目结构（详细）
```
.
├─ frontend/                               # 静态前端
│  └─ index.html                           # 主页面与前端逻辑
├─ runtime/                                # 本地运行环境（JRE/MySQL）
│  ├─ jre/                                 # 便携 JRE（需自行准备）
│  ├─ mysql/                               # 便携 MySQL（需自行准备）
│  └─ scripts/                             # 初始化脚本
│     └─ init.sql                          # 初始化数据库与账号
├─ village-admin-system/                   # Java 后端
│  ├─ src/main/java/org/village/system/
│  │  └─ Application.java                  # 后端入口与 API 实现
│  └─ pom.xml                              # Maven 构建配置
├─ build-offline.bat                       # 打包离线运行环境
├─ start-local.bat                         # 启动后端+MySQL
├─ stop-local.bat                          # 停止后端+MySQL
├─ start-frontend.bat                      # 启动前端静态服务
├─ 地图.svg                                # 主目录地图（SVG）
└─ README.md                               # 项目说明
```

### 主要数据库表（节选）
- users, transactions, warnings, warning_logs
- industry_metrics, map_data, residents, ai_records
- ops_audit, ops_monitor, ops_health, ops_logs, ops_backups, ops_restores
- gov_tasks, gov_checkins, gov_acceptance, gov_point_rules, gov_point_audit, gov_activities
- feedback_items, feedback_flow, feedback_announcements

### 接口设计（REST 风格，JSON）
- 登录：`POST /api/auth/login`
- 验证码：`GET /api/auth/captcha`
- 用户：`GET /api/users`、`POST /api/users`、`PUT /api/users/{id}`
- 收支：`GET /api/finance/transactions`、`POST /api/finance/transactions`
- 预警：`GET /api/warnings/events`、`POST /api/warnings/rules`
- 地图：`GET /api/map`（SVG）
- 村民：`GET /api/residents`、`POST /api/residents`、`PUT /api/residents/{id}`、`DELETE /api/residents/{id}`
- 运维：`/api/ops/monitor`、`/api/ops/health`、`/api/ops/logs`、`/api/ops/backups`、`/api/ops/restores`、`/api/ops/audit`

### 部署与运行（技术栈）
- Java 8，构建：Maven，数据库：MySQL（本地便携版）。
- Docker 未使用，采用 Windows 本地一键启动脚本。

### 前端开发与本地运行

#### 本地一键启动（Windows）
1. 将 JRE 解压到 `runtime\jre`（需包含 `bin\java.exe`）。
2. 将 MySQL 便携版解压到 `runtime\mysql`（需包含 `bin\mysqld.exe`）。
3. 运行项目根目录 `start-local.bat`（会同时启动 MySQL + 后端，窗口保持不退出）。

停止服务：运行 `stop-local.bat`。

### 数据库编辑工具（db-editor.html）
启动后端后，直接打开 `db-editor.html`，root 登录后可浏览所有表并对数据进行新增/编辑/删除（通过后端接口 `/api/ops/db/*`，仅允许本机访问）。默认 root 密码为 `lydlg`（可用环境变量 `DB_EDITOR_ROOT_PASSWORD` 覆盖）。

## Windows 离线包说明

已提供一键打包与启动脚本：
- 打包：双击 [build-offline.bat](build-offline.bat)
- 启动：双击 [start-local.bat](start-local.bat)
- 停止：双击 [stop-local.bat](stop-local.bat)

### 离线包打包流程
1. 将 JRE/MySQL 安装包放在项目根目录：
	- OpenJDK8U-jre_x64_windows_hotspot_8u472b08.zip（或 OpenJDK8U-jdk_x64_windows_hotspot_8u472b08.zip）
	- mysql-8.0.36-winx64.zip
2. 运行 [build-offline.bat](build-offline.bat)，会自动解压到 runtime 目录，并生成 dist/countryside-offline.zip。

### 离线包运行流程
1. 解压 dist/countryside-offline.zip。
2. 双击 start-local.bat 启动后端与 MySQL。
3. 打开 frontend/index.html 使用系统（会自动加载主目录 `地图.svg`）。
#### 前端使用方式
- 直接打开 `frontend\index.html`。
- 或运行 `start-frontend.bat`（会启动本地静态服务并自动打开页面）。
- 前端默认加载主目录 `地图.svg`；如需从数据库加载，请使用 `db-editor.html` 保存 SVG。
- 后端默认地址为 `http://localhost:8080`。

#### 数据库编辑工具
- `db-editor.html` 需要 root 码 `lydlg` 才能进入。

#### 本地开发（可选）
```powershell
cd frontend
python -m http.server 8081
```

> 注意：若前端使用 `http://localhost:8081` 访问，请确保后端已启用 CORS（当前已启用），或在 `frontend/app.js` 使用 `http://localhost:8080` 作为接口基准地址。
