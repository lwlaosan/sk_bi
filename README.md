# SK BI（若依基座）

SK BI 按 `docs/bi` 中的设计文档分阶段实现，基于若依前后端分离版开发：后端为 RuoYi-Vue 3.9.2 的 Spring Boot 3 分支，前端为 RuoYi-Vue3 3.9.2 的 TypeScript 分支。

当前已完成阶段 1 基线：若依 JWT 登录与菜单权限、MySQL 8 元数据迁移、数据源凭据加密、数据源 CRUD/用户与角色 ACL、连接测试、存储过程发现与参数签名，以及数据源管理页面。

第一版主功能已经按文档贯通：报表与数据源 ACL、完整聚合设计器、组件覆盖过程、参数映射、版本差异与回滚、Redis 配置热失效、静态/SQL 控件选项、强类型存储过程查询、动态表格异构钻取、多种图表、响应式手机端运行页、Excel 导出、打印样式、并发/超时/行数保护和运行审计。业务数据源支持 MySQL、SQL Server、PostgreSQL 和 Oracle，项目元数据库仍使用 MySQL。

报表洞察支持千问与 DeepSeek：开发者可配置供应商、模型和业务提示词，运行用户按当前页面及下钻层级生成洞察。洞察正文采用安全 Markdown 渲染，每次结果连同生成用户、报表版本、层级路径和数据快照保存为历史记录。

## 本地运行

要求 JDK 17、Maven 3.9、Node.js 20+、MySQL 8 和 Redis。先创建 `sk_bi_meta` 数据库并依次执行若依基础脚本和 BI 菜单脚本：

```bash
mysql -u root -p sk_bi_meta < sql/ry_20260417.sql
mysql -u root -p sk_bi_meta < sql/bi_menu.sql
```

如果阶段 1 菜单已经初始化，只执行阶段 2 增量脚本，避免重复插入：

```bash
mysql -u root -p sk_bi_meta < sql/bi_menu_phase2.sql
```

BI 业务表由 Flyway 在应用启动时从 `ruoyi-bi/src/main/resources/db/migration` 自动迁移。设置元数据库和数据源主密钥：

```bash
export BI_META_DB_URL='jdbc:mysql://localhost:3306/sk_bi_meta?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true'
export BI_META_DB_USERNAME='sk_bi'
export BI_META_DB_PASSWORD='replace-me'
export BI_DATASOURCE_MASTER_KEY="$(openssl rand -base64 32)"
export DRUID_MONITOR_USERNAME='admin'
export DRUID_MONITOR_PASSWORD='replace-with-a-strong-password'
# 可选：启用报表洞察时至少配置其中一个
export DASHSCOPE_API_KEY='replace-with-qwen-key'
export DEEPSEEK_API_KEY='replace-with-deepseek-key'
```

构建并运行后端：

```bash
mvn -pl ruoyi-admin -am package -DskipTests
java -jar ruoyi-admin/target/ruoyi-admin.jar
```

启动前端：

```bash
cd frontend
npm install
npm run dev
```

登录沿用若依 JWT；默认基础脚本账号为 `admin/admin123`，首次部署后应立即修改。生产环境请在 Spring 激活 profile 中加入 `prod`，缺少合法 256 位主密钥时应用会拒绝启动。

## 主要接口

- `GET/POST /api/bi/admin/datasources`
- `GET/PUT /api/bi/admin/datasources/{id}`
- `POST /api/bi/admin/datasources/{id}/test`
- `GET /api/bi/admin/datasources/{id}/procedures`
- `GET /api/bi/admin/datasources/{id}/procedures/{procedureName}/parameters`
- `GET/POST /api/bi/admin/reports`
- `GET/PUT /api/bi/admin/reports/{id}/configuration`
- `GET /api/bi/runtime/reports/{uuid}`
- `POST /api/bi/runtime/reports/{uuid}/components/{componentKey}/query`
- `POST /api/bi/runtime/reports/{uuid}/insight`
- `GET /api/bi/runtime/reports/{uuid}/insights`

执行测试：

```bash
mvn -pl ruoyi-bi -am test
cd frontend && npm run build:prod
```

## 安全与贡献

不要提交 `.local.env`、数据库密码、模型 API Key 或 `BI_DATASOURCE_MASTER_KEY`。生产部署前必须修改基础脚本中的默认管理员密码，并配置 Druid 监控密码。安全问题请按 [SECURITY.md](SECURITY.md) 通过 GitHub 私密漏洞报告提交。

路线图见 `docs/bi/08-test-and-roadmap.md`。跨实例 Redis、大数据量性能和真实业务存储过程的端到端指标，需要在部署环境中使用实际 Redis 与业务库完成验收。欢迎通过 Issue 和 Pull Request 参与改进。
