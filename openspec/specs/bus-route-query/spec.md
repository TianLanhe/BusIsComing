# bus-route-query Specification

## Purpose
TBD - created by archiving change build-bus-query-mvp. Update Purpose after archive.
## Requirements

### Requirement: 主界面展示无路线空状态

系统 SHALL 在不存在本地路线配置时，在主界面展示可操作的空状态。

#### Scenario: 主界面没有路线配置
- **WHEN** 用户打开主界面且本地不存在路线配置
- **THEN** 系统展示空状态和新增路线入口，并且不展示可用的查询操作

### Requirement: 主界面选择已保存路线

系统 SHALL 允许用户在主界面查询巴士方案前选择一条已保存的本地路线配置。

#### Scenario: 在选择器中展示已保存路线
- **WHEN** 用户打开主界面且本地存在路线配置
- **THEN** 系统在路线选择器中展示路线，格式为 `路线名称：起点地址 -> 终点地址`

#### Scenario: 路线配置变化后刷新选择器
- **WHEN** 用户新增、编辑或删除路线配置后返回主界面
- **THEN** 系统从本地存储重新加载路线选择器

### Requirement: 通过仓库抽象查询巴士路线

系统 SHALL 使用所选路线配置的起点地址和终点地址，通过 `BusRouteRepository` 抽象查询巴士方案。

#### Scenario: 查询所选路线
- **WHEN** 用户选择一条已保存路线配置并点击查询操作
- **THEN** 系统使用所选起点地址和终点地址调用巴士路线仓库

#### Scenario: Activity 不直接写死 Mock 数据
- **WHEN** MVP 需要生成巴士路线结果
- **THEN** 系统从仓库实现中获取结果，而不是在 Activity 中硬编码结果行

### Requirement: 提供起终点相关的 Mock 巴士结果

系统 SHALL 根据所选起点地址和终点地址提供确定性的 Mock 巴士路线结果。

#### Scenario: 查询渔湾到兴华路线
- **WHEN** 仓库查询 `渔湾村渔进楼` 到 `兴华二村丰兴楼`
- **THEN** 系统返回巴士路线 `82`、`8X`、`780`，价格分别为 `6.0`、`7.2`、`10.5`，等待时间分别为 `4`、`9`、`13`

#### Scenario: 查询兴华到渔湾路线
- **WHEN** 仓库查询 `兴华二村丰兴楼` 到 `渔湾村渔进楼`
- **THEN** 系统返回巴士路线 `82`、`8X`，价格分别为 `6.0`、`7.2`，等待时间分别为 `6`、`11`

#### Scenario: 查询未匹配路线
- **WHEN** 仓库查询未匹配 MVP Mock 数据的起点和终点组合
- **THEN** 系统返回空结果列表

### Requirement: 展示巴士路线查询结果

系统 SHALL 以表格化列表展示巴士查询结果，列包括路线、价格和预计等候时间。

#### Scenario: 展示非空查询结果
- **WHEN** 巴士查询返回一条或多条结果
- **THEN** 系统展示路线、价格和预计等候时间列，并为每条巴士路线结果展示一行

#### Scenario: 格式化价格和等待时间
- **WHEN** 系统展示巴士路线结果
- **THEN** 价格展示为 `HK$金额`，预计等候时间展示为 `约 N 分钟`

### Requirement: 展示无查询结果空状态

系统 SHALL 在所选路线没有可用巴士路线结果时展示无结果提示。

#### Scenario: 展示暂无可用巴士路线
- **WHEN** 巴士查询返回空结果列表
- **THEN** 系统清空旧结果行，并展示 `暂无可用巴士路线`

### Requirement: 预留网络权限

系统 SHALL 包含 Android 网络权限，为后续 HTTP 巴士 API 接入做准备。

#### Scenario: Manifest 包含网络权限
- **WHEN** 检查 Android Manifest
- **THEN** 其中包含 `android.permission.INTERNET`

### Requirement: 語言切換後保留查詢上下文並自動重查
系統 SHALL 在語言切換後分別保留常用與搜尋 destination 的有效起終點語義，並以新語言重新取得曾查詢過的動態路線資料。

#### Scenario: 已選常用路線時切換語言
- **WHEN** 常用 destination 已選擇一條常用路線並曾發起有效查詢，且用戶切換語言
- **THEN** 系統 SHALL 保留該常用路線 id 及原始保存名稱
- **AND** 系統 SHALL 使用其起終點座標以新語言自動重查

#### Scenario: 搜尋查詢時切換語言
- **WHEN** 搜尋 destination 已使用未保存的起終點發起有效查詢，且用戶切換語言
- **THEN** 系統 SHALL 保留搜尋起終點名稱與座標
- **AND** 系統 SHALL 使用新語言自動重查

#### Scenario: 兩個 destination 都有有效查詢
- **WHEN** 常用與搜尋 destination 均有曾發起查詢的有效起終點上下文
- **AND** 用戶切換語言
- **THEN** 兩個 destination SHALL 各自使舊 generation 失效並以新語言重查
- **AND** 任一 destination 的結果 SHALL NOT 覆蓋另一 destination 的查詢狀態

#### Scenario: destination 切換不觸發語言重查
- **WHEN** 用戶只在常用、搜尋與設定 destination 之間切換而 App 實際語言沒有改變
- **THEN** 系統 SHALL 保留各自現有查詢狀態
- **AND** 系統 SHALL NOT 將普通 destination 切換當成語言變更而重查

#### Scenario: 自動重查不記錄使用
- **WHEN** 系統因語言切換自動重查常用路線
- **THEN** 系統 SHALL NOT 增加該路線使用次數
- **AND** 系統 SHALL NOT 改變未切換其他路線前的既有使用去重狀態

#### Scenario: 舊語言漸進更新晚到
- **WHEN** 舊語言路線的 ETA、站點預覽或詳情更新在新語言查詢後返回
- **THEN** 系統 SHALL 忽略該更新
- **AND** 系統 SHALL NOT 將舊語言資料插入常用或搜尋的目前結果列表

#### Scenario: 新語言自動重查失敗
- **WHEN** 新語言路線查詢失敗
- **THEN** 系統 SHALL 清除舊結果並以新語言展示失敗狀態
- **AND** 系統 SHALL 保留起終點供用戶手動重試
