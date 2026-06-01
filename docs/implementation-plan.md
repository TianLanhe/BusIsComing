# BusIsComming 实现计划

## 1. 计划目的

本文档用于跟踪 BusIsComming 第一版 MVP 的开发任务、依赖关系、验收点和测试用例。

当前阶段只做任务拆分和计划梳理，不开始代码实现。

需求来源：

- `docs/overview-design.md`
- `docs/specification.md`

## 2. 状态说明

任务状态使用以下值：

| 状态 | 说明 |
| --- | --- |
| Not Started | 未开始 |
| In Progress | 进行中 |
| Done | 已完成 |
| Blocked | 被阻塞 |

## 3. 阶段总览

| 阶段 | 名称 | 目标 | 状态 |
| --- | --- | --- | --- |
| Phase 0 | 工程准备 | 让空项目具备 Kotlin Android 开发和 App 启动能力 | Not Started |
| Phase 1 | 数据模型和本地存储 | 完成路线配置的本地持久化能力 | Not Started |
| Phase 2 | Mock 查询能力 | 完成可替换的巴士查询仓库和本地 Mock 数据 | Not Started |
| Phase 3 | 路线编辑页 | 完成新增和编辑路线配置流程 | Not Started |
| Phase 4 | 路线管理页 | 完成路线配置列表、编辑入口和删除确认 | Not Started |
| Phase 5 | 主界面 | 完成路线选择、查询和结果展示 | Not Started |
| Phase 6 | 排序 | 完成按价格和预计等候时间排序 | Not Started |
| Phase 7 | 整体验收和收尾 | 完成 UI 整理、构建验证和完整回归 | Not Started |

## 4. 详细任务

### Phase 0：工程准备

#### TASK-001 配置 Kotlin Android 支持

状态：Not Started

目标：

- 增加 Kotlin Gradle 插件。
- 确认 `app` 模块可以编译 Kotlin。
- 创建基础 Kotlin 包结构。

依赖：

- 无。

交付物：

- Gradle 配置支持 Kotlin。
- Kotlin 源码目录和基础包结构可用。

测试用例：

- 执行 `./gradlew build` 成功。
- 新增一个最小 Kotlin 类后项目可以正常编译。

#### TASK-002 配置 Android 基础入口

状态：Not Started

目标：

- 创建 `MainActivity`。
- 在 `AndroidManifest.xml` 注册启动 Activity。
- 增加 `android.permission.INTERNET` 权限。

依赖：

- TASK-001。

交付物：

- App 可以启动到主界面。
- Manifest 中包含网络权限。

测试用例：

- 启动 App 后进入 `MainActivity`。
- 检查 Manifest，确认存在 `android.permission.INTERNET`。
- 执行 `./gradlew build` 成功。

### Phase 1：数据模型和本地存储

#### TASK-003 创建数据模型

状态：Not Started

目标：

- 创建 `RouteConfig`。
- 创建 `BusRouteOption`。
- 创建排序枚举 `SortField` 和 `SortDirection`。

依赖：

- TASK-001。

交付物：

- `data/model/RouteConfig.kt`
- `data/model/BusRouteOption.kt`
- 排序枚举定义。

测试用例：

- `RouteConfig` 包含 `id`、`name`、`origin`、`destination`。
- `BusRouteOption` 包含 `routeName`、`priceHkd`、`waitMinutes`。
- 排序枚举覆盖价格和预计等候时间。

#### TASK-004 实现本地数据库

状态：Not Started

目标：

- 实现 `RouteConfigDbHelper`。
- 创建 `route_configs` 表。
- 表字段包含 `id`、`name`、`origin`、`destination`、`created_at`、`updated_at`。

依赖：

- TASK-003。

交付物：

- `data/local/RouteConfigDbHelper.kt`
- 本地 SQLite 表结构。

测试用例：

- 首次启动可以创建数据库。
- 表结构字段完整。
- 数据库升级逻辑不会导致启动崩溃。

