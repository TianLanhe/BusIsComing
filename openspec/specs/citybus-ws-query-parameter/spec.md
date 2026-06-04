# citybus-ws-query-parameter Specification

## Purpose
TBD - created by archiving change add-citybus-ws-query-param. Update Purpose after archive.
## Requirements
### Requirement: Citybus 路线查询携带固定 ws 参数

系统 SHALL 在每一次 Citybus `ppsearch_p3.php` 路线查询请求中携带固定 query 参数 `ws=1.3`。

#### Scenario: 单个路线查询 URL 包含 ws
- **WHEN** 系统为已保存路线构造 Citybus 路线查询 URL
- **THEN** URL MUST 包含 query 参数 `ws=1.3`

#### Scenario: 三种 m1 请求都包含 ws
- **WHEN** 用户在主界面点击查询并触发 `m1=T`、`m1=F`、`m1=W` 三次请求
- **THEN** 三次请求 MUST 都包含 `ws=1.3`
- **AND** 三次请求 MUST 共用同一个查询时间 `t`

#### Scenario: 不新增其他网页会话参数
- **WHEN** 系统构造 Citybus 路线查询 URL
- **THEN** URL SHALL NOT 包含 `loc`
- **AND** URL SHALL NOT 包含 `ssid`
- **AND** URL SHALL NOT 包含 `sysid`

### Requirement: ws 参数下三种 m1 返回差异可验证

系统 SHALL 支持在 `ws=1.3` 参数下重新验证三种 `m1` 返回差异，并将差异结果用于聚合展示。

#### Scenario: m1 W 样例解析
- **WHEN** 系统解析用户提供的 `m1=W` HTML 样例
- **THEN** 系统 SHALL 解析出 12 条有效候选路线
- **AND** 结果 SHALL 包含 `8 → 90`、`8 → E11A`、`8 → 10`、`788`、`780`
- **AND** `8 → 90` 的步行距离 SHALL 为 `95`

#### Scenario: m1 F 样例解析
- **WHEN** 系统解析用户提供的 `m1=F` HTML 样例
- **THEN** 系统 SHALL 解析出 4 条有效候选路线
- **AND** 结果 SHALL 包含 `8X → 10`、`8X → 1`、`780`、`788`
- **AND** `8X → 10` 的总价格 SHALL 为 `8.1`
- **AND** `8X → 10` 的步行距离 SHALL 为 `355`

#### Scenario: m1 T 样例解析
- **WHEN** 系统解析用户提供的 `m1=T` HTML 样例
- **THEN** 系统 SHALL 解析出 4 条有效候选路线
- **AND** 结果 SHALL 包含 `789 → 619`、`789 → 15`、`788`、`780`
- **AND** `789 → 619` 的总价格 SHALL 为 `16.4`
- **AND** `789 → 619` 的总耗时 SHALL 为 `28`
- **AND** `789 → 619` 的步行距离 SHALL 为 `363`

#### Scenario: 真实接口带 ws 与不带 ws 对照
- **WHEN** 验证人员使用同一组起终点坐标、同一个查询时间和同一个 `m1` 分别请求不带 `ws` 与带 `ws=1.3` 的 Citybus URL
- **THEN** 验证记录 SHALL 包含两次请求的完整 URL、HTTP 状态码、响应大小、候选路线数量和代表路线
- **AND** 验证记录 SHALL 明确 `ws=1.3` 是否返回比不带 `ws` 更完整的路线集合
- **AND** 当 `ws=1.3` 返回更多候选路线时，验证记录 SHALL 列出新增的代表性多程路线

#### Scenario: 真实接口三种 m1 cURL 对照
- **WHEN** 验证人员使用同一组起终点坐标、同一个查询时间和 `ws=1.3` 分别请求 `m1=T`、`m1=F`、`m1=W`
- **THEN** 验证记录 SHALL 包含三种请求的 HTTP 状态码、候选路线数量和代表路线
- **AND** 验证记录 SHALL 明确三种 `m1` 返回集合是否存在差异

### Requirement: ws 参数下聚合结果保持现有规则

系统 SHALL 在加入 `ws=1.3` 后继续使用当前三模式聚合、去重和默认排序规则。

#### Scenario: 三种样例合并去重
- **WHEN** 系统将用户提供的 `m1=T`、`m1=F`、`m1=W` 三组样例结果合并
- **THEN** 系统 SHALL 按路线号序列、总价格、总耗时和步行距离完全一致去重
- **AND** 合并后 SHALL 得到 16 条唯一路线

#### Scenario: 重复直达路线去重
- **WHEN** `788` 和 `780` 在 `m1=T`、`m1=F`、`m1=W` 样例中以相同路线号序列、总价格、总耗时和步行距离重复出现
- **THEN** 系统 SHALL 只保留每条重复直达路线的一份

#### Scenario: 相同路线号但属性不同不去重
- **WHEN** 三种 `m1` 结果中出现路线号序列相同但总耗时或步行距离不同的候选路线
- **THEN** 系统 SHALL 将这些候选视为不同结果并全部保留

#### Scenario: 合并后默认排序
- **WHEN** 三种 `m1` 查询完成并生成合并结果
- **THEN** 系统 SHALL 默认按总耗时分钟数升序展示
- **AND** 主界面表头 SHALL 显示 `总耗时(分钟) ↑`

### Requirement: ws 参数下异常和边界情况可验证

系统 SHALL 覆盖加入 `ws=1.3` 后的异常和边界情况，确保查询失败、部分失败和异常 HTML 不破坏现有体验。

#### Scenario: 一个或两个 m1 请求失败
- **WHEN** 三种 `m1` 请求中只有一到两个请求失败
- **THEN** 系统 SHALL 展示成功请求解析并聚合后的路线结果

#### Scenario: 三种 m1 请求全部失败
- **WHEN** 三种 `m1` 请求全部发生网络失败、HTTP 非 2xx 或 HTML 解析失败
- **THEN** 系统 SHALL 将本次路线查询视为失败

#### Scenario: 某个 m1 返回无有效候选
- **WHEN** 某个 `m1` 响应可解析但没有有效候选路线
- **THEN** 系统 SHALL 使用其他成功模式的有效候选路线继续聚合

#### Scenario: 三种 m1 均无有效候选
- **WHEN** 三种 `m1` 响应均可解析但没有有效候选路线
- **THEN** 主界面 SHALL 显示 `暂无可用巴士路线`

#### Scenario: 查询结果行数增加
- **WHEN** `ws=1.3` 使合并结果包含比原先更多的候选路线
- **THEN** 主界面 SHALL 通过 RecyclerView 正常滚动展示所有结果
- **AND** 五列表格横向滚动和表头排序 SHALL 保持可用

