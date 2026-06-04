## ADDED Requirements

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

系统 SHALL 使用首程 bus leg 调用 DATA.GOV.HK 城巴公开 API，推导上车站点并获取最近一班车 ETA。

#### Scenario: 通过 route-stop 推导 stop_id
- **WHEN** 系统获得首程 company、公开 route、direction path 和 boardingSeq
- **THEN** 系统 SHALL 请求 `https://rt.data.gov.hk/v2/transport/citybus/route-stop/{company}/{route}/{direction}`
- **AND** 系统 SHALL 在响应 data 中查找 `seq == boardingSeq` 的记录
- **AND** 系统 SHALL 使用该记录的 `stop` 作为首程上车站点 `stop_id`

#### Scenario: 查询并过滤 ETA
- **WHEN** 系统获得首程 company、stop_id、公开 route、原始 direction code 和 boardingSeq
- **THEN** 系统 SHALL 请求 `https://rt.data.gov.hk/v2/transport/citybus/eta/{company}/{stop_id}/{route}`
- **AND** 系统 SHALL 只使用 `route`、`stop`、`dir` 和 `seq` 均匹配首程信息的 ETA 记录
- **AND** 系统 SHALL 使用匹配记录中最近的一班车计算候车时间

#### Scenario: 计算候车分钟数
- **WHEN** 系统获得匹配 ETA 的 ISO 时间
- **THEN** 系统 SHALL 用 ETA 时间减当前系统时间计算剩余时间
- **AND** 剩余时间小于等于 0 秒时候车时间 SHALL 为 `0`
- **AND** 剩余时间大于 0 秒时候车时间 SHALL 按分钟向上取整

#### Scenario: ETA 不可用
- **WHEN** route-stop 找不到上车站点、ETA 响应没有匹配记录、网络请求失败或响应解析失败
- **THEN** 系统 SHALL 将该路线候车时间标记为不可用

### Requirement: 渐进式补全候车时间

系统 SHALL 在路线查询完成后优先补全总耗时最低的前 5 条路线 ETA，并在剩余路线后台补全期间保持列表可用。

#### Scenario: 前 5 条 ETA 完成后展示
- **WHEN** 路线聚合去重完成并按总耗时升序排序
- **THEN** 系统 SHALL 优先并发查询前 5 条路线的首程 ETA
- **AND** 当前 5 条路线 ETA 均完成或失败时，系统 SHALL 展示完整路线列表

#### Scenario: 前 5 条 ETA 最多等待 5 秒
- **WHEN** 前 5 条路线中仍有 ETA 查询未完成且等待时间达到 5 秒
- **THEN** 系统 SHALL 展示完整路线列表
- **AND** 未完成的前 5 条路线候车时间 SHALL 显示为查询中
- **AND** 系统 SHALL 在后台继续补全这些未完成的 ETA，并在完成后更新对应结果行

#### Scenario: 少于 5 条路线
- **WHEN** 聚合去重后的路线结果少于 5 条
- **THEN** 系统 SHALL 只优先补全实际存在的路线 ETA

#### Scenario: 剩余路线后台补全
- **WHEN** 完整路线列表已经展示且仍有非首屏路线 ETA 未完成
- **THEN** 系统 SHALL 在后台继续补全剩余路线 ETA
- **AND** 每条路线 ETA 完成后，系统 SHALL 更新对应结果行的候车时间显示

#### Scenario: 旧查询更新被忽略
- **WHEN** 用户在后台 ETA 补全期间发起新的路线查询、切换已保存路线或离开主界面
- **THEN** 系统 SHALL 忽略旧查询后续 ETA 更新

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
