# citybus-first-leg-eta Specification

## Purpose
记录从 Citybus 点到点候选路线解析首程 bus leg、推导上车站点、查询公开 ETA、渐进式补全候车时间以及主界面展示和排序规则。
## Requirements
### Requirement: 解析每条候选路线的首程 ETA 信息

系统 SHALL 从每条 Citybus 点到点候选路线中解析首程 bus leg，用于后续推导首程车辆 ETA。

#### Scenario: 候选路线包含 showroutep2p 信息
- **WHEN** 系统解析 `ppsearch_p3.php` 中一个包含 `showroutep2p(...)` 的候选路线元素
- **THEN** 系统 SHALL 从 `showroutep2p` 的第一个字符串参数中解析该候选路线的首程 bus leg
- **AND** 首程信息 SHALL 包含公司代码、内部路线 variant、公开路线号、上车站序、下车站序、方向代码和公开 API direction path

#### Scenario: 多段路线只取第一段
- **WHEN** 候选路线包含两段或更多 bus legs
- **THEN** 系统 SHALL 只使用第一段 bus leg 计算候车时间
- **AND** 系统 SHALL NOT 请求转乘段 ETA

#### Scenario: 缺少可解析首程信息
- **WHEN** 候选路线可以解析路线、价格、总耗时和步行距离，但缺少可解析的首程 bus leg
- **THEN** 系统 SHALL 保留该路线结果
- **AND** 该路线候车时间 SHALL 视为不可用

### Requirement: 推导首程站点并查询 ETA

系统 SHALL 使用首程 bus leg 调用 DATA.GOV.HK 城巴公开 API，推导上车站点并获取最近一班车 ETA；当 ETA 响应中的 `seq` 与首程上车站序不一致但 `route`、`stop` 和 `dir` 已匹配时，系统 SHALL 使用保守降级策略避免错误丢弃有效 ETA。

#### Scenario: 通过 route-stop 推导 stop_id
- **WHEN** 系统获得首程 company、公开 route、direction path 和 boardingSeq
- **THEN** 系统 SHALL 请求 `https://rt.data.gov.hk/v2/transport/citybus/route-stop/{company}/{route}/{direction}`
- **AND** 系统 SHALL 在响应 data 中查找 `seq == boardingSeq` 的记录
- **AND** 系统 SHALL 使用该记录的 `stop` 作为首程上车站点 `stop_id`

#### Scenario: 查询 ETA 并优先使用严格匹配
- **WHEN** 系统获得首程 company、stop_id、公开 route、原始 direction code 和 boardingSeq
- **THEN** 系统 SHALL 请求 `https://rt.data.gov.hk/v2/transport/citybus/eta/{company}/{stop_id}/{route}`
- **AND** 系统 SHALL 优先使用 `route`、`stop`、`dir` 和 `seq` 均匹配首程信息且 `eta` 非空可解析的 ETA 记录
- **AND** 系统 SHALL 使用严格匹配记录中最近的一班车计算候车时间

#### Scenario: 严格匹配缺失时降级匹配 ETA
- **WHEN** ETA 响应中没有 `route`、`stop`、`dir` 和 `seq` 均匹配且 `eta` 非空可解析的记录
- **AND** ETA 响应中存在 `route`、`stop` 和 `dir` 均匹配且 `eta` 非空可解析的记录
- **THEN** 系统 SHALL 使用这些降级匹配记录中最近的一班车计算候车时间

#### Scenario: 降级匹配仍要求路线站点和方向一致
- **WHEN** ETA 响应中的记录 `seq` 与 boardingSeq 不一致
- **AND** 该记录的 `route`、`stop` 或 `dir` 任一字段不匹配首程信息
- **THEN** 系统 SHALL NOT 使用该记录计算候车时间

#### Scenario: 计算候车分钟数
- **WHEN** 系统获得匹配 ETA 的 ISO 时间
- **THEN** 系统 SHALL 用 ETA 时间减当前系统时间计算剩余时间
- **AND** 剩余时间小于等于 0 秒时候车时间 SHALL 为 `0`
- **AND** 剩余时间大于 0 秒时候车时间 SHALL 按分钟向上取整

#### Scenario: ETA 不可用
- **WHEN** route-stop 找不到上车站点、ETA 响应没有严格匹配或降级匹配的非空可解析记录、网络请求失败或响应解析失败
- **THEN** 系统 SHALL 将该路线候车时间标记为不可用

### Requirement: 渐进式补全候车时间

系统 SHALL 在路线查询完成后立即展示聚合后的路线列表，并在后台渐进式补全候车时间。

#### Scenario: 路线聚合完成后立即展示
- **WHEN** 路线聚合去重完成并按总耗时升序排序
- **THEN** 系统 SHALL 立即展示完整路线列表
- **AND** 系统 SHALL NOT 等待前 5 条路线 ETA 完成或达到 ETA 超时后才展示路线列表

#### Scenario: 初始候车状态
- **WHEN** 系统首次展示路线列表
- **THEN** 可解析首程 ETA 信息且 ETA 尚未完成的路线候车时间 SHALL 显示为查询中
- **AND** 缺少可解析首程 ETA 信息的路线候车时间 SHALL 显示为不可用

#### Scenario: ETA 后台补全
- **WHEN** 路线列表已经展示且存在可解析首程 ETA 信息的路线
- **THEN** 系统 SHALL 在后台使用有上限的并发方式补全这些路线的 ETA
- **AND** 每条路线 ETA 完成后，系统 SHALL 更新对应结果行的候车时间显示

