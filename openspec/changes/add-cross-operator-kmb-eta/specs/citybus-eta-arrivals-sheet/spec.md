## MODIFIED Requirements

### Requirement: 首程 ETA 班次底部面板
系統 SHALL 允許用戶從路線結果卡片的候車區打開首程 ETA 班次底部面板，以查看全部已合併的可用班次及其營運商。

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
- **WHEN** 路線結果卡片的候車狀態為查詢中、暫無車輛或無法取得
- **AND** 用戶點擊該卡片的候車區
- **THEN** 系統 SHALL NOT 打開首程 ETA 班次底部面板

### Requirement: ETA 班次面板內容
系統 SHALL 在首程 ETA 班次底部面板中以目前 App 語言展示與該路線首程相關的全部班次、每班營運商、方向、保守更新時間和備註資訊。

#### Scenario: 展示面板標題和方向
- **WHEN** 系統打開首程 ETA 班次底部面板
- **THEN** 面板標題 SHALL 以目前 App 語言表達首程路線候車時間
- **AND** 面板副標題 SHALL 優先使用目前語言選中的上車站與 ETA 目的地表達行車方向
- **AND** 若 ETA 沒有任何可用目的地欄位，面板副標題 SHALL 使用卡片站點預覽中的下車站原文作為方向

#### Scenario: 展示全部已合併 ETA
- **WHEN** 首程 ETA 結果包含一筆或以上可展示班次
- **THEN** 面板 SHALL 按聚合結果順序展示全部班次
- **AND** 每個班次的班序、候車分鐘及具體到達時刻文案 SHALL 使用目前 App 語言
- **AND** 面板 SHALL NOT 只截取排序後的前三筆班次

#### Scenario: 展示城巴營運商膠囊
- **WHEN** 某筆 ETA 的 operator 為 CTB
- **THEN** 該列 SHALL 顯示文字為香港繁體／簡體 `城巴` 或英文 `CTB` 的膠囊
- **AND** 膠囊 SHALL 使用 `#ECCF00` 背景及 `#004891` 文字

#### Scenario: 展示九巴營運商膠囊
- **WHEN** 某筆 ETA 的 operator 為 KMB
- **THEN** 該列 SHALL 顯示文字為香港繁體／簡體 `九巴` 或英文 `KMB` 的膠囊
- **AND** 膠囊 SHALL 使用 `#E60012` 背景及 `#FFFFFF` 文字

#### Scenario: 展示龍運營運商膠囊
- **WHEN** 某筆 ETA 的 operator 為 LWB
- **THEN** 該列 SHALL 顯示文字為香港繁體 `龍運`、簡體 `龙运` 或英文 `LWB` 的膠囊
- **AND** 膠囊 SHALL 使用 `#F15622` 背景及 `#17211F` 文字

#### Scenario: 營運商不只以顏色區分
- **WHEN** 面板展示任一 ETA 班次
- **THEN** 營運商膠囊 SHALL 永遠包含可讀文字
- **AND** 該列無障礙描述 SHALL 包含班序、營運商、候車時間、到達時刻及可用備註

#### Scenario: 即將到站文案
- **WHEN** 某筆 ETA 的候車分鐘數為 0
- **THEN** 面板 SHALL 使用目前 App 語言顯示即將到站語義
- **AND** 系統 SHALL NOT 顯示本地化後的 `0 分鐘` 等價文字

#### Scenario: 展示非空備註
- **WHEN** 某筆 ETA 具有按目前語言及官方 fallback 選出的非空備註
- **THEN** 面板 SHALL 在該班次下方以次級文字展示備註原文
- **AND** 卡片 SHALL NOT 因該備註額外增加文字

#### Scenario: 展示多來源保守更新時間
- **WHEN** 面板展示來自一個或多個營運商的 ETA 班次
- **THEN** 面板 SHALL 以目前 App 語言展示更新時間標籤及 `HH:mm`
- **AND** 更新時間 SHALL 使用目前完整列表各來源有效 timestamp 中最舊的一個
- **AND** 每個來源 SHALL 優先使用 response 的 generated timestamp，缺失時使用該來源 ETA record 的 data timestamp

#### Scenario: 英文或大字體內容較長
- **WHEN** 方向、膠囊、備註或更新時間在目前寬度無法單行完整展示
- **THEN** 面板 SHALL 保持標題及方向區可見並讓班次列表垂直滾動
- **AND** 面板 SHALL 允許內容換行或增加行高
- **AND** 系統 SHALL NOT 以固定寬度裁去核心方向、營運商、ETA 或備註語義

### Requirement: ETA 班次面板更新行為
系統 SHALL 在不新增自動輪詢的前提下，讓已打開的 ETA 班次面板以原子 render 反映當前查詢結果的有效跨營運商 ETA 更新。

#### Scenario: 不自動定時刷新
- **WHEN** 用戶打開首程 ETA 班次底部面板
- **THEN** 系統 SHALL NOT 啟動新的 ETA 定時刷新或輪詢任務
- **AND** 面板 SHALL 使用當前查詢結果已取得的 ETA 資料

#### Scenario: 面板打開期間同步有效後台更新
- **WHEN** 首程 ETA 班次底部面板正在展示某條 route result
- **AND** 同一查詢 generation 的後台 ETA 更新返回該 route result 的新合併班次資料
- **THEN** 系統 SHALL 在一次 render 中更新全部班次、班序、營運商、分鐘及更新時間
- **AND** 系統 SHALL NOT 讓舊班次與新營運商標籤短暫錯配

#### Scenario: 忽略舊查詢更新
- **WHEN** 首程 ETA 班次底部面板正在展示某條 route result
- **AND** 不同 route result identity、舊查詢 generation、舊語言版本或舊資料 snapshot 的 ETA 更新較晚返回
- **THEN** 系統 SHALL 忽略該舊 ETA 更新
- **AND** 系統 SHALL NOT 用舊資料覆蓋面板內容

