## Village 管理系统 — 项目说明

### 项目概述
Village 管理系统是面向村级治理与服务的管理平台，提供一套以模块化、可扩展为目标的解决方案。面向村委、乡镇基层管理员及运维人员，核心目标为：增强治理效率、提升数据透明度、规范事务流转，支持AI 。

### 核心功能

- **1. 用户与权限模块（Auth & User）**
	- 职责：用户身份管理、角色与权限控制、村民基础信息（证件/ID 卡）管理。
	- 子模块：认证（登录/单点）、权限（RBAC）、用户资料、组织（村/小组）管理、密码/策略。
	- 主要数据实体：User, Role, Permission, Resident
	- 界面：登录页、用户管理、角色权限管理、居民信息台账。

- **2. 财务与收支模块（Finance）**
	- 职责：收支凭证录入、审核流程、对账与报表、预算与项目资金管理。
	- 子模块：收支录入、审核工作流、报表导出（CSV/PDF）、凭证附件管理。
	- 主要数据实体：Transaction, Account, Voucher, AuditRecord
	- 界面：收支录入、审核列表、月度/年度报表、导出中心。

- **3. 预警管理模块（Early Warning）**
	- 职责：定义预警规则、实时检测、自动分派与通知、预警处置记录。
	- 子模块：规则引擎、触发器、分派策略、预警历史与追踪。
	- 主要数据实体：WarningRule, WarningEvent, Assignment, Escalation
	- 界面：规则配置、预警看板、处置记录、导出与订阅设置。

- **4. 基层治理与任务模块（Governance）**
	- 职责：发布治理任务、签到/验收、积分规则与申报、活动管理。
	- 子模块：任务发布、执行记录、积分核算、考核与督办。
	- 主要数据实体：Task, Checkin, PointRecord, Activity
	- 界面：任务发布、任务面板、积分查看、督办中心。

- **5. 民情反馈与政务公开模块（Feedback & Public）**
	- 职责：收集民情、受理与流转、回应公开与统计、政务信息发布。
	- 子模块：反馈渠道（APP/来访/电话）、受理流程、公开栏、互动统计。
	- 主要数据实体：Feedback, Case, Announcement, Comment
	- 界面：反馈受理台、进度查询、政务公开页、互动统计报表。

- **6. 产业看板模块（Industry Dashboard）**
	- 职责：产业数据采集、关键指标展示、趋势分析、产业预警与建议。
	- 子模块：基地管理、指标采集器、可视化面板、预测模型接入点。
	- 主要数据实体：Base, ProductionMetric, Indicator, Forecast
	- 界面：产业大屏、时序图、地块/基地详情页。

- **7. AI 辅助模块（AI）**
	- 职责：政策问答、自动摘要、智能建议、文本分类（可选接入外部模型或内部服务）。
	- 子模块：大模型API接口、摘要/报告生成、模型管理、审计日志（模型调用）。
	- 主要数据实体：AiRequest, AiResponse, ModelConfig
	- 界面：智能问答、建议面板、模型设置（仅管理员）。

- **8. 运维与审计模块（Ops & Audit）**
	- 职责：系统监控、日志管理、备份/恢复、数据清理、审计链路。
	- 子模块：日志中心、备份任务、权限审计、作业调度（Cron）、健康检查。
	- 主要数据实体：AuditLog, BackupJob, HealthCheck
	- 界面：运维面板、审计查询、备份与恢复操作。

- **9. 公共与基础模块（Common）**
	- 职责：跨模块通用能力（文件存储、通知、字典、缓存、错误处理、安全中间件）。
	- 子模块：文件服务、消息推送（邮件/短信/站内）、字典管理、通用审核组件。

### 系统架构概要
- 分层：前端（SPA 或多页面）、API 网关/后端服务（模块化微服务或单体模块化）、数据库（关系型 MySQL）、缓存（Redis）、对象存储（文件/附件）。
- 安全：HTTPS、基于 Token 的认证（JWT/OAuth）、细粒度 RBAC 权限控制、操作审计与敏感字段加密。
- 可扩展性：所有模块通过 REST 接口或消息总线（如 RabbitMQ/消息队列）解耦，AI 模块通过独立服务接入第三方或内部模型。

### 项目结构（模块依赖）
下面的目录结构示例严格按照核心模块与依赖关系组织，便于代码隔离与职责分明。

```
village-admin-system
│
├── village-admin-common
│
├── village-admin-user
│   ├── village-admin-common
│   └── village-admin-logging 
│
├── village-admin-finance
│   ├── village-admin-common
│   └── village-admin-logging
│
├── village-admin-early-warning
│   ├── village-admin-common
│   ├── village-admin-user
│   ├── village-admin-industry 
│   └── village-admin-logging
│
├── village-admin-governance
│   ├── village-admin-common
│   └── village-admin-logging
│
├── village-admin-feedback
│   ├── village-admin-common
│   ├── village-admin-user
│   └── village-admin-logging
│
├── village-admin-ops
│   ├── village-admin-common
│   └── village-admin-logging
│
├── village-admin-industry
│   ├── village-admin-common
│   └── village-admin-logging
│
├── village-admin-ai
│   ├── village-admin-common
│   └── village-admin-logging
│
├── village-admin-logging
│   └── village-admin-common
│
└── village-admin-generator
	└── village-admin-common


### 主要数据库表
- User(id, username, password_hash, name, mobile, role_id, org_id, created_at)
- Role(id, name, permissions)
- Resident(id, name, id_card, address, household, phone)
- Transaction(id, type, amount, account_id, voucher_id, status, created_by, created_at)
- WarningRule(id, name, condition, severity, enabled)
- WarningEvent(id, rule_id, target_id, severity, status, triggered_at)
- Task(id, title, description, assigned_to, status, due_date)
- Feedback(id, source, content, status, assigned_to, created_at)

### 接口设计（REST 风格，JSON）
- 登录：`POST /api/auth/login` {username,password} -> {token,user}
- 用户：`GET /api/users/{id}`、`POST /api/users`、`PUT /api/users/{id}`
- 收支：`POST /api/finance/transactions`、`GET /api/finance/reports?month=2025-01`
- 预警：`GET /api/warnings/events`、`POST /api/warnings/rules`

### 部署与运行（技术栈）
- Java1.8，构建：Maven，数据库：MySQL（本地便携版），缓存：Redis（可选）。
- Docker 已停用，改为本地一键启动脚本（Windows）。

### 前端开发与本地运行

#### 本地一键启动（Windows）
1. 将 JRE 解压到 `runtime\jre`（需包含 `bin\java.exe`）。
2. 将 MySQL 便携版解压到 `runtime\mysql`（需包含 `bin\mysqld.exe`）。
3. 运行项目根目录 `start-local.bat`。

停止服务：运行 `stop-local.bat`。

#### 前端使用方式
- 直接打开 `frontend\index.html`。
- 后端默认地址为 `http://localhost:8080`。

#### 本地开发（可选）
```powershell
cd frontend
python -m http.server 8081
```

> 注意：若前端使用 `http://localhost:8081` 访问，请确保后端已启用 CORS（当前已启用），或在 `frontend/app.js` 使用 `http://localhost:8080` 作为接口基准地址。