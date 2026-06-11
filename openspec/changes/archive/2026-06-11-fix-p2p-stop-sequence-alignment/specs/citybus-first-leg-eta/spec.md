## MODIFIED Requirements

### Requirement: 推导首程站点并查询 ETA

系统 SHALL 使用首程 bus leg 和 Citybus P2P stop map 推导上车站点 `stop_id`，再调用 DATA.GOV.HK 城巴公开 ETA API 获取最近一班车 ETA；当 ETA 响应中的 `seq` 与首程上车站序不一致但 `route`、`stop` 和 `dir` 已匹配时，系统 SHALL 使用保守降级策略避免错误丢弃有效 ETA。

#### Scenario: 通过 P2P stop map 推导 stop_id
- **WHEN** 系统获得首程 bus leg、完整 `rawInfo` 和 `lang`
- **THEN** 系统 SHALL 使用 `rawInfo + lang` 查询或读取缓存的 P2P stop map
- **AND** 系统 SHALL 在 P2P stop map 中查找第一段 bus leg 的 `routeVariant + boardingSeq`
- **AND** 系统 SHALL 使用该站点的 `stop_id` 作为首程上车站点 `stop_id`

#### Scenario: 不通过 route-stop 推导 stop_id
- **WHEN** 系统需要推导首程 ETA 的 `stop_id`
- **THEN** 系统 SHALL NOT 調用 `https://rt.data.gov.hk/v2/transport/citybus/route-stop/{company}/{route}/{direction}` 作為運行時 stopId 來源
- **AND** 系统 SHALL NOT 在 P2P stop map 不可用时回退到 route-stop 推导

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
- **WHEN** P2P stop map 不可用、P2P stop map 找不到首程上车站点、ETA 响应没有严格匹配或降级匹配的非空可解析记录、网络请求失败或响应解析失败
- **THEN** 系统 SHALL 将该路线候车时间标记为不可用

## ADDED Requirements

### Requirement: P2P stop map 和 ETA 并发控制

系统 SHALL 缓存 P2P stop map 查询结果并限制 ETA 补全并发，降低重复网络请求和首屏延迟。

#### Scenario: P2P stop map 结果缓存 1 天
- **WHEN** 系统已经成功解析某个 `rawInfo + lang` 的 P2P stop map
- **THEN** 系统 SHALL 在 App 进程内缓存该结果 1 天
- **AND** 1 天内再次查询相同 key 时 SHALL 优先使用缓存结果

#### Scenario: P2P stop map 缓存过期
- **WHEN** 某个 P2P stop map 缓存结果保存时间超过 1 天
- **THEN** 系统 SHALL 重新请求 `showstops2.php`
- **AND** 新的成功解析结果 SHALL 替换旧缓存

#### Scenario: 相同首程 ETA 请求去重
- **WHEN** 同一次路线查询中多条路线共享相同首程 company、route、direction、boardingSeq 和 P2P stopId
- **THEN** 系统 SHALL 只发起一次对应 ETA 请求
- **AND** 系统 SHALL 将该 ETA 结果应用到所有共享该首程的路线

#### Scenario: ETA 补全并发受限
- **WHEN** 系统需要为多条路线补全 ETA
- **THEN** 系统 SHALL 使用有上限的并发执行方式
- **AND** 系统 SHALL NOT 为每条路线创建无界线程或无界并发请求

## REMOVED Requirements

### Requirement: route-stop 缓存和 ETA 并发控制
**Reason**: 首程 ETA stopId 不再通过 DATA.GOV.HK `route-stop` 推导，route-stop 成功缓存和过期刷新不再属于运行时 ETA 补全要求。
**Migration**: 使用 `P2P stop map 和 ETA 并发控制` 要求缓存 `rawInfo + lang` 对应的 P2P stop map，并保留 ETA 请求去重和并发限制。
