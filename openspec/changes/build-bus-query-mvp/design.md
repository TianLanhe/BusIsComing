## 上下文

BusIsComming 当前是一个空的 Android Studio 项目，已有 Android Gradle Plugin、AppCompat、Core KTX 和 Material 依赖，但还没有实现 Activity 或完整应用流程。已有产品文档 `docs/overview-design.md` 和 `docs/specification.md` 将第一版 MVP 定义为一个本地运行的 Android App：用户可以管理命名的起点/终点路线配置，并查询 Mock 巴士路线结果。

第一版必须使用 Kotlin + XML，实现本地持久化、主查询页面、路线管理页面，并为后续真实 HTTP API 替换 Mock 数据源保留清晰边界。真实巴士 API 本身不属于本次变更范围。

## 目标 / 非目标

**目标：**

- 在现有 Gradle 工程结构上增加 Kotlin Android 代码。
- 实现本地路线配置 CRUD，字段包含必填的 `name`、`origin` 和 `destination`。
- 使用 SQLite 在设备本地持久化路线配置。
- 实现主界面，加载已保存路线、支持选择路线并查询巴士结果。
- 实现根据起点/终点变化的 Mock 巴士查询数据。
- 展示路线、港币价格和预计等候时间。
- 支持点击表头按价格和预计等候时间排序。
- 将巴士查询逻辑放在仓库接口之后，便于未来替换为 HTTP 实现。

**非目标：**

- 真实 HTTP 巴士 API 接入。
- 地图选点、地理编码、地址联想，或直接巴士方案以外的复杂路线规划。
- 账号登录、云同步、推送提醒或用户分析。
- 完整 MVVM、依赖注入、Room、Compose 或 Navigation Component。
- 专项深色模式设计。

## 技术决策

### 决策 1：使用 Kotlin + XML、AppCompat 和 Material Components

App 会增加 Kotlin 支持，并使用 XML 布局、AppCompat Activity 和 Material Components 实现页面。

理由：

- 符合用户指定的技术栈。
- 当前项目已经包含 AppCompat 和 Material 依赖。
- XML 布局适合快速完成第一版 MVP，避免引入 Compose 迁移成本。

备选方案：

- Jetpack Compose：更现代，但需要额外 Gradle 和 UI 架构配置，对当前 MVP 收益有限。
- Java + XML：Gradle 改动较小，但不符合 Kotlin 方向。

### 决策 2：使用 SQLiteOpenHelper 持久化路线配置

路线配置会存储在名为 `route_configs` 的本地 SQLite 表中，字段包括 `id`、`name`、`origin`、`destination`、`created_at` 和 `updated_at`。

理由：

- 满足本地持久化需求，不需要引入 Room 或 DataStore。
- 数据模型很小，单表 SQLite 足够。
- 便于通过仓库级测试和 App 重启手动验证。

备选方案：

- Room：DAO API 更清晰，但需要注解处理和更多配置，对小型 MVP 偏重。
- SharedPreferences/DataStore：适合键值状态，但不适合多条路线记录的 CRUD。

### 决策 3：通过 `BusRouteRepository` 隔离巴士查询

主界面依赖 `BusRouteRepository.searchRoutes(origin, destination)`，第一版实现为 `MockBusRouteRepository`。

理由：

- 避免把 Mock 数据写死在 Activity 中。
- 后续替换为 `HttpBusRouteRepository` 时改动范围更小。
- 查询行为更容易测试和维护。

备选方案：

- 直接在 `MainActivity` 写死 Mock 结果：最快，但接真实 API 时会产生不必要返工。
- 现在就引入服务层和依赖注入：长期更规整，但对 MVP 规模来说偏重。

### 决策 4：列表和结果表格都使用 RecyclerView

路线管理列表和巴士结果表格都使用 RecyclerView。结果表格采用固定表头加行布局的形式。

理由：

- RecyclerView 更适合动态数据和未来扩展。
- 排序时只需要替换 adapter 数据。
- 同一模式可以覆盖路线配置列表和巴士路线结果。

备选方案：

- TableLayout：适合静态小表格，但排序和动态更新不够灵活。
- ListView：API 较旧，不如 RecyclerView 适合现代 Android UI。

### 决策 5：主界面、管理页、编辑页使用独立 Activity

MVP 会使用 `MainActivity`、`RouteManageActivity` 和 `RouteEditActivity`。

理由：

- 当前项目从空模板开始，不需要立即引入导航图。
- 独立 Activity 让页面职责和 Intent 参数更直接。
- 方便在 Android Studio 中查看和调试。

备选方案：

- 单 Activity + Fragment + Navigation Component：更适合大应用，但第一版会增加额外抽象和配置。

### 决策 6：表单校验本地显式完成

路线编辑输入会在保存前 trim。`name`、`origin` 和 `destination` trim 后必须非空。第一版允许重复路线配置。

理由：

- 规格说明要求纯文本输入，并明确第一版不强制禁止重复。
- 本地校验能立刻给出反馈，不依赖网络。

备选方案：

- 强制路线名称或起终点唯一：后续可能有价值，但当前没有产品要求。

## 风险 / 权衡

- [风险] SQLiteOpenHelper 代码在应用变大后会变得啰嗦。→ 缓解：数据库访问集中封装在 `RouteConfigRepository`，后续需要时再迁移 Room。
- [风险] 多 Activity 在导航变复杂后可能不够灵活。→ 缓解：当前保持页面职责窄，等流程变多再考虑 Navigation Component。
- [风险] Mock 查询模型可能与未来 HTTP API 返回结构不一致。→ 缓解：保持 `BusRouteOption` 模型最小化，并通过 `BusRouteRepository` 隔离映射。
- [风险] RecyclerView 模拟表格在长文本情况下可能影响可读性。→ 缓解：结果行使用稳定列宽，路线管理中的长地址允许换行或省略。
- [风险] 真实 API 未确定，无法最终设计网络错误态。→ 缓解：当前只保留 `INTERNET` 权限，HTTP 错误处理留给后续 API 接入变更。

## 迁移计划

这是空项目的第一版实现，不需要已有用户数据迁移。

部署步骤：

1. 增加 Kotlin 支持和 Activity 声明。
2. 增加数据模型、SQLite helper 和仓库。
3. 增加路线编辑页、路线管理页和主查询页。
4. 增加 Mock 巴士查询和排序行为。
5. 执行 Gradle 构建和 MVP 手动验收。

回滚策略：

- 如果 MVP 不被接受，回退本次变更文件即可。
- 由于当前没有既有应用数据模型，回滚不需要数据库迁移。

## 开放问题

- 真实 HTTP API 的 URL、方法、参数、鉴权、JSON 返回结构、超时策略和错误处理规则尚未确定，已明确延后处理。
