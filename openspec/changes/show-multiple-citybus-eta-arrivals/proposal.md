## Why

目前主頁路線卡片只展示首程最近一班車的候車時間，但 Citybus ETA API 一次響應可能包含第 1、2、3 班到站資料。用戶在決定是否等待時，除了「下一班多久到」之外，也需要知道錯過下一班後是否會等待很久。

本變更將多班 ETA 作為輕量輔助資訊展示：卡片保持可掃讀，只補充「下一班」摘要；完整班次放入點擊候車區後出現的底部面板。

## What Changes

- 將首程 ETA 解析結果從單一候車分鐘數擴展為最多 3 筆到站資料。
- 卡片右側候車區繼續突出第 1 班；若存在第 2 班，補充展示 `下一班 X 分鐘 ›`。
- 點擊有 2 班及以上 ETA 的候車區，打開首程 ETA 底部面板。
- 卡片其他區域仍打開原有路線詳情，不改變路線詳情入口。
- 底部 ETA 面板展示首程路線、第 1-3 班、候車分鐘數、具體到達時刻、更新時間和非空 `rmk_tc`。
- 多程路線只展示首程 ETA；面板標題使用 `首程 <路線> 候車時間` 避免誤解為整條轉乘路線。
- 候車排序仍只使用第 1 班候車時間，不因第 2/3 班改變排序語義。
- 不新增 ETA 網絡請求，只保留現有 ETA API 響應中的多條記錄。
- 不新增自動定時刷新；重新查詢路線才刷新 ETA，若面板打開期間後台 ETA 更新到達，面板可同步更新。

## Capabilities

### New Capabilities
- `citybus-eta-arrivals-sheet`: 定義首程多班 ETA 底部面板的打開條件、內容、狀態和更新行為。

### Modified Capabilities
- `citybus-first-leg-eta`: 首程 ETA 由單一最近班次擴展為最多 3 筆匹配班次，同時保持 stopId、strict/fallback 匹配、排序和並發語義。
- `bus-route-result-table`: 結果卡片的候車展示新增第 2 班摘要與候車區點擊入口。
- `route-query-results-layout`: 結果卡片在窄屏和字體縮放下需容納候車主行、下一班輔助行和可點擊狀態而不重疊。

## Impact

- 影響資料模型：`WaitTimeState.Available` 需要能承載多筆 ETA arrival，而不是只保存一個分鐘數。
- 影響 ETA 服務：解析 `eta_seq`、`eta`、`generated_timestamp` 或 `data_timestamp`、`dest_tc` 和非空 `rmk_tc`。
- 影響 Repository 回調：後台 ETA 補全仍按 routeId 更新，但更新內容包含多班 ETA。
- 影響 UI：路線卡片候車區需要分離點擊事件；新增一個輕量 ETA 底部面板。
- 影響排序：候車排序比較值仍取第 1 班候車分鐘數。
- 影響測試：需要覆蓋多班 ETA 解析、卡片文案、面板內容、只有一班時不可點、無 ETA/查詢中狀態和排序不變。
