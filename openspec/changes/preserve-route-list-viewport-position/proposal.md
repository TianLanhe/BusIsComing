## Why

目前路線結果重排時，RecyclerView 或顯式 stable-id 錨點會跟隨原卡片移動，使使用者切換排序或收到背景更新後離開原列表位置。使用者希望視窗固定在「第幾項及其露出偏移」，由新排序的卡片填入該位置。

## What Changes

- 常用與搜尋結果統一按第一可見列表項序號及相對內容頂部偏移保持視窗，適用於排序欄位／升降序、漸進 ETA／步行／站點預覽及同一查詢的手動／自動刷新。
- 卡片身份繼續用於差異更新與已開啟互動；視窗恢復不再追蹤原卡片身份，也不按總捲動像素或滑塊百分比恢復。
- 保留 AppBar 展開狀態及既有刷新回饋；結果減少或項目高度變化時僅作最小有效範圍修正；空結果不發出無效捲動。
- 視窗恢復服從最新查詢、列表提交及使用者捲動，過期 callback 不得拉回舊位置。
- 置頂規則、置頂操作主動展示置頂區、新查詢、詳情返回和配置重建各自既有語義不因本次重排修復而改變。

## Capabilities

### New Capabilities

無。

### Modified Capabilities

- `bus-route-results-sorting`: 新增所有結果重排使用序號與偏移的視窗契約，修正漸進步行排序的 stable-id 視窗規則。
- `route-query-results-layout`: 更新自動及手動刷新視窗規則，同時保留刷新回饋與依路線身份管理的已開啟互動。

## Impact

- 主要實作範圍：`MainActivity`、`SearchFragment`、`RouteListViewportController`、`RouteListViewportAnchor` 及共用列表提交協調；沿用 `BusRouteAdapter`／DiffUtil 的資料身份。
- 相容性：不修改排序比較器、置頂 token、行程儲存、查詢參數、刷新排程或外部資料格式；不新增 runtime 文案或權限。
- 驗證：序號／偏移邊界單元測試，以及真實 RecyclerView 的重排、差異動畫、連續提交、使用者滑動、部分露出、置頂分隔行、大小字體與生命週期測試。
- 此 change 的排序行為改動適用於所有裝置；另一個無 GMS 相容 change 的「正常 Google 環境不變」約束不撤銷本次獨立授權的排序改動。
