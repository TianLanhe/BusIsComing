## Why

目前路線卡片只能展示路線、價格、耗時、候車與步行距離，無法讓用戶在列表中快速確認實際上車站與下車站。底部詳情彈層可以提供完整站點路徑，但若為每張卡片提前請求完整詳情會放大網絡請求量；因此需要用更輕量、可緩存的站點預覽補足卡片掃讀能力。

## What Changes

- 新增路線卡片站點預覽：在路線文字與底部信息區之間，以單行展示 `上車 A站  →  下車 B站`。
- 從 `showroutep2p(...)` 的 `rawInfo` 解析完整 P2P bus legs，用第一段 `boardingSeq` 推導上車站，用最後一段 `alightingSeq` 推導下車站。
- 使用 DATA.GOV.HK `route-stop` API 推導 stop id，再使用 `stop` API 查詢站點名稱；此能力只服務卡片預覽，不替代底部詳情彈層的 Citybus HTML 詳情來源。
- 新增進程內緩存與請求去重：`route-stop`、`stop name` 和 `preview` 均緩存 1 天；同一次查詢先聚合唯一請求，限制並發，避免按卡片數線性放大請求。
- 路線列表先展示，站點預覽在後台漸進補全；失敗、空響應或解析失敗不緩存，也不影響卡片主信息與排序。
- 調整路線結果去重規則：當卡片開始展示站點預覽後，`rawInfo` 或首末站差異 SHALL 保留為不同路線，避免合併掉用戶可見差異。

## Capabilities

### New Capabilities

- `route-card-stop-preview`: 路線卡片站點預覽、完整 P2P legs 解析、DATA.GOV.HK 站點推導與站名查詢、緩存、請求去重、漸進補全和失敗降級。

### Modified Capabilities

無。

## Impact

- 影響路線結果模型：需要保存完整 P2P plan 或可派生完整 plan 的 `rawInfo`、`lang` 等預覽元數據。
- 影響 `CitybusRouteParser`：需要從每條候選路線的 `showroutep2p(...)` 解析所有 bus legs，而不只解析首程 ETA。
- 影響 Citybus 公開 API 輔助組件：需要將 `route-stop` 查詢能力抽成可被 ETA 與卡片預覽共用的 resolver，並新增 `stop` 站名查詢。
- 影響 `CitybusBusRouteRepository`：需要在聚合去重後啟動站點預覽補全、聚合唯一請求、限制並發、忽略舊查詢回調，並將結果回填到對應卡片。
- 影響主頁 UI：`item_bus_route.xml` 與 `BusRouteAdapter` 需要支持單行站點預覽，預覽不可用時隱藏該行。
- 需要新增單元測試覆蓋完整 P2P legs 解析、首末站推導、route-stop/stop 緩存、請求去重、失敗不緩存、舊查詢忽略、去重規則和卡片文案綁定。
