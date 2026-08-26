# SK BI（若依基座）

SK BI 按 `docs/bi` 中的设计文档分阶段实现，基于若依前后端分离版开发：后端为 RuoYi-Vue 3.9.2 的 Spring Boot 3 分支，前端为 RuoYi-Vue3 3.9.2 的 TypeScript 分支。

当前已完成阶段 1 基线：若依 JWT 登录与菜单权限、MySQL 8 元数据迁移、数据源凭据加密、数据源 CRUD/用户与角色 ACL、连接测试、存储过程发现与参数签名，以及数据源管理页面。

第一版主功能已经按文档贯通：报表与数据源 ACL、完整聚合设计器、组件覆盖过程、参数映射、版本差异与回滚、Redis 配置热失效、静态/SQL 控件选项、强类型存储过程查询、动态表格异构钻取、柱状图/折线图/指标卡、客户端分页排序、Excel 导出、打印样式、并发/超时/行数保护和运行审计。运行前仍需准备符合 `docs/bi/05-stored-procedure-contract.md` 的业务库与存储过程。

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

## 已实现 API

- `GET/POST /api/bi/admin/datasources`
- `GET/PUT /api/bi/admin/datasources/{id}`
- `POST /api/bi/admin/datasources/{id}/test`
- `GET /api/bi/admin/datasources/{id}/procedures`
- `GET /api/bi/admin/datasources/{id}/procedures/{procedureName}/parameters`

执行测试：

```bash
mvn -pl ruoyi-bi -am test
cd frontend && npm run build:prod
```

## 尚未完成

代码范围遵循 `docs/bi/08-test-and-roadmap.md`，不引入第一版明确排除的普通 SQL 数据集、移动端设计器、控件级联或服务端分页排序。跨实例 Redis、50,000 行性能和真实业务存储过程的端到端指标需要在部署环境中使用实际 Redis 与业务库完成验收。
