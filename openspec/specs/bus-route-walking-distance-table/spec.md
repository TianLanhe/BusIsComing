# bus-route-walking-distance-table Specification

## Purpose
TBD - created by archiving change aggregate-citybus-m1-results. Update Purpose after archive.
## Requirements
### Requirement: 主界面展示五列巴士路线结果

系统 SHALL 在主界面以五列表格展示聚合后的巴士路线查询结果。

#### Scenario: 展示五列表头
- **WHEN** 主界面展示查询结果区域
- **THEN** 表头 SHALL 依次显示 `路线`、`价格(HKD)`、`总耗时(分钟)`、`预计汽车到站时间(分钟)`、`步行距离(米)`

#### Scenario: 展示步行距离单元格
- **WHEN** 查询结果包含步行距离米数
- **THEN** 步行距离列 SHALL 只显示数值
- **AND** 单元格 SHALL NOT 重复显示 `米`

#### Scenario: 展示免费价格累计结果
- **WHEN** 查询结果包含免费路线段
- **THEN** 价格列 SHALL 显示免费路线段按 0 HKD 累计后的总价格数值

#### Scenario: 展示全免费路线价格
- **WHEN** 查询结果中某条候选路线总价格为 0 HKD
- **THEN** 价格列 SHALL 显示 `0.0`

### Requirement: 五列表格支持横向滚动

系统 SHALL 允许结果表格横向滚动，以保证五列表头和数据在手机宽度下可读。

#### Scenario: 小屏宽度展示五列表格
- **WHEN** 设备宽度不足以完整展示五列表格
- **THEN** 用户 SHALL 能横向滚动查看所有列

#### Scenario: 表头和行列宽一致
- **WHEN** 用户横向查看结果表格
- **THEN** 表头列和结果行列 SHALL 保持对齐

### Requirement: 查询结果支持五列排序

系统 SHALL 支持用户点击五个可排序表头切换升序和降序。

#### Scenario: 默认按总耗时升序排序
- **WHEN** 查询成功并展示聚合结果
- **THEN** 系统 SHALL 默认按 `总耗时(分钟)` 升序排序
- **AND** `总耗时(分钟)` 表头 SHALL 显示升序标识 `↑`

#### Scenario: 按路线中转次数排序
- **WHEN** 用户点击 `路线` 表头
- **THEN** 系统 SHALL 按中转次数升序排序；再次点击同一表头时按中转次数降序排序

#### Scenario: 按价格排序
- **WHEN** 用户点击 `价格(HKD)` 表头
- **THEN** 系统 SHALL 按总价格数值升序排序；再次点击同一表头时按总价格数值降序排序

#### Scenario: 按总耗时排序
- **WHEN** 用户点击 `总耗时(分钟)` 表头
- **THEN** 系统 SHALL 按预计路线总耗时升序排序；再次点击同一表头时按预计路线总耗时降序排序

#### Scenario: 按预计汽车到站时间排序
- **WHEN** 用户点击 `预计汽车到站时间(分钟)` 表头
- **THEN** 系统 SHALL 按预计汽车到站时间升序排序；再次点击同一表头时按预计汽车到站时间降序排序

#### Scenario: 按步行距离排序
- **WHEN** 用户点击 `步行距离(米)` 表头
- **THEN** 系统 SHALL 按步行距离米数升序排序；再次点击同一表头时按步行距离米数降序排序

#### Scenario: 展示当前排序方向
- **WHEN** 用户对任一表头应用排序
- **THEN** 当前排序表头 SHALL 显示 `↑` 或 `↓` 表示排序方向
- **AND** 其他表头 SHALL NOT 显示排序方向

