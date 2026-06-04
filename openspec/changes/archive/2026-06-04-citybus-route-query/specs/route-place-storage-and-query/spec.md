## MODIFIED Requirements

### Requirement: 巴士查询使用地点信息

系统 SHALL 让巴士查询流程使用已保存路线中的起点和终点地点信息执行真实路线查询。

#### Scenario: 查询时传递地点经纬度
- **WHEN** 用户选择已保存路线并点击查询
- **THEN** 系统向巴士查询仓库传递起点和终点的经纬度

#### Scenario: 真实查询返回路线数据
- **WHEN** 巴士查询仓库收到任意有效起点和终点地点信息
- **THEN** 系统 SHALL 使用起点和终点经纬度调用真实 Citybus 路线查询接口并返回解析后的候选路线数据

#### Scenario: 真实查询无结果
- **WHEN** Citybus 路线查询成功但没有任何有效候选路线
- **THEN** 系统 SHALL 返回空路线结果列表

#### Scenario: 真实查询失败
- **WHEN** Citybus 路线查询网络请求失败或响应无法解析
- **THEN** 系统 SHALL 向主界面提供路线查询失败状态
