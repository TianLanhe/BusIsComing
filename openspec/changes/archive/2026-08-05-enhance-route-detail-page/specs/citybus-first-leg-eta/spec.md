## ADDED Requirements

### Requirement: 全屏路線詳情區分首程即時 ETA 與 Citybus 預計時刻
系統 SHALL 在全屏路線詳情頁展示首程候車狀態，並以來源標籤、文字及視覺層級將 DATA.GOV.HK 即時 ETA 與 Citybus 方案預計時間分開。

#### Scenario: 進入詳情時已有可用首程 ETA
- **WHEN** 用戶從已有 `WaitTimeState.Available` 的路線卡片進入全屏詳情頁
- **THEN** 頁面 SHALL 立即在首個乘車段展示 `即時 · 還有 N 分鐘` 或目前語言等效文案
- **AND** 即時 ETA SHALL 使用品牌強調色
- **AND** 同一上車站有 Citybus 方案時間時頁面 SHALL 另行展示中性的 `預計 HH:mm`

#### Scenario: 詳情頁刷新首程 ETA
- **WHEN** 全屏詳情頁具有完整 `FirstLegEtaQuery` 且頁面仍在有效生命週期
- **THEN** 系統 SHALL 使用既有首程 ETA 查詢與匹配規則在背景刷新候車狀態
- **AND** 新結果 SHALL 只更新首個乘車段的即時 ETA 狀態
- **AND** 過期 generation 或舊語言結果 SHALL 被取消或忽略

#### Scenario: 首程暫無車輛
- **WHEN** 首程 ETA 查詢成功但沒有匹配班次並產生 `WaitTimeState.NoArrivals`
- **THEN** 詳情頁 SHALL 顯示目前語言的暫無車輛狀態
- **AND** 系統 SHALL NOT 將該狀態顯示為技術故障

#### Scenario: 首程 ETA 技術故障
- **WHEN** 首程 ETA 為帶結構化原因的 `WaitTimeState.Unavailable`
- **THEN** 詳情頁 SHALL 顯示目前語言的候車暫不可用狀態
- **AND** 系統 SHALL NOT 將 Citybus 預計上車時間替代或標示為即時 ETA

#### Scenario: 後續乘車段沒有即時 ETA
- **WHEN** 路線詳情包含第二段或其後乘車段
- **THEN** 頁面 SHALL 只展示這些分段可用的 Citybus 預計上車時間
- **AND** 系統 SHALL NOT 根據首程 ETA、總耗時或計劃時間推算後續即時候車分鐘
