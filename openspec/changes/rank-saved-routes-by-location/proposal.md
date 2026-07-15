## Why

主頁的常用路線目前僅依使用統計排序；即使路線起點相距很遠，用戶仍需手動判斷哪一條最接近目前位置。另一方面，同一條已選路線反覆點擊查詢會重複累加使用次數，令統計偏向短時間的重複操作，而不是實際切換後的使用行為。

本變更讓常用路線優先依目前位置與起點的距離排列，並將使用統計改為一次首頁會話內每條連續選中路線僅記錄一次，使首頁排序更貼近出門時的實際選擇。

## What Changes

- 主頁成功取得目前位置後，常用路線快捷卡與完整列表依起點直線距離由近至遠排序。
- 距離相同時依使用次數由多至少排序；使用次數仍相同時沿用最近使用時間、更新時間及路線 id 的穩定兜底順序。
- 定位未授權、關閉、失敗或超時時，主頁回退至既有使用統計排序，不新增額外定位請求或阻塞路線選擇。
- 低精度但成功取得的位置可用於排列；既有「附近」標籤和自動選中仍維持其精度判定。
- 同一 `MainActivity` 會話內，連續選中的已保存路線只在首次點擊 `查詢` 時記錄一次使用；重複查詢與下拉刷新均不重複計數。切換至另一條已保存路線後再切回，才可再次記錄。
- 螢幕旋轉不重置上述使用記錄資格；重新開啟 App 後可重新開始計數。

## Capabilities

### New Capabilities

無。

### Modified Capabilities

- `main-route-selection`: 常用路線快捷卡與完整列表改為按目前位置距離排序，並定義定位結果、低精度、失敗降級與返回管理頁後的重排行為。
- `route-place-storage-and-query`: 已保存路線的查詢使用統計改為在一次連續選中會話中只記錄首次查詢，並調整使用統計在定位排序中的次要比較角色。

## Impact

- 影響 `app/src/main/java/com/golink/busiscoming/ui/main/MainActivity.kt` 的主頁路線載入、定位回調、快捷卡／完整列表呈現及會話生命週期狀態。
- 影響 `data/location` 的既有 `GeoDistanceCalculator`、`CurrentLocationSnapshot` 與 `NearbyRouteSelectionPolicy` 使用邊界，以及 `data/repository/RouteConfigRepository.kt` 的讀取排序契約；不變更 SQLite schema、歷史使用統計或 Citybus／DATA.GOV.HK 請求。
- 影響 `RouteShortcutSelector`、主頁定位與使用統計的 JVM／instrumentation 測試，需覆蓋距離並列、定位失敗、低精度、管理頁返回、重複查詢、下拉刷新、切換路線、旋轉螢幕及重啟邊界。
- 主頁 UI 只改變路線排列與既有選中狀態的呈現；不展示距離、使用次數或排名，且需維持目前的無障礙名稱、觸控範圍和不同螢幕尺寸行為。
