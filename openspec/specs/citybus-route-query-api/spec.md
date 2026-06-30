# citybus-route-query-api Specification

## Purpose
TBD - created by archiving change citybus-route-query. Update Purpose after archive.
## Requirements
### Requirement: 通过 Citybus 接口查询点到点巴士路线

系统 SHALL 使用 Citybus 点到点路线查询接口，根据已保存路线的起点和终点经纬度查询可选巴士路线。

#### Scenario: 发起路线查询请求
- **WHEN** 用户在主界面选择已保存路线并点击查询
- **THEN** 系统请求 `https://mobile.citybus.com.hk/nwp3/ppsearch_p3.php`，并携带 `slat=<起点纬度>`、`slon=<起点经度>`、`elat=<终点纬度>`、`elon=<终点经度>`、`t=<查询时间>`、`leg=2`、`m1=T`、`l=0`

#### Scenario: 查询时间使用香港时间
- **WHEN** 系统构造路线查询请求
- **THEN** `t` 参数 MUST 使用当前香港时间并格式化为 `yyyy-MM-dd HH:mm`

#### Scenario: 使用最小必要請求
- **WHEN** 系統發起 Citybus 路線查詢請求
- **THEN** 系統 SHALL 使用無顯式 header 的 GET 請求
- **AND** 系統 SHALL NOT 顯式設置 `Cookie`、`User-Agent`、`Referer`、`Sec-Fetch-*`、`sec-ch-ua*`、`Connection` 或 `Accept-Language`

#### Scenario: 路線查詢日誌不輸出完整 cURL
- **WHEN** debug build 記錄 Citybus 路線查詢資訊
- **THEN** 日誌 SHALL 只包含脫敏摘要
- **AND** 日誌 SHALL NOT 包含完整 cURL、完整 URL、完整 query string、headers、cookies、`slat=`、`slon=`、`elat=` 或 `elon=`

### Requirement: 解析 Citybus 路线查询 HTML

系统 SHALL 从 Citybus 返回 HTML 的 `routelist2` 区域解析可选巴士路线列表。

#### Scenario: 定位路线列表容器
- **WHEN** Citybus 路线查询返回 HTML
- **THEN** 系统 SHALL 定位 `id` 去除首尾空格后等于 `routelist2` 的元素作为路线列表容器

#### Scenario: 解析多段巴士路线
- **WHEN** `routelist2` 中某个候选路线 table 表示多段巴士路线
- **THEN** 系统 SHALL 提取每段巴士路线号并用 `→` 连接为展示路线名称

#### Scenario: 累计路线总价格
- **WHEN** 候选路线包含一段或多段巴士价格
- **THEN** 系统 SHALL 将每段价格累计为该候选路线的总价格，单位为 HKD

#### Scenario: 解析预计路线总耗时
- **WHEN** 候选路线包含 `預計N分鐘` 或等价总耗时信息
- **THEN** 系统 SHALL 将 `N` 解析为预计路线总耗时分钟数

#### Scenario: 预计汽车到站时间阶段性取值
- **WHEN** 系统成功解析候选路线的预计路线总耗时
- **THEN** 系统 SHALL 将预计汽车到站时间分钟数设置为预计路线总耗时分钟数

### Requirement: 处理路线查询结果状态

系统 SHALL 区分路线查询成功、无结果和失败状态。

#### Scenario: 路线查询成功且有结果
- **WHEN** Citybus 返回可解析的候选路线
- **THEN** 系统 SHALL 返回所有有效候选路线结果

#### Scenario: 路线查询成功但无可用路线
- **WHEN** Citybus 返回的 `routelist2` 存在但没有任何有效候选路线
- **THEN** 系统 SHALL 返回空路线结果列表

#### Scenario: 路线查询失败
- **WHEN** 路线查询网络请求失败、HTTP 状态码非 2xx、HTML 缺少 `routelist2` 或 HTML 格式无法解析
- **THEN** 系统 SHALL 将其视为路线查询失败
