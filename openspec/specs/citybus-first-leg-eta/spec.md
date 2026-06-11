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

系統 SHALL 使用首程 bus leg 與 P2P stop map 推導上車站點，呼叫 DATA.GOV.HK 城巴公開 ETA API，並從同一次 ETA 響應中取得最多 3 筆首程到站班次；當 ETA 響應中的 `seq` 與首程上車站序不一致但 `route`、`stop` 和 `dir` 已匹配時，系統 SHALL 使用保守降級策略避免錯誤丟棄有效 ETA。

#### Scenario: 透過 P2P stop map 推導 stop_id
- **WHEN** 系統獲得首程 company、route variant、公開 route、boardingSeq、direction code、rawInfo 和 lang
- **THEN** 系統 SHALL 使用 `showstops2.php?r=<rawInfo>&l=<lang>` 的 P2P stop map 查找首程上車站
- **AND** 系統 SHALL 使用該 P2P stop map 記錄的 `stop_id` 作為 ETA 查詢站點
- **AND** 系統 SHALL NOT 使用公開 `route-stop/{company}/{route}/{direction}` 作為運行時 stop_id fallback

#### Scenario: 查詢 ETA 並優先使用嚴格匹配
- **WHEN** 系統獲得首程 company、stop_id、公開 route、原始 direction code 和 boardingSeq
- **THEN** 系統 SHALL 請求 `https://rt.data.gov.hk/v2/transport/citybus/eta/{company}/{stop_id}/{route}`
- **AND** 系統 SHALL 優先使用 `route`、`stop`、`dir` 和 `seq` 均匹配首程資訊且 `eta` 非空可解析的 ETA 記錄
- **AND** 系統 SHALL 從嚴格匹配記錄中保留最多 3 筆 ETA 班次

#### Scenario: 嚴格匹配缺失時降級匹配 ETA
- **WHEN** ETA 響應中沒有 `route`、`stop`、`dir` 和 `seq` 均匹配且 `eta` 非空可解析的記錄
- **AND** ETA 響應中存在 `route`、`stop` 和 `dir` 均匹配且 `eta` 非空可解析的記錄
- **THEN** 系統 SHALL 使用這些降級匹配記錄
- **AND** 系統 SHALL 從降級匹配記錄中保留最多 3 筆 ETA 班次

#### Scenario: 降級匹配仍要求路線站點和方向一致
- **WHEN** ETA 響應中的記錄 `seq` 與 boardingSeq 不一致
- **AND** 該記錄的 `route`、`stop` 或 `dir` 任一字段不匹配首程資訊
- **THEN** 系統 SHALL NOT 使用該記錄計算或展示首程候車班次

#### Scenario: ETA 班次排序
- **WHEN** 系統取得 1 筆或更多匹配 ETA 記錄
- **THEN** 系統 SHALL 優先按 `eta_seq` 升序排列班次
- **AND** 若 `eta_seq` 缺失或不可解析，系統 SHALL 使用 `eta` 時間升序作為兜底排序
- **AND** 系統 SHALL 最多保留排序後的前 3 筆班次

#### Scenario: 計算每班候車分鐘數
- **WHEN** 系統獲得匹配 ETA 的 ISO 時間
- **THEN** 系統 SHALL 用 ETA 時間減當前系統時間計算剩餘時間
- **AND** 剩餘時間小於等於 0 秒時該班候車分鐘數 SHALL 為 `0`
- **AND** 剩餘時間大於 0 秒時該班候車分鐘數 SHALL 按分鐘向上取整

#### Scenario: 保留 ETA 展示資料
- **WHEN** 系統解析匹配 ETA 記錄
- **THEN** 每筆可展示班次 SHALL 保留班次序號、候車分鐘數、ETA 時間、`HH:mm` 到達時刻和可用的 `dest_tc`
- **AND** 若 `rmk_tc` 非空，系統 SHALL 保留該備註供班次面板展示
- **AND** 系統 SHALL 保留 `generated_timestamp` 或 `data_timestamp` 供更新時間展示

#### Scenario: ETA 不可用
- **WHEN** P2P stop map 找不到上車站點、ETA 響應沒有嚴格匹配或降級匹配的非空可解析記錄、網絡請求失敗或響應解析失敗
- **THEN** 系統 SHALL 將該路線候車時間標記為不可用

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

系統 SHALL 支持按候車時間排序，排序比較值 SHALL 使用每條路線首程第 1 班 ETA 的候車分鐘數，並在 ETA 漸進更新期間保持排序結果可理解。

#### Scenario: 默認仍按總耗時升序
- **WHEN** 用戶完成一次路線查詢且系統展示初始結果列表
- **THEN** 系統 SHALL 默認按總耗時分鐘數升序展示
- **AND** 系統 SHALL NOT 因後台 ETA 更新改變默認總耗時排序

#### Scenario: 按候車時間升序排序
- **WHEN** 用戶選擇候車時間排序並切換到升序
- **THEN** 系統 SHALL 將已有可用第 1 班候車時間的路線按分鐘數升序排序
- **AND** 第 2 班或第 3 班候車時間 SHALL NOT 參與排序比較
- **AND** 候車時間為查詢中或暫無車輛的路線 SHALL 排在可用候車時間之後

#### Scenario: 按候車時間降序排序
- **WHEN** 用戶再次選擇候車時間排序並切換到降序
- **THEN** 系統 SHALL 將已有可用第 1 班候車時間的路線按分鐘數降序排序
- **AND** 第 2 班或第 3 班候車時間 SHALL NOT 參與排序比較
- **AND** 候車時間為查詢中或暫無車輛的路線 SHALL 仍排在可用候車時間之後

#### Scenario: 候車時間排序中後台更新
- **WHEN** 當前排序字段是候車時間且後台 ETA 更新某條路線的班次資料
- **THEN** 系統 SHALL 使用該路線最新第 1 班候車分鐘數重新排序並刷新列表
- **AND** 系統 SHALL NOT 因第 2 班或第 3 班變化單獨改變排序

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

