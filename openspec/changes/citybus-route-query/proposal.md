## Why

当前主界面查询仍使用本地 Mock 数据，无法根据用户保存路线的真实起终点经纬度返回可用巴士路线。现在已经明确 Citybus 路线查询 API、HTML 返回结构和展示字段，需要把查询链路升级为真实 HTTP 查询，并让结果表格展示更完整的路线信息。

## What Changes

- 新增 Citybus 点到点路线查询 HTTP 调用：
  - 请求 `https://mobile.citybus.com.hk/nwp3/ppsearch_p3.php`
  - 使用已保存路线的起点纬度、起点经度、终点纬度、终点经度。
  - 查询时间 `t` 使用当前香港时间，格式为 `yyyy-MM-dd HH:mm`。
  - `leg=2`、`m1=T`、`l=0` 等参数按用户提供 cURL 固定。
  - 请求 headers 和 Cookie 参考用户提供 cURL 构造。
- 引入 jsoup，用于解析 Citybus 返回的 HTML。
- 从 `routelist2` 区域解析每条候选路线：
  - 每个候选路线可包含一段或多段巴士路线。
  - 路线号用箭头连接展示，例如 `82X → 102`。
  - 累计每段巴士价格为总价格。
  - 提取预计路线总耗时。
  - 本阶段预计汽车到站时间先使用预计路线总耗时代替。
- 主界面查询结果表格从 3 列升级为 4 列：
  - `路线`
  - `价格(HKD)`
  - `总耗时(分钟)`
  - `预计汽车到站时间(分钟)`
- 排序交互扩展：
  - 点击路线列按中转次数排序，升序为中转次数由低到高，降序为由高到低。
  - 点击价格、总耗时、预计汽车到站时间列分别按对应数值升序/降序切换。
- 查询失败时展示明确错误状态；查询无结果仍展示“暂无可用巴士路线”。
- **BREAKING**：`BusRouteOption` 的字段含义和 UI 表格列将从旧 Mock 三列模型升级为真实查询四列模型。

## Capabilities

### New Capabilities

- `citybus-route-query-api`：定义 Citybus 路线查询 API 的请求参数、请求头、HTML 解析和错误处理规则。
- `bus-route-result-table`：定义主界面 4 列结果表格、展示格式和排序规则。

### Modified Capabilities

- `route-place-storage-and-query`：将巴士查询从 Mock 固定返回升级为基于已保存地点经纬度调用真实 Citybus 路线查询。

## Impact

- 修改 `BusRouteOption` 数据模型，增加总耗时、预计汽车到站时间和中转次数相关信息。
- 修改 `BusRouteRepository` 实现，新增真实 HTTP 查询仓库并在主界面使用。
- 新增 Citybus 路线 HTML parser 及单元测试。
- 修改 `BusRouteSorter`、排序字段枚举和排序测试。
- 修改主界面表头、结果行布局和 `BusRouteAdapter`。
- 新增 jsoup 依赖。
- 保留 `INTERNET` 权限使用。
