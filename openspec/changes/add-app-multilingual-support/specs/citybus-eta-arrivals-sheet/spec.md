## MODIFIED Requirements

### Requirement: ETA 班次面板內容
系統 SHALL 在首程 ETA 班次底部面板中以目前 App 語言展示與該路線首程相關的班次、方向、更新時間和備註資訊。

#### Scenario: 展示面板標題和方向
- **WHEN** 系統打開首程 ETA 班次底部面板
- **THEN** 面板標題 SHALL 以目前 App 語言表達首程路線候車時間
- **AND** 面板副標題 SHALL 優先使用目前語言選中的上車站與 ETA 目的地表達行車方向
- **AND** 若 ETA 沒有任何可用目的地欄位，面板副標題 SHALL 使用卡片站點預覽中的下車站原文作為方向

#### Scenario: 展示最多三班 ETA
- **WHEN** 首程 ETA 響應包含 1 到 3 筆可展示班次
- **THEN** 面板 SHALL 按班次順序展示這些班次
- **AND** 每個班次的班序、候車分鐘及具體到達時刻文案 SHALL 使用目前 App 語言

#### Scenario: ETA 超過三班時限制展示
- **WHEN** 首程 ETA 響應包含超過 3 筆可展示班次
- **THEN** 面板 SHALL 只展示排序後的前 3 筆班次

#### Scenario: 即將到站文案
- **WHEN** 某筆 ETA 的候車分鐘數為 0
- **THEN** 面板 SHALL 使用目前 App 語言顯示即將到站語義
- **AND** 系統 SHALL NOT 顯示本地化後的 `0 分鐘` 等價文字

#### Scenario: 展示非空備註
- **WHEN** 某筆 ETA 具有按目前語言及官方 fallback 選出的非空備註
- **THEN** 面板 SHALL 在該班次下方以次級文字展示備註原文
- **AND** 卡片 SHALL NOT 因該備註額外增加文字

#### Scenario: 展示更新時間
- **WHEN** 面板展示 ETA 班次
- **THEN** 面板 SHALL 以目前 App 語言展示更新時間標籤及 `HH:mm`
- **AND** 更新時間 SHALL 優先使用 ETA response 的 `generated_timestamp`
- **AND** 若 `generated_timestamp` 缺失，系統 SHALL 使用 ETA record 的 `data_timestamp`

#### Scenario: 英文或大字體內容較長
- **WHEN** 方向、備註或更新時間在目前寬度無法單行完整展示
- **THEN** 面板 SHALL 允許換行、增加行高或垂直滾動
- **AND** 系統 SHALL NOT 以固定寬度裁去核心方向或備註語義