#### TASK-005 实现路线配置仓库

状态：Not Started

目标：

- 实现 `RouteConfigRepository`。
- 支持 `getAll()`、`getById(id)`、`insert(...)`、`update(config)`、`delete(id)`。

依赖：

- TASK-004。

交付物：

- `data/repository/RouteConfigRepository.kt`

测试用例：

- 新增后能查到路线配置。
- 根据 id 能查到正确记录。
- 修改后数据更新。
- 删除后数据不存在。
- 删除不存在 id 不崩溃。
- App 重启后数据仍存在。

### Phase 2：Mock 查询能力

#### TASK-006 定义巴士查询仓库接口

状态：Not Started

目标：

- 定义 `BusRouteRepository` 接口。
- 接口方法为 `searchRoutes(origin: String, destination: String): List<BusRouteOption>`。

依赖：

- TASK-003。

交付物：

- `data/repository/BusRouteRepository.kt`

测试用例：

- UI 层可以依赖接口类型。
- 接口不包含 Android UI 依赖。

#### TASK-007 实现 Mock 巴士查询仓库

状态：Not Started

目标：

- 实现 `MockBusRouteRepository`。
- 根据起点和终点返回本地写死数据。
- 未匹配起终点时返回空列表。

依赖：

- TASK-006。

交付物：

- `data/repository/MockBusRouteRepository.kt`

Mock 数据：

| 起点 | 终点 | 返回路线 |
| --- | --- | --- |
| 渔湾村渔进楼 | 兴华二村丰兴楼 | 82、8X、780 |
| 兴华二村丰兴楼 | 渔湾村渔进楼 | 82、8X |
| 其他 | 其他 | 空列表 |

测试用例：

- `渔湾村渔进楼 -> 兴华二村丰兴楼` 返回 3 条。
- `兴华二村丰兴楼 -> 渔湾村渔进楼` 返回 2 条。
- 未匹配路线返回空列表。
- 返回价格和等待时间符合规格说明。

### Phase 3：路线编辑页

#### TASK-008 创建路线编辑页 UI

状态：Not Started

目标：

- 创建路线编辑页布局。
- 页面包含路线名称、起点地址、终点地址输入框。
- 页面包含保存按钮和返回入口。

依赖：

- TASK-002。

交付物：

- `ui/edit/RouteEditActivity.kt`
- `res/layout/activity_route_edit.xml`

测试用例：

- 页面字段完整展示。
- 输入较长文本时界面不明显错乱。
- 返回入口可正常返回上一页。

#### TASK-009 实现新增路线逻辑

状态：Not Started

目标：

- 对用户输入执行 trim。
- 校验路线名称、起点地址、终点地址必填。
- 合法输入保存到本地数据库。
- 保存成功后返回路线管理页。

依赖：

- TASK-005。
- TASK-008。

交付物：

- 新增路线配置流程。

测试用例：

- 空字段保存失败并展示错误。
- 全空格字段保存失败。
- 合法输入保存成功。
- 保存后返回管理页。
- 新增后路线管理页可以看到新路线。

#### TASK-010 实现编辑路线逻辑

状态：Not Started

目标：

- 根据路线 id 加载已有配置。
- 编辑页回填路线名称、起点地址、终点地址。
- 保存后更新原记录。
- id 不存在时不崩溃。

依赖：

- TASK-005。
- TASK-008。
- TASK-009。

交付物：

- 编辑路线配置流程。

测试用例：

- 编辑页能正确回填已有数据。
- 修改保存后列表展示新内容。
- id 不存在时提示记录不存在。
- id 不存在时不崩溃，并可以返回管理页。

### Phase 4：路线管理页

#### TASK-011 创建路线管理页 UI

状态：Not Started

目标：

- 创建路线管理页布局。
- 使用 `RecyclerView` 展示路线配置。
- 每项显示路线名称和“起点地址 -> 终点地址”。
- 提供新增、编辑、删除入口。

