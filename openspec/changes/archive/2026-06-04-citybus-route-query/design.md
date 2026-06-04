## Context

当前 App 已经支持本地保存路线配置，并且每条路线保存了起点和终点的地点名称、纬度、经度。主界面查询流程已经通过 `BusRouteRepository` 抽象，但当前实现仍是 `MockBusRouteRepository`，返回固定三列结果：路线、价格、预计等候时间。

本次变更要把主界面查询切换到 Citybus 点到点路线查询接口。该接口返回 HTML，不是 JSON；可用路线位于 `routelist2` 区域，每个候选路线用一个 table 表示。每个 table 可能包含单段巴士，也可能包含多段中转巴士，需要解析路线号、逐段价格、预计总耗时，并映射到新的四列表格。

## Goals / Non-Goals

**Goals:**

- 基于已保存路线的起点和终点经纬度调用 Citybus 路线查询 API。
- 查询时间使用当前香港时间，格式为 `yyyy-MM-dd HH:mm`。
- 使用与用户 cURL 一致的关键 headers 和 Cookie。
- 使用 jsoup 解析 Citybus HTML。
- 从 `routelist2` 中解析所有候选路线。
- 支持单段和多段中转路线，路线号用 `→` 连接展示。
- 累计每段价格为总价格。
- 解析预计路线总耗时，并暂时复用为预计汽车到站时间。
- 主界面结果表格升级为 4 列，并展示单位。
- 支持按路线中转次数、价格、总耗时、预计汽车到站时间排序。
- 查询中、查询失败、查询无结果状态在 UI 上清晰可见。

**Non-Goals:**

- 不实现真实 ETA 到站时间解析；本阶段预计汽车到站时间等于总耗时。
- 不实现路线详情页、站点详情、地图路线或步行距离展示。
- 不实现请求重试、缓存、分页或后台定时刷新。
- 不支持用户配置查询时间，始终使用当前时间。
- 不处理除 Citybus 当前 HTML 结构以外的其他供应商格式。

## Decisions

### 决策 1：新增真实查询仓库，保留 Repository 抽象

新增 `CitybusBusRouteRepository` 实现 `BusRouteRepository`，主界面依旧依赖接口，不直接写 HTTP 或解析逻辑。

理由：

- 符合现有轻量 Repository 分层。
- 后续如果 Citybus API 变化，可以替换仓库或 parser，不需要重写 UI。
- 单元测试可以分别覆盖 URL 构造、headers、HTML parser 和排序。

备选方案：

- 直接在 `MainActivity` 中发请求：实现快，但会让 UI 直接依赖网络和 HTML 结构，后续维护成本高。
- 继续保留 Mock 并只改 UI：无法满足真实查询目标。

### 决策 2：使用 `HttpURLConnection` 发起请求

路线查询仓库沿用地点搜索仓库当前的原生 `HttpURLConnection` 方式，并复用类似的超时、headers 组装和错误处理模式。

理由：

- 当前项目没有 OkHttp/Retrofit。
- 只有一个新增 GET 接口，引入完整 HTTP 框架收益不高。
- 与 `CitybusPlaceSearchRepository` 风格一致。

备选方案：

- 引入 OkHttp：更好用，但对当前单接口场景偏重。

### 决策 3：使用 jsoup 解析 HTML

新增 jsoup 依赖，并实现 `CitybusRouteParser`。解析时优先定位 id 规范化后为 `routelist2` 的节点，再解析其内部候选 table。

理由：

- 返回是嵌套 HTML，且样例中存在属性空格、嵌套 table 和非严格格式。
- jsoup 对不规整 HTML 的容错能力比正则更稳定。

解析策略：

- 先查找 `id` trim 后等于 `routelist2` 的元素，因为样例中 `id="routelist2 "` 带尾随空格。
- 在该元素下找出带 `aria-label` 且包含 `預計` 的候选 table。
- 优先从 `aria-label` 提取：
  - 路线号：匹配每个 `路线号 港元价格` 片段。
  - 价格：提取所有 `港元x.x` 并求和。
  - 总耗时：提取 `預計N分鐘`。
- 如果 `aria-label` 缺失或不完整，再从 table 文本提取路线号、`$x.x` 价格和 `預計 N 分鐘`。
- 去除无法同时得到路线号、价格和总耗时的无效候选。
- 如果 `routelist2` 存在但没有有效候选，返回空列表。
- 如果 HTML 格式无法识别或没有 `routelist2`，视为解析失败。

