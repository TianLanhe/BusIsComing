## MODIFIED Requirements

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

## ADDED Requirements

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
