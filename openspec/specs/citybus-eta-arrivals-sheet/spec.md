# citybus-eta-arrivals-sheet Specification

## Purpose
TBD - created by archiving change show-multiple-citybus-eta-arrivals. Update Purpose after archive.
## Requirements
### Requirement: 首程 ETA 班次底部面板
系統 SHALL 允許用戶從路線結果卡片的候車區打開首程 ETA 班次底部面板，以查看最多 3 班車的到站資訊。

#### Scenario: 有兩班或以上 ETA 時打開面板
- **WHEN** 路線結果卡片的首程 ETA 包含 2 筆或更多可展示班次
- **AND** 用戶點擊該卡片的候車區
- **THEN** 系統 SHALL 打開首程 ETA 班次底部面板
- **AND** 系統 SHALL NOT 同時打開路線詳情底部彈層

#### Scenario: 只有一班 ETA 時不打開面板
- **WHEN** 路線結果卡片的首程 ETA 只包含 1 筆可展示班次
- **AND** 用戶點擊該卡片的候車區
- **THEN** 系統 SHALL NOT 打開首程 ETA 班次底部面板
- **AND** 候車區 SHALL NOT 展示表示可展開的箭頭

#### Scenario: 沒有可用 ETA 時不打開面板
- **WHEN** 路線結果卡片的候車狀態為查詢中或暫無車輛
- **AND** 用戶點擊該卡片的候車區
- **THEN** 系統 SHALL NOT 打開首程 ETA 班次底部面板

### Requirement: ETA 班次面板內容
系統 SHALL 在首程 ETA 班次底部面板中展示與該路線首程相關的班次、方向、更新時間和備註資訊。

#### Scenario: 展示面板標題和方向
- **WHEN** 系統打開首程 ETA 班次底部面板
- **THEN** 面板標題 SHALL 顯示為 `首程 <路線> 候車時間`
- **AND** 面板副標題 SHALL 優先顯示為 `<上車站> 往 <dest_tc>`
- **AND** 若 ETA 缺少 `dest_tc`，面板副標題 SHALL 使用卡片站點預覽中的下車站名作為方向

#### Scenario: 展示最多三班 ETA
- **WHEN** 首程 ETA 響應包含 1 到 3 筆可展示班次
- **THEN** 面板 SHALL 按班次順序展示這些班次
- **AND** 每個班次 SHALL 包含 `第N班`、候車分鐘數和具體到達時刻

#### Scenario: ETA 超過三班時限制展示
- **WHEN** 首程 ETA 響應包含超過 3 筆可展示班次
- **THEN** 面板 SHALL 只展示排序後的前 3 筆班次

#### Scenario: 即將到站文案
- **WHEN** 某筆 ETA 的候車分鐘數為 0
- **THEN** 面板中該班次的候車文案 SHALL 顯示為 `即將到站`
- **AND** 系統 SHALL NOT 顯示 `0 分鐘`

#### Scenario: 展示非空備註
- **WHEN** 某筆 ETA 包含非空 `rmk_tc`
- **THEN** 面板 SHALL 在該班次下方以次級文字展示該備註
- **AND** 卡片 SHALL NOT 因該備註額外增加文字

#### Scenario: 展示更新時間
- **WHEN** 面板展示 ETA 班次
- **THEN** 面板 SHALL 展示 `更新 HH:mm`
- **AND** 更新時間 SHALL 優先使用 ETA response 的 `generated_timestamp`
- **AND** 若 `generated_timestamp` 缺失，系統 SHALL 使用 ETA record 的 `data_timestamp`

### Requirement: ETA 班次面板更新行為
系統 SHALL 在不新增自動輪詢的前提下，讓已打開的 ETA 班次面板反映當前查詢結果的有效後台 ETA 更新。

#### Scenario: 不自動定時刷新
- **WHEN** 用戶打開首程 ETA 班次底部面板
- **THEN** 系統 SHALL NOT 啟動新的 ETA 定時刷新或輪詢任務
- **AND** 面板 SHALL 使用當前查詢結果已取得的 ETA 資料

#### Scenario: 面板打開期間同步有效後台更新
- **WHEN** 首程 ETA 班次底部面板正在展示某條 routeId
- **AND** 同一查詢 generation 的後台 ETA 更新返回該 routeId 的新班次資料
- **THEN** 系統 SHALL 更新面板中的班次與更新時間

#### Scenario: 忽略舊查詢更新
- **WHEN** 首程 ETA 班次底部面板正在展示某條 routeId
- **AND** 舊查詢 generation 的 ETA 更新晚於新查詢返回
- **THEN** 系統 SHALL 忽略該舊 ETA 更新
- **AND** 系統 SHALL NOT 用舊資料覆蓋面板內容

