## Why

目前主頁常用路線只按本地使用統計選中預設路線。用戶在香港通勤場景下，出門前更常需要從多條常用路線中快速選出「距離目前位置起點最近」的一條，避免手動在快捷卡或完整列表中反覆判斷。

本變更希望在不改變常用排序、查詢流程和本機路線資料結構的前提下，讓主頁打開時可依目前 GPS 位置自動選中最近起點，並用輕量標籤告知用戶這條路線由定位選出。

## What Changes

- 主頁在已保存常用路線數量至少 2 條時，每次 `MainActivity` 建立後自動嘗試一次前台定位。
- 新增 Google Play Services Location 依賴，使用 `FusedLocationProviderClient` 取得一次目前位置，不引入 `LocationManager` fallback。
- 若尚未授權前台定位，首次符合條件時直接請求 `ACCESS_FINE_LOCATION` 和 `ACCESS_COARSE_LOCATION`；用戶拒絕後持久化記錄，後續不再自動彈權限請求。
- 以目前位置到每條常用路線起點座標的直線距離選出最近起點；接受粗略位置，但需通過精度與明顯領先規則。
- 自動選中的路線沿用現有快捷卡展示規則；若不在原始 Top 3，臨時提升為首頁第一張快捷卡，不改完整列表排序和使用統計。
- 自動選中的快捷卡顯示 `附近` 標籤；用戶手動選擇任意路線後標籤消失。
- 定位失敗、權限拒絕、精度不足或 Google Play Services Location 不可用時，保持現有常用排序預設路線，按限頻 Toast 說明原因。
- 不自動發起路線查詢、不新增手動重新定位入口、不修改 Citybus 查詢參數、不改資料庫 schema。

## Capabilities

### New Capabilities

無。

### Modified Capabilities

- `main-route-selection`: 主頁常用路線區塊新增依目前位置自動選中最近起點、`附近` 標籤、定位權限 fallback 與失敗提示行為。

## Impact

- 影響模組：
  - `app/src/main/AndroidManifest.xml`：新增前台定位權限宣告。
  - `gradle/libs.versions.toml`、`app/build.gradle.kts`：新增 Google Play Services Location 依賴。
  - `app/src/main/java/com/example/busiscoming/ui/main/MainActivity.kt`：接入定位權限、一次性定位、異步結果保護、`附近` UI 狀態和 Toast 限頻。
  - `app/src/main/java/com/example/busiscoming/ui/main/RouteShortcutSelector.kt` 或新增鄰近路線策略類：保留現有 Top 3 臨時提升語義，補充依距離選中策略。
  - `app/src/test/java/com/example/busiscoming/`：新增或更新 JVM 單元測試覆蓋選擇策略、精度閾值、tie-breaker、手動選擇後丟棄定位結果與限頻狀態。
- 既有規格影響：
  - 修改 `main-route-selection` 規格；不修改 `route-place-storage-and-query` 的資料持久化要求。
- 外部資料與接口：
  - 不改 Citybus mobile、DATA.GOV.HK ETA 或任何巴士查詢 HTTP 參數。
  - 使用 Android／Google Play Services 定位能力取得 `Location(latitude, longitude, accuracy)`。
- 相容性與隱私：
  - 最低支援仍為 minSdk 25。
  - 用戶拒絕定位後不反覆自動彈權限；若之後在系統設定重新授權，下次主頁建立時恢復自動定位。
  - 若 Google Play Services Location 不可用，保持常用排序 fallback。
- 驗證：
  - 優先補充純邏輯 JVM 單元測試。
  - 實作完成後運行 `./gradlew build`。
  - 有可用模擬器或實機時補充手動驗證定位授權、拒絕、超時、`附近` 標籤、手動選擇清除標籤和管理頁返回不重定位。
