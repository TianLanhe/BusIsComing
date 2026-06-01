# BusIsComming 概要设计

## 1. 项目目标

BusIsComming 是一个用于查询巴士到站信息的 Android App。第一版目标是完成一个可本地运行和调试的 MVP：

- 用户可以管理常用路线配置。
- 用户可以在主界面选择已配置的起点和终点。
- 用户点击查询后，App 展示从起点到终点的可用巴士路线。
- 查询结果包含路线、价格、预计等候时间。
- 查询结果支持按价格和预计等候时间排序。

第一版暂不接入真实 HTTP API，先使用本地 Mock 数据。后续拿到真实 API 和调用方式后，再替换查询数据源。

## 2. 已确认需求

### 2.1 路线配置

路线配置需要支持新增、查看、修改、删除。

每条路线配置包含：

- 路线名称
- 起点地址
- 终点地址

起点和终点均为纯文本地图地址，例如：

- 渔湾村渔进楼
- 兴华二村丰兴楼

路线配置只保存在本地，不需要账号、云同步或远程保存。

删除路线配置时需要二次确认，避免误删。

### 2.2 主界面查询

主界面包含：

- 已保存路线配置的下拉列表
- 查询按钮
- 巴士路线结果表格

如果本地没有任何路线配置，主界面显示空状态，并引导用户新增路线配置。

点击查询后，根据选中的起点和终点加载巴士路线。

第一版查询结果来自本地 Mock 数据，且 Mock 数据需要和起点、终点有关。

### 2.3 查询结果

查询结果表格包含 3 列：

- 路线
- 价格
- 预计等候时间

价格固定使用港币，展示格式建议为：

- HK$6.0
- HK$12.5

预计等候时间表示车辆到站时间，展示格式建议为：

- 约 3 分钟
- 约 12 分钟

如果没有可用路线，显示：

> 暂无可用巴士路线

### 2.4 排序

结果表格支持排序：

- 点击“价格”表头，按价格升序或降序切换。
- 点击“预计等候时间”表头，按等待时间升序或降序切换。

建议在当前排序字段旁展示方向：

- 价格 ↑
- 价格 ↓
- 预计等候时间 ↑
- 预计等候时间 ↓

## 3. 页面设计

### 3.1 主界面

主界面职责：

- 展示路线配置选择入口。
- 触发巴士路线查询。
- 展示查询结果。
- 提供进入路线管理页的入口。

主要控件：

- 顶部标题栏
- 路线配置下拉框
- 查询按钮
- 路线管理入口
- 查询结果表格
- 空状态提示

下拉列表展示格式：

```text
路线名称：起点地址 -> 终点地址
```

示例：

```text
上班：渔湾村渔进楼 -> 兴华二村丰兴楼
```

没有配置时：

- 显示空状态文案。
- 显示“新增路线”按钮。
- 点击后进入路线编辑页或路线管理页。

### 3.2 路线管理页

路线管理页职责：

- 展示所有本地路线配置。
- 提供新增、编辑、删除操作。

每条路线配置展示：

- 路线名称
- 起点地址 -> 终点地址

操作：

- 新增：进入路线编辑页。
- 编辑：进入路线编辑页并带入已有数据。
- 删除：弹出二次确认，确认后删除。

### 3.3 路线编辑页

路线编辑页职责：

- 创建新的路线配置。
- 修改已有路线配置。

输入字段：

- 路线名称
- 起点地址
- 终点地址

校验规则：

- 路线名称必填。
- 起点地址必填。
- 终点地址必填。

保存成功后返回路线管理页。

## 4. 技术方案

### 4.1 技术栈

- 语言：Kotlin
- UI：XML + AppCompat + Material Components
- 本地存储：SQLiteOpenHelper
- 列表与表格：RecyclerView
- 下拉选择：MaterialAutoCompleteTextView 或 Spinner
- 网络权限：提前加入 `android.permission.INTERNET`

选择 Kotlin + XML 的原因：

- 符合当前项目已有依赖和工程结构。
- 实现成本低，适合快速完成第一版 MVP。
- 后续可继续演进到更完整的 MVVM 或 Jetpack 组件。

### 4.2 模块结构

建议代码结构：

```text
app/src/main/java/com/example/busiscomming/
├── data/
│   ├── local/
│   │   └── RouteConfigDbHelper.kt
│   ├── model/
│   │   ├── BusRouteOption.kt
│   │   └── RouteConfig.kt
│   └── repository/
│       ├── BusRouteRepository.kt
│       ├── MockBusRouteRepository.kt
│       └── RouteConfigRepository.kt
└── ui/
    ├── edit/
    │   └── RouteEditActivity.kt
    ├── main/
    │   ├── BusRouteAdapter.kt
    │   └── MainActivity.kt
    └── manage/
        ├── RouteConfigAdapter.kt
        └── RouteManageActivity.kt
```

XML 资源建议结构：

```text
app/src/main/res/layout/
├── activity_main.xml
├── activity_route_edit.xml
├── activity_route_manage.xml
├── item_bus_route.xml
└── item_route_config.xml
```