依赖：

- TASK-005。
- TASK-008。

交付物：

- `ui/manage/RouteManageActivity.kt`
- `ui/manage/RouteConfigAdapter.kt`
- `res/layout/activity_route_manage.xml`
- `res/layout/item_route_config.xml`

测试用例：

- 无数据时展示空状态。
- 有数据时列表展示正确。
- 点击新增进入路线编辑页。
- 点击编辑进入路线编辑页并携带 id。

#### TASK-012 实现删除确认

状态：Not Started

目标：

- 点击删除时弹出确认弹窗。
- 用户确认后删除路线配置。
- 用户取消后保持数据不变。

依赖：

- TASK-011。

交付物：

- 删除确认交互。

测试用例：

- 点击删除会出现确认弹窗。
- 取消删除后数据仍存在。
- 确认删除后数据移除。
- 删除最后一条后展示空状态。

### Phase 5：主界面

#### TASK-013 创建主界面 UI

状态：Not Started

目标：

- 创建主界面布局。
- 包含路线下拉列表、查询按钮、路线管理入口。
- 包含结果表头、结果 `RecyclerView` 和空状态区域。

依赖：

- TASK-002。
- TASK-005。

交付物：

- `ui/main/MainActivity.kt`
- `ui/main/BusRouteAdapter.kt`
- `res/layout/activity_main.xml`
- `res/layout/item_bus_route.xml`

测试用例：

- 无路线配置时展示空状态和新增入口。
- 有路线配置时展示下拉列表。
- 查询按钮在无配置时不可用或不展示。
- 路线管理入口可进入管理页。

#### TASK-014 实现路线选择逻辑

状态：Not Started

目标：

- 主界面加载本地路线配置。
- 下拉项展示格式为 `路线名称：起点地址 -> 终点地址`。
- 默认选中第一条，或在返回主界面时保持当前选择。

依赖：

- TASK-013。

交付物：

- 主界面路线配置下拉选择能力。

测试用例：

- 新增路线后回到主界面能看到。
- 删除当前选中路线后主界面能刷新。
- 删除全部路线后回到空状态。
- 下拉项展示格式符合规格说明。

#### TASK-015 实现查询结果展示

状态：Not Started

目标：

- 点击查询调用 `BusRouteRepository`。
- 展示路线、价格、预计等候时间。
- 无结果时展示“暂无可用巴士路线”。

依赖：

- TASK-007。
- TASK-014。

交付物：

- 主界面查询和结果展示能力。

测试用例：

- 正向 Mock 路线展示 3 条。
- 反向 Mock 路线展示 2 条。
- 未匹配路线展示无结果文案。
- 价格格式为 `HK$金额`。
- 等候时间格式为 `约 N 分钟`。
- 旧查询结果不会在无结果查询后残留。

### Phase 6：排序

#### TASK-016 实现价格排序

状态：Not Started

目标：

- 点击“价格”表头后按价格升序排序。
- 再次点击“价格”表头后按价格降序排序。
- 表头展示当前排序方向。

依赖：

- TASK-015。

交付物：

- 价格排序交互。

测试用例：

- 第一次点击价格后按价格升序。
- 第二次点击价格后按价格降序。
- 排序后数据数量不变。
- 表头方向显示正确。

#### TASK-017 实现预计等候时间排序

状态：Not Started

目标：

- 点击“预计等候时间”表头后按等待分钟数升序排序。
- 再次点击“预计等候时间”表头后按等待分钟数降序排序。
- 表头展示当前排序方向。

依赖：

- TASK-015。

交付物：

- 等待时间排序交互。

测试用例：

- 第一次点击等待时间后按分钟数升序。
- 第二次点击等待时间后按分钟数降序。
- 从价格排序切换到等待时间排序时，默认等待时间升序。
- 表头方向显示正确。

### Phase 7：整体验收和收尾

#### TASK-018 UI 细节整理

状态：Not Started

