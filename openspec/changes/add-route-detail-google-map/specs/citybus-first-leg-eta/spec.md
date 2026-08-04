## MODIFIED Requirements

### Requirement: 全屏路線詳情區分首程即時 ETA 與 Citybus 預計時刻
系統 SHALL 在全屏路線詳情的摘要與首個乘車段展示首程候車狀態，並 SHALL 以來源標籤、文字及視覺層級將 DATA.GOV.HK 即時 ETA 與 Citybus 方案預計時間分開；頁面位於前台時 SHALL 每 60 秒更新一次首程 ETA。

#### Scenario: 進入詳情時已有可用首程 ETA
- **WHEN** 用戶從已有 `WaitTimeState.Available` 的路線卡片進入全屏詳情頁
- **THEN** 頁面 SHALL 立即在摘要及首個乘車段展示 `即時 · 還有 N 分鐘` 或目前語言等效文案
- **AND** 即時 ETA SHALL 使用品牌強調色及可由輔助技術理解的即時來源語義
- **AND** 同一上車站有 Citybus 方案時間時頁面 SHALL 另行展示中性的 `預計 HH:mm`

#### Scenario: 進入詳情時首程 ETA 尚在查詢
- **WHEN** 路線具備完整 `FirstLegEtaQuery` 但尚未有可展示的 ETA 結果
- **THEN** 摘要與首個乘車段 SHALL 顯示局部載入狀態
- **AND** Google Map、路線摘要及其他詳情內容 SHALL 保持可操作

#### Scenario: 詳情頁位於前台
- **WHEN** 詳情頁具有完整 `FirstLegEtaQuery` 且 Activity 位於前台
- **THEN** 系統 SHALL 使用既有首程 ETA 查詢與匹配規則立即取得或刷新候車狀態
- **AND** 系統 SHALL 在頁面持續位於前台期間每 60 秒再次刷新
- **AND** 新結果 SHALL 只更新摘要及首個乘車段的即時 ETA 狀態
- **AND** ETA 更新 SHALL NOT 觸發整張地圖或整份時間線重建

#### Scenario: 詳情頁進入後台或退出
- **WHEN** 詳情頁進入後台、被關閉或被銷毀
- **THEN** 系統 SHALL 停止詳情頁的 60 秒 ETA 刷新排程
- **AND** 系統 SHALL NOT 為此頁面申請背景執行或啟動通知監控服務

#### Scenario: 詳情頁返回前台
- **WHEN** 詳情頁返回前台且最近一次成功 ETA 已超過 60 秒
- **THEN** 系統 SHALL 立即刷新首程 ETA
- **AND** 刷新完成前系統 MAY 保留上次成功值並標記其正在更新

#### Scenario: 首程暫無車輛
- **WHEN** 首程 ETA 查詢成功但沒有匹配班次並產生 `WaitTimeState.NoArrivals`
- **THEN** 摘要及首個乘車段 SHALL 顯示目前語言的暫無車輛狀態
- **AND** 系統 SHALL NOT 將該狀態顯示為技術故障

#### Scenario: 首程 ETA 技術故障
- **WHEN** 首程 ETA 為帶結構化原因的 `WaitTimeState.Unavailable`
- **THEN** 摘要及首個乘車段 SHALL 顯示目前語言的候車暫不可用狀態
- **AND** 地圖、路線摘要、Citybus 詳情與分段幾何 SHALL 保持可用
- **AND** 系統 SHALL NOT 將 Citybus 預計上車時間替代或標示為即時 ETA

#### Scenario: 後續乘車段沒有即時 ETA
- **WHEN** 路線詳情包含第二段或其後乘車段
- **THEN** 頁面 SHALL 只展示這些分段可用的 Citybus 預計上車時間
- **AND** 系統 SHALL NOT 根據首程 ETA、總耗時或計劃時間推算後續即時候車分鐘

#### Scenario: 舊 ETA 結果完成
- **WHEN** 舊 request generation、舊語言或已退出頁面的 ETA 請求稍後完成
- **THEN** 系統 SHALL 取消或忽略該結果
- **AND** 該結果 SHALL NOT 覆寫目前頁面的 ETA 狀態
