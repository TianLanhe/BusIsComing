## 上下文

当前路线配置只保存 `origin` 和 `destination` 两个纯文本字段，后续真实巴士路线查询需要使用经纬度，因此纯文本路线配置已经不够。用户已经提供 Citybus 地点搜索接口示例，该接口通过关键词返回候选地点名称、纬度、经度。

这次变更要把路线管理从“输入纯文本起点/终点”升级为“搜索并选择地点”，并把选中的地点信息保存到本地数据库。当前巴士路线查询仍使用 Mock 数据，但接口形态需要为后续经纬度查询做准备。

## 目标 / 非目标

**目标：**

- 新增 `Place` 模型，包含地点名称、纬度、经度。
- 新增 Citybus 地点搜索 HTTP 仓库和纯文本响应 parser。
- 起点和终点输入框支持输入 1 个字后自动搜索，使用 debounce。
- 地点搜索结果通过下拉列表展示，最多展示接口返回的 100 条。
- 用户必须从下拉候选中选择起点和终点地点后才能保存路线。
- 用户修改地点输入框文本后，清空已选择地点，必须重新选择。
- 起点和终点名称、纬度、经度完全相同时禁止保存。
- 本地数据库升级到地点字段；旧纯文本路线配置删除重建。
- 主界面和路线管理页只展示地点名称。
- Mock 巴士查询忽略起终点，固定返回测试数据。

**非目标：**

- 不实现真实巴士路线查询 API。
- 不实现地图选点、地址联想之外的地图能力。
- 不保存地点唯一 id，因为接口没有提供唯一 id。
- 不做离线地点缓存。
- 不做复杂重试、分页或搜索历史。

## 技术决策

### 决策 1：使用 `Place` 值对象表示地点

新增：

```kotlin
data class Place(
    val name: String,
    val latitude: Double,
    val longitude: Double
)
```

`RouteConfig` 调整为：

```kotlin
data class RouteConfig(
    val id: Long,
    val name: String,
    val origin: Place,
    val destination: Place
)
```

理由：

- 让 UI、数据库和后续查询都围绕统一地点对象工作。
- 明确纬度和经度是路线配置的一部分，而不是临时 UI 状态。

### 决策 2：SQLite 表升级为显式地点字段

将 `route_configs` 表升级为：

```sql
id INTEGER PRIMARY KEY AUTOINCREMENT
name TEXT NOT NULL
origin_name TEXT NOT NULL
origin_latitude REAL NOT NULL
origin_longitude REAL NOT NULL
destination_name TEXT NOT NULL
destination_latitude REAL NOT NULL
destination_longitude REAL NOT NULL
created_at INTEGER NOT NULL
updated_at INTEGER NOT NULL
```

数据库版本升级时直接删除旧表并重建，旧路线配置不迁移。

理由：

- 旧数据只有纯文本，没有经纬度，迁移会产生不可信数据。
- 用户已确认采用删除旧路线配置的方案。

### 决策 3：地点搜索使用轻量 HTTP 实现

新增 `PlaceSearchRepository` 和 `CitybusPlaceSearchRepository`。第一版使用 JDK/Android 原生 `HttpURLConnection`，不引入 OkHttp 或 Retrofit。

请求规则：

- URL：`https://mobile.citybus.com.hk/nwp3/bsearch_p3.php`
- Query：
  - `l=0`
  - `q=<URLEncoder 编码后的用户输入>`
  - `limit=100`
  - `timestamp=<System.currentTimeMillis()>`
- Headers 按用户提供的 cURL 保持，包括 `Accept`、`Accept-Language`、`Referer`、`User-Agent`、`X-Requested-With`、`Sec-Fetch-*`、`sec-ch-*` 和 Cookie。
- Cookie 来自用户提供的 cURL `-b` 参数；实现时不要打印 Cookie 日志。

理由：

- 当前项目依赖较轻，原生 HTTP 足够完成单接口文本请求。
- 后续如果 API 增多，再考虑 OkHttp。

### 决策 4：纯文本 parser 独立测试

新增 `CitybusPlaceParser`，把 HTTP 返回文本解析为 `List<Place>`。

解析规则：

- 空响应或格式异常返回错误。
- 第一行表头跳过。
- 如果剩余有效内容为 `No Result`，返回空列表。
- 正常行按 `|` 分割，至少需要 4 列。
- 第 2 列是展示和保存的地点名称。
- 第 3 列解析为纬度。
- 第 4 列解析为经度。
- 任一正常行格式不满足要求时，该行可跳过；如果没有任何有效地点且不是 `No Result`，视为错误。

理由：

- Citybus 返回不是 JSON，parser 必须隔离，便于单元测试。

### 决策 5：RouteEditActivity 使用可输入下拉和 debounce

起点和终点使用 `MaterialAutoCompleteTextView` + adapter 展示候选地点。

交互规则：

- 输入长度达到 1 个字符后触发搜索。
- 使用 300ms debounce，减少连续输入产生的请求。
- 每次输入框文本变化都清空对应已选择地点，除非变化来自用户选择候选。
- 选择候选后记录 `Place` 对象，并把输入框文本设置为地点名称。
- 无结果显示“没有匹配地点”。
- 网络失败或解析失败显示“地点搜索失败，请稍后重试”。
- 保存时必须 route name、origin place、destination place 均有效。
- 起终点 `name`、`latitude`、`longitude` 完全相同时禁止保存。

理由：

- 下拉选择能保证保存的数据一定包含经纬度。
- 自动搜索符合用户要求。

### 决策 6：Mock 巴士查询固定返回

`BusRouteRepository` 接口调整为接收起点/终点地点或经纬度。`MockBusRouteRepository` 暂时忽略参数，任意起点和终点都返回同一组测试数据。

理由：

- 用户已确认当前 Mock 不再按起终点匹配。
- 后续真实巴士 API 会使用经纬度参数替换 Mock。

## 风险 / 权衡

- [风险] cURL 中 Cookie 可能过期，导致接口后续不可用。→ 缓解：第一版按用户要求保持 headers，包括 Cookie；若失败，再单独做接口稳定性变更。
- [风险] 每输入 1 个字即搜索，请求频率可能较高。→ 缓解：使用 debounce，并在新请求发起时忽略旧请求结果。
- [风险] 返回 100 条候选下拉较长。→ 缓解：按用户要求展示 100 条；保持列表可滚动。
- [风险] 删除旧路线配置会丢失用户已保存路线。→ 缓解：这是用户确认的迁移方案；数据库升级行为需要在任务和验收中明确。
- [风险] 原生 HTTP 异步代码较分散。→ 缓解：仓库封装网络请求，Activity 只处理状态和回调。

## 验证计划

- 单元测试覆盖 Citybus 文本 parser：
  - 正常返回。
  - `No Result`。
  - 空响应。
  - 格式错误。
  - 部分无效行。
- 单元测试覆盖起终点相同判断。
- 构建验证执行 `./gradlew build`。
- 模拟器手动验证：
  - 输入 1 个字后出现候选下拉。
  - 选择起点和终点后可以保存。
  - 未选择候选不能保存。
  - 起终点完全相同不能保存。
  - 主界面和路线管理页只展示地点名称。
