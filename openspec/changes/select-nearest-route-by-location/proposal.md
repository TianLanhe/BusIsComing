## Why

目前主頁常用路線只按本機使用統計選中預設路線，用戶仍需自行判斷哪條常用路線的起點最接近目前位置。同時，新增、編輯、複製路線及臨時查詢的 Citybus 地點候選只顯示名稱；遇到同名或相近地點時，用戶無法直接比較候選與目前 GPS 位置的距離。

本變更希望建立一套共用、一次性的前台定位能力：主頁可依可信位置選中最近起點；已具備定位權限時，地點候選可在不阻塞搜尋、不改變候選排序的前提下顯示直線距離。

## What Changes

- 主頁在已保存常用路線數量至少 2 條時，每次 `MainActivity` 建立後自動嘗試一次前台定位。
- 新增 Google Play Services Location 依賴，建立共用一次性定位元件，統一處理最近 30 秒位置快照、3 秒新定位超時、並發請求共用及生命週期過期結果。
- 若主頁最近路線流程尚未取得前台定位權限，首次符合條件時直接請求 `ACCESS_FINE_LOCATION` 和 `ACCESS_COARSE_LOCATION`；用戶拒絕後持久化記錄，後續不再自動彈權限請求。
- 新增、編輯、複製路線及臨時查詢不主動請求定位權限；只有 App 已取得粗略或精確定位權限時，才於頁面或彈層開啟後取得一次位置。
- 以共用直線距離計算器處理目前位置到常用路線起點或 Citybus 候選地點座標的距離，不調用步行路線服務，也不把位置或距離傳送給 Citybus。
- 主頁依目前位置選擇最近起點時保留精度與明顯領先規則；候選地點距離只要成功取得位置便顯示，不因位置精度不足而隱藏。
- 候選項保持 Citybus 原始相關性順序、約 52dp 單行卡片及每屏 4 至 6 個完整項目的目標；地點名稱位於左側，右側以定位圖示和次要文字顯示距離。
- 候選距離小於 1000 米時顯示四捨五入的整數米，例如 `368m`；四捨五入後達 1000 米時改為一位小數公里，例如 `1.0km`、`1.2km`。
- 候選先正常展示；位置稍後返回時原位補充距離，不關閉列表、不重排、不改變滾動位置。無權限、定位失敗或超時時靜默省略距離。
- 選定候選後，輸入框仍只顯示地點名稱，不持久化或顯示選取當下的距離。
- 自動選中的主頁快捷卡顯示 `附近` 標籤；用戶手動選擇任意路線後標籤消失。
- 不自動發起路線查詢、不新增手動重新定位入口、不修改 Citybus 查詢參數、不改資料庫 schema。

## Capabilities

### New Capabilities

無。

### Modified Capabilities

- `main-route-selection`: 主頁常用路線區塊新增依目前位置自動選中最近起點、`附近` 標籤、定位權限 fallback 與失敗提示行為。
- `route-place-selection`: 新增、編輯、複製路線及臨時查詢的候選地點，在已有定位權限且位置可用時顯示與目前位置的直線距離，並定義格式、排序、失敗降級及 UI 行為。

## Impact

- 影響模組：
  - `app/src/main/AndroidManifest.xml`：新增前台定位權限宣告。
  - `gradle/libs.versions.toml`、`app/build.gradle.kts`：新增 Google Play Services Location 依賴。
  - `app/src/main/java/com/example/busiscoming/ui/main/MainActivity.kt`：接入定位權限、一次性定位、異步結果保護、`附近` UI 狀態及 Toast 限頻。
  - `app/src/main/java/com/example/busiscoming/ui/edit/RouteEditActivity.kt`、`TemporaryRouteBottomSheet.kt`：在已有權限時取得一次位置，並把位置快照交給共用候選控制器。
  - `app/src/main/java/com/example/busiscoming/ui/common/PlaceInputController.kt` 及候選項佈局／資源：原位顯示定位圖示與格式化距離。
  - 新增或調整共用定位元件、位置快照模型、直線距離計算器及距離格式化器；現有通知監控距離估算與最近路線策略改用同一計算來源。
  - `app/src/test/java/com/example/busiscoming/`、`app/src/androidTest/`：覆蓋定位快照、快取、超時、精度規則、距離格式、候選原位更新、生命週期及視覺回歸。
- 既有規格影響：
  - 修改 `main-route-selection` 與 `route-place-selection`；不修改 `route-place-storage-and-query` 的資料持久化要求。
  - 本 change 的候選距離規格有意取代 `improve-route-refresh-feedback` 中「候選項只顯示地點名稱」的限制；實作順序必須先完成該 change 的候選卡修正，再加入距離欄位。
- 外部資料與接口：
  - 不改 Citybus mobile、DATA.GOV.HK ETA 或任何巴士查詢 HTTP 參數。
  - 使用 Android／Google Play Services 定位能力取得 `latitude`、`longitude`、`accuracy` 及位置時間。
  - Citybus 候選本身已包含經緯度，距離完全在本機計算。
- 相容性與隱私：
  - 最低支援仍為 minSdk 25。
  - 路線表單和臨時查詢不主動彈定位權限；無權限或定位失敗時維持完整候選選擇能力。
  - 位置快照與距離不持久化、不加入 Citybus 請求。
- 驗證：
  - 補充純邏輯 JVM 單元測試及定位元件測試。
  - 完成候選 UI instrumentation、模擬器定位與多尺寸視覺驗證。
  - 實作完成後運行 `./gradlew build` 及 OpenSpec 嚴格校驗。
