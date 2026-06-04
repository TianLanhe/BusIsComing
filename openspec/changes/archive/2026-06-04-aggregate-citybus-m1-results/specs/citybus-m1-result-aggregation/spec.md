## ADDED Requirements

### Requirement: 并发查询三种 Citybus m1 模式

系统 SHALL 在用户查询已保存路线时，并发请求 Citybus `m1=T`、`m1=F`、`m1=W` 三种模式，并将结果聚合为一个路线列表。

#### Scenario: 三种 m1 使用同一查询时间
- **WHEN** 用户在主界面选择已保存路线并点击查询
- **THEN** 系统 MUST 生成一次当前香港时间 `t`
- **AND** 三次 Citybus HTTP 请求 MUST 共用同一个 `t`
- **AND** 三次请求的 `m1` 参数分别为 `T`、`F`、`W`

#### Scenario: 不新增额外 Citybus query 参数
- **WHEN** 系统构造三种 `m1` 路线查询请求
- **THEN** 请求 SHALL 保持当前 App 已使用的 query 参数集合
- **AND** 请求 SHALL NOT 新增 `ws`、`loc`、`ssid`、`sysid`

#### Scenario: 聚合三种 m1 返回结果
- **WHEN** 三种 `m1` 请求返回可解析路线
- **THEN** 系统 SHALL 将三次返回的所有有效候选路线聚合为一个列表

#### Scenario: 部分 m1 请求失败
- **WHEN** 三种 `m1` 请求中至少一个请求成功且返回可解析响应
- **THEN** 系统 SHALL 展示成功请求聚合后的路线结果

#### Scenario: 三种 m1 请求全部失败
- **WHEN** 三种 `m1` 请求全部发生网络失败、HTTP 非 2xx 或 HTML 解析失败
- **THEN** 系统 SHALL 将本次路线查询视为失败

### Requirement: 聚合结果去重

系统 SHALL 对三种 `m1` 返回的聚合路线结果进行去重。

#### Scenario: 完全一致路线去重
- **WHEN** 聚合结果中多条候选路线的路线号序列、总价格、总耗时和步行距离完全一致
- **THEN** 系统 SHALL 只保留其中一条候选路线

#### Scenario: 路线号相同但属性不同不去重
- **WHEN** 聚合结果中多条候选路线的路线号序列相同，但总价格、总耗时或步行距离任一字段不同
- **THEN** 系统 SHALL 将它们视为不同候选路线并全部保留

### Requirement: 聚合结果默认按总耗时升序

系统 SHALL 在用户未手动点击排序表头前，将聚合查询结果按总耗时由低到高排序。

#### Scenario: 查询成功后的默认排序
- **WHEN** 三种 `m1` 查询完成并生成聚合结果
- **THEN** 系统 SHALL 按预计路线总耗时分钟数升序排列结果

### Requirement: 解析免费价格

系统 SHALL 支持 Citybus 返回中的免费巴士费用格式，并将其按 0 HKD 计入总价。

#### Scenario: aria-label 中包含免費
- **WHEN** 候选路线的 `aria-label` 中包含某段路线价格为 `免費` 或 `免費 *`
- **THEN** 系统 SHALL 将该段路线价格解析为 `0.0`

#### Scenario: table 文本中包含免費
- **WHEN** 候选路线 table 文本中包含某段路线价格为 `免費` 或 `免費 *`
- **THEN** 系统 SHALL 将该段路线价格解析为 `0.0`

#### Scenario: 全免费路线价格
- **WHEN** 候选路线所有路线段价格均为免费
- **THEN** 系统 SHALL 将该候选路线总价格设为 `0.0`

### Requirement: 解析步行距离

系统 SHALL 从每条 Citybus 候选路线中解析步行距离，单位为米。

#### Scenario: 从 aria-label 解析步行距离
- **WHEN** 候选路线的 `aria-label` 包含 `步行距離(約) XXX米`
- **THEN** 系统 SHALL 将 `XXX` 解析为步行距离米数

#### Scenario: 从 table 文本解析步行距离
- **WHEN** 候选路线 table 文本包含 `步行距離(約) XXX米`
- **THEN** 系统 SHALL 将 `XXX` 解析为步行距离米数

#### Scenario: 候选路线缺少步行距离
- **WHEN** 候选路线无法解析出步行距离
- **THEN** 系统 SHALL 将该候选路线视为无效候选并不展示

### Requirement: 测试指定路线的真实聚合结果

系统 SHALL 使用用户指定路线验证三种 `m1` 聚合查询效果。

#### Scenario: 验证漁灣邨漁進樓到港鐵中環站
- **WHEN** 验证环境中存在路线 `漁灣邨漁進樓 -> 港鐵中環站`
- **THEN** 起点 SHALL 使用坐标 `22.267079693838, 114.24208950984`
- **AND** 终点 SHALL 使用坐标 `22.282043425996, 114.15760138031`
- **AND** 系统 SHALL 验证查询结果包含多程巴士路线
- **AND** 系统 SHALL 验证三种 `m1` 模式存在返回差异
- **AND** 系统 SHALL 验证步行距离和免费价格解析结果