目标：

- 统一页面间距、标题、按钮样式。
- 完善空状态文案。
- 避免界面过于简陋。
- 处理长文本展示。

依赖：

- TASK-008。
- TASK-011。
- TASK-013。

交付物：

- 可用且简洁的 Material 风格界面。

测试用例：

- 小屏幕下文本不明显重叠。
- 长路线名称和长地址能正常换行或截断。
- 主要操作入口清晰可见。
- 空状态文案准确。

#### TASK-019 构建和回归验证

状态：Not Started

目标：

- 执行完整 Gradle 构建。
- 手动跑通核心用户路径。
- 对照规格说明完成验收。

依赖：

- TASK-001 至 TASK-018。

交付物：

- 构建验证结果。
- 回归测试结果。

测试用例：

- `./gradlew build` 成功。
- 首次打开 App 展示空状态。
- 新增路线成功。
- 主界面选择路线并查询成功。
- 查询结果展示正确。
- 价格排序正确。
- 等待时间排序正确。
- 编辑路线成功。
- 删除路线有确认弹窗。
- 删除全部路线后恢复空状态。

## 5. 推荐实现顺序

建议按以下顺序推进：

1. TASK-001：配置 Kotlin Android 支持。
2. TASK-002：配置 Android 基础入口。
3. TASK-003：创建数据模型。
4. TASK-004：实现本地数据库。
5. TASK-005：实现路线配置仓库。
6. TASK-006：定义巴士查询仓库接口。
7. TASK-007：实现 Mock 巴士查询仓库。
8. TASK-008：创建路线编辑页 UI。
9. TASK-009：实现新增路线逻辑。
10. TASK-010：实现编辑路线逻辑。
11. TASK-011：创建路线管理页 UI。
12. TASK-012：实现删除确认。
13. TASK-013：创建主界面 UI。
14. TASK-014：实现路线选择逻辑。
15. TASK-015：实现查询结果展示。
16. TASK-016：实现价格排序。
17. TASK-017：实现预计等候时间排序。
18. TASK-018：UI 细节整理。
19. TASK-019：构建和回归验证。

## 6. 进度跟踪表

| 任务编号 | 任务名称 | 状态 | 备注 |
| --- | --- | --- | --- |
| TASK-001 | 配置 Kotlin Android 支持 | Not Started |  |
| TASK-002 | 配置 Android 基础入口 | Not Started |  |
| TASK-003 | 创建数据模型 | Not Started |  |
| TASK-004 | 实现本地数据库 | Not Started |  |
| TASK-005 | 实现路线配置仓库 | Not Started |  |
| TASK-006 | 定义巴士查询仓库接口 | Not Started |  |
| TASK-007 | 实现 Mock 巴士查询仓库 | Not Started |  |
| TASK-008 | 创建路线编辑页 UI | Not Started |  |
| TASK-009 | 实现新增路线逻辑 | Not Started |  |
| TASK-010 | 实现编辑路线逻辑 | Not Started |  |
| TASK-011 | 创建路线管理页 UI | Not Started |  |
| TASK-012 | 实现删除确认 | Not Started |  |
| TASK-013 | 创建主界面 UI | Not Started |  |
| TASK-014 | 实现路线选择逻辑 | Not Started |  |
| TASK-015 | 实现查询结果展示 | Not Started |  |
| TASK-016 | 实现价格排序 | Not Started |  |
| TASK-017 | 实现预计等候时间排序 | Not Started |  |
| TASK-018 | UI 细节整理 | Not Started |  |
| TASK-019 | 构建和回归验证 | Not Started |  |

## 7. 阻塞项和待确认事项

当前没有阻塞第一版 Mock MVP 的事项。

后续接入真实 HTTP API 前，需要补充：

- API URL。
- HTTP 方法。
- 请求参数。
- 鉴权方式。
- 返回 JSON 结构。
- 错误码处理规则。
- 超时和重试策略。
- 字段映射规则。