## 5. 数据模型

### 5.1 RouteConfig

```kotlin
data class RouteConfig(
    val id: Long,
    val name: String,
    val origin: String,
    val destination: String
)
```

字段说明：

- `id`：本地数据库主键。
- `name`：用户自定义路线名称。
- `origin`：起点地址。
- `destination`：终点地址。

### 5.2 BusRouteOption

```kotlin
data class BusRouteOption(
    val routeName: String,
    val priceHkd: Double,
    val waitMinutes: Int
)
```

字段说明：

- `routeName`：巴士路线名称。
- `priceHkd`：港币价格。
- `waitMinutes`：预计车辆到站等待分钟数。

## 6. 本地存储设计

第一版使用 SQLiteOpenHelper 管理本地路线配置。

表名建议：

```sql
route_configs
```

字段建议：

```sql
id INTEGER PRIMARY KEY AUTOINCREMENT
name TEXT NOT NULL
origin TEXT NOT NULL
destination TEXT NOT NULL
created_at INTEGER NOT NULL
updated_at INTEGER NOT NULL
```

仓库层封装：

```kotlin
class RouteConfigRepository {
    fun getAll(): List<RouteConfig>
    fun getById(id: Long): RouteConfig?
    fun insert(name: String, origin: String, destination: String): Long
    fun update(config: RouteConfig)
    fun delete(id: Long)
}
```

## 7. 巴士查询设计

查询能力通过接口抽象，便于后续替换为真实 HTTP API。

```kotlin
interface BusRouteRepository {
    fun searchRoutes(origin: String, destination: String): List<BusRouteOption>
}
```

第一版实现：

```kotlin
class MockBusRouteRepository : BusRouteRepository {
    override fun searchRoutes(origin: String, destination: String): List<BusRouteOption> {
        // 根据 origin + destination 返回本地写死数据
    }
}
```

后续接真实 API 时新增：

```kotlin
class HttpBusRouteRepository : BusRouteRepository {
    override fun searchRoutes(origin: String, destination: String): List<BusRouteOption> {
        // 调用真实 HTTP API
    }
}
```

主界面只依赖 `BusRouteRepository`，不直接关心数据来自 Mock 还是 HTTP。

## 8. Mock 数据策略

Mock 数据需要和起点、终点有关。

建议第一版内置几组数据：

```text
渔湾村渔进楼 -> 兴华二村丰兴楼
```

返回示例：

| 路线 | 价格 | 预计等候时间 |
| --- | --- | --- |
| 82 | HK$6.0 | 约 4 分钟 |
| 8X | HK$7.2 | 约 9 分钟 |
| 780 | HK$10.5 | 约 13 分钟 |

```text
兴华二村丰兴楼 -> 渔湾村渔进楼
```

返回示例：

| 路线 | 价格 | 预计等候时间 |
| --- | --- | --- |
| 82 | HK$6.0 | 约 6 分钟 |
| 8X | HK$7.2 | 约 11 分钟 |

其他未匹配的起点和终点：

- 可以返回一组通用测试路线；或
- 返回空列表并展示“暂无可用巴士路线”。

第一版建议返回空列表，这样可以同时验证空状态。

## 9. 排序规则

主界面维护当前查询结果列表和排序状态。

排序字段：

```kotlin
enum class SortField {
    PRICE,
    WAIT_TIME
}
```

排序方向：

```kotlin
enum class SortDirection {
    ASC,
    DESC
}
```

交互规则：

- 第一次点击“价格”：按价格升序。
- 再次点击“价格”：按价格降序。
- 切换到“预计等候时间”：按等待时间升序。
- 再次点击“预计等候时间”：按等待时间降序。

## 10. 后续真实 API 接入预留

第一版提前加入：

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

后续拿到真实 API 后，需要补充：

- 请求 URL
- HTTP 方法
- 请求参数
- 鉴权方式
- 返回 JSON 结构
- 错误码处理
- 超时和重试策略
- 数据映射到 `BusRouteOption`

推荐后续使用：

- OkHttp：负责 HTTP 请求。
- kotlinx.serialization 或 org.json：负责 JSON 解析。

如果 API 是异步或耗时请求，需要避免在主线程直接调用，可以使用：

- Kotlin Coroutines
- 或简单的后台线程 + 主线程回调

## 11. 第一版开发顺序

建议实现顺序：

1. 添加 Kotlin 支持和 Activity 声明。
2. 创建数据模型。
3. 实现本地 SQLite 路线配置存储。
4. 实现路线管理页。
5. 实现路线编辑页。
6. 实现主界面空状态和路线下拉。
7. 实现 Mock 巴士查询。
8. 实现结果表格和排序。
9. 补充基础样式和交互细节。
10. 构建验证。

## 12. 暂不包含的功能

第一版暂不实现：

- 地图选点
- 地址搜索和联想
- 真实公交 API
- 账号登录
- 云同步
- 推送提醒
- 收藏以外的复杂路线规划
- 多语言
- 深色模式专项适配