备选方案：

- 只用正则解析完整 HTML：对嵌套和属性空格较脆弱。
- 只解析 `showroutep2p` 参数：可以提取总耗时和路线 id，但价格仍需 table 或 aria-label，不能单独满足需求。

### 决策 4：扩展 `BusRouteOption` 为四列表格模型

将查询结果模型扩展为：

```kotlin
data class BusRouteOption(
    val routeName: String,
    val priceHkd: Double,
    val durationMinutes: Int,
    val arrivalMinutes: Int,
    val transferCount: Int
)
```

其中：

- `routeName`：如 `82X → 102`。
- `priceHkd`：多段价格求和。
- `durationMinutes`：预计路线总耗时。
- `arrivalMinutes`：本阶段等于 `durationMinutes`。
- `transferCount`：中转次数，单段路线为 `0`，两段路线为 `1`，即 `max(0, routeSegments - 1)`。

理由：

- 直接服务新表格和排序需求。
- 让路线排序不依赖 UI 文本拆分。

备选方案：

- 只保存 routeName 并在排序时按箭头数量推导：实现简单但不稳，且 UI 文本变化会影响排序。

### 决策 5：主界面查询异步执行

网络查询必须在后台线程执行，UI 线程只负责状态切换和渲染。沿用轻量 `ExecutorService + Handler` 模式，不引入协程。

UI 状态：

- 查询前：保留当前选择路线和空结果状态。
- 查询中：禁用查询按钮，显示“查询中...”或轻量加载文案，避免重复请求。
- 查询成功且有结果：展示表格，隐藏空状态。
- 查询成功但无结果：显示“暂无可用巴士路线”。
- 查询失败：显示“路线查询失败，请稍后重试”，清空旧结果，恢复查询按钮。

理由：

- 当前项目没有 Kotlin 协程依赖。
- 与路线编辑页地点搜索的异步模式一致。

### 决策 6：四列表格采用紧凑但可读的文本布局

结果表头改为：

- `路线`
- `价格(HKD)`
- `总耗时(分钟)`
- `预计汽车到站时间(分钟)`

结果行中：

- 价格单元格只显示数字，例如 `20.4`。
- 总耗时和预计到站时间只显示数字，例如 `34`。
- 路线列展示多段路线号，例如 `82X → 102`。

表格列宽采用约束权重或水平可读布局，避免长表头挤压。必要时让表头文本换行，例如 `预计汽车到站时间\n(分钟)`。

理由：

- 单元格数字更短，表头已经给出单位。
- 四列在手机宽度上需要控制文本长度，避免内容重叠。

## Risks / Trade-offs

- [Risk] Citybus HTML 结构变化会导致 parser 失效。→ Mitigation：parser 优先用 `aria-label`，并提供 table 文本兜底；格式异常时显示失败状态并用单元测试覆盖样例 HTML。
- [Risk] Cookie 可能过期或接口拒绝请求。→ Mitigation：第一版按用户提供 cURL 保持 headers 和 Cookie；HTTP 非 2xx 显示失败状态，后续再做 Cookie 更新或无 Cookie 调研。
- [Risk] 查询返回很多候选路线，主线程排序或渲染可能卡顿。→ Mitigation：解析在后台执行，RecyclerView 渲染列表；预期数据量较小，排序在内存中完成。
- [Risk] 四列表头在小屏设备上拥挤。→ Mitigation：表头允许换行，行高和列权重固定，手动验证主流模拟器宽度。
- [Risk] “预计汽车到站时间”暂时等于总耗时，语义不完全准确。→ Mitigation：在规格和代码注释中明确这是阶段性替代，后续真实 ETA 再单独变更。

## Migration Plan

- 更新数据模型、排序字段和 UI 表格。
- 新增 Citybus 路线查询仓库和 parser。
- 将 `MainActivity` 默认仓库从 Mock 切换到真实仓库。
- 保留或调整 Mock 仓库用于单元测试和无网络开发兜底。
- 运行 `./gradlew build` 和 parser/sorter 单元测试。
- 使用模拟器验证新增路线后真实查询、无结果、失败和排序行为。

## Open Questions

无。用户已确认查询时区、单元格显示格式、排序列、jsoup 依赖和多段路线展示方式。
