## MODIFIED Requirements

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