#### Scenario: 少于 5 条路线
- **WHEN** 聚合去重后的路线结果少于 5 条
- **THEN** 系统 SHALL 立即展示实际存在的路线
- **AND** 系统 SHALL 只为实际存在且可解析首程 ETA 信息的路线后台补全 ETA

#### Scenario: 旧查询更新被取消或忽略
- **WHEN** 用户在后台 ETA 补全期间发起新的路线查询、切换已保存路线或离开主界面
- **THEN** 系统 SHALL 取消或停止调度旧查询尚未完成的 ETA 补全任务
- **AND** 系统 MUST 忽略旧查询后续 ETA 更新

### Requirement: 候车时间列展示状态

系统 SHALL 将主界面结果表格的到站列展示为候车时间，并区分查询中、可用和不可用状态。

#### Scenario: 表头展示候车时间
- **WHEN** 主界面展示路线结果表头
- **THEN** 到站相关列 SHALL 显示为 `候车时间\n(分钟)`

#### Scenario: 候车时间可用
- **WHEN** 某条路线首程 ETA 已成功计算为分钟数
- **THEN** 该路线候车时间单元格 SHALL 只显示分钟数字

#### Scenario: 候车时间查询中
- **WHEN** 某条路线首程 ETA 正在后台查询且尚未完成
- **THEN** 该路线候车时间单元格 SHALL 显示 `...`

#### Scenario: 候车时间不可用
- **WHEN** 某条路线首程 ETA 不可用或查询失败
- **THEN** 该路线候车时间单元格 SHALL 显示 `-`

### Requirement: 候车时间排序

系统 SHALL 支持按候车时间排序，并在 ETA 渐进更新期间保持排序结果可理解。

#### Scenario: 默认仍按总耗时升序
- **WHEN** 用户完成一次路线查询且系统展示初始结果列表
- **THEN** 系统 SHALL 默认按总耗时分钟数升序展示
- **AND** 系统 SHALL NOT 因后台 ETA 更新改变默认总耗时排序

#### Scenario: 按候车时间升序排序
- **WHEN** 用户点击 `候车时间\n(分钟)` 表头切换到升序排序
- **THEN** 系统 SHALL 将已有可用候车时间的路线按分钟数升序排序
- **AND** 候车时间为 `...` 或 `-` 的路线 SHALL 排在可用候车时间之后

#### Scenario: 按候车时间降序排序
- **WHEN** 用户再次点击 `候车时间\n(分钟)` 表头切换到降序排序
- **THEN** 系统 SHALL 将已有可用候车时间的路线按分钟数降序排序
- **AND** 候车时间为 `...` 或 `-` 的路线 SHALL 仍排在可用候车时间之后

#### Scenario: 候车时间排序中后台更新
- **WHEN** 当前排序字段是候车时间且后台 ETA 更新某条路线候车时间
- **THEN** 系统 SHALL 按最新候车时间状态重新排序并刷新列表

### Requirement: route-stop 缓存和 ETA 并发控制

系统 SHALL 缓存 route-stop 查询结果并限制 ETA 补全并发，降低重复网络请求和首屏延迟。

#### Scenario: route-stop 结果缓存 1 天
- **WHEN** 系统已经成功查询某个 `(company, route, direction)` 的 route-stop 数据
- **THEN** 系统 SHALL 在 App 进程内缓存该结果 1 天
- **AND** 1 天内再次查询相同 key 时 SHALL 优先使用缓存结果

#### Scenario: route-stop 缓存过期
- **WHEN** 某个 route-stop 缓存结果保存时间超过 1 天
- **THEN** 系统 SHALL 重新请求 DATA.GOV.HK route-stop API

#### Scenario: 相同首程 ETA 请求去重
- **WHEN** 同一次路线查询中多条路线共享相同首程 company、route、direction、boardingSeq 和 stop_id
- **THEN** 系统 SHALL 只发起一次对应 ETA 请求
- **AND** 系统 SHALL 将该 ETA 结果应用到所有共享该首程的路线

#### Scenario: ETA 补全并发受限
- **WHEN** 系统需要为多条路线补全 ETA
- **THEN** 系统 SHALL 使用有上限的并发执行方式
- **AND** 系统 SHALL NOT 为每条路线创建无界线程或无界并发请求

### Requirement: 路线查询任务不被旧 ETA 补全阻塞

系统 SHALL 确保新的路线查询可以独立开始，不被上一轮路线查询遗留的 ETA 补全任务阻塞。

#### Scenario: 首次路线回调后查询任务结束
- **WHEN** 系统完成路线聚合并发出初始路线列表回调
- **THEN** 当前路线查询任务 SHALL 不再同步等待剩余 ETA 补全全部完成
- **AND** 剩余 ETA 补全 SHALL 由独立后台任务继续执行

#### Scenario: 连续点击查询
- **WHEN** 用户在上一轮 ETA 补全尚未全部完成时再次点击查询
- **THEN** 系统 SHALL 立即启动新一轮路线查询
- **AND** 新路线查询 SHALL NOT 等待上一轮剩余 ETA 补全任务完成

#### Scenario: ETA 按完成顺序更新
- **WHEN** 多个后台 ETA 请求并发执行且后提交的请求先完成
- **THEN** 系统 SHALL 允许已完成 ETA 尽快更新对应路线
- **AND** 系统 SHALL NOT 因较早提交但较慢的 ETA 请求阻塞后续已完成 ETA 的界面更新

