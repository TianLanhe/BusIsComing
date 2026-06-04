## Why

当前主界面结果表格中的“预计汽车到站时间”仍使用路线总耗时作为阶段性替代，无法反映首程车辆真实抵站时间。现在已经确认可以从 Citybus `ppsearch_p3.php` 候选路线中的 `showroutep2p(...)` 推导首程上车站点，并调用 DATA.GOV.HK 城巴公开 ETA API 计算候车时间，因此需要把该列升级为真实首程候车时间。

## What Changes

- 为每条聚合去重后的展示路线解析首程 bus leg，包括公司、路线 variant、公开路线号、上车站序和方向。
- 通过 DATA.GOV.HK `route-stop` API 推导首程上车站 `stop_id`，再通过 ETA API 获取最近一班车抵站时间。
- 将主界面结果表格的“预计汽车到站时间”改为“候车时间(分钟)”，行内展示候车分钟数。
- 查询结果默认继续按总耗时升序展示。
- 优先并发补全总耗时最低的前 5 条路线 ETA；前 5 条完成、失败或等待最多 5 秒后展示完整结果列表。
- 剩余路线继续后台计算首程 ETA，并在完成后更新对应行。
- ETA 查询中显示 `...`，ETA 不可用或失败显示 `-`。
- 用户按候车时间排序时，已有 ETA 按分钟排序，`...` 和 `-` 排在最后；后台 ETA 回来后如当前仍按候车时间排序则自动重排。
- 对 `route-stop` 结果做进程内缓存，按 `(company, route, direction)` 缓存 1 天，减少重复网络请求。

## Capabilities

### New Capabilities

- `citybus-first-leg-eta`: 定义从 Citybus 点到点候选路线推导首程 ETA、渐进式补全候车时间、缓存和表格展示规则。

### Modified Capabilities

无。

## Impact

- 影响 `CitybusRouteParser`：需要从每个候选路线元素提取并解析 `showroutep2p(...)` 的首程信息。
- 影响 `CitybusBusRouteRepository` 和相关仓库模型：需要支持路线结果先返回、首程 ETA 后续补全，以及受限并发和缓存。
- 影响 `BusRouteOption`、排序逻辑和主界面 adapter：候车时间需要区分查询中、可用、不可用三种状态。
- 影响主界面表格文案：`预计汽车到站时间\n(分钟)` 改为 `候车时间\n(分钟)`。
- 影响单元测试、parser fixture、仓库并发/缓存测试、排序测试和模拟器验证。
