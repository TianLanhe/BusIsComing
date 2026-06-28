## Why

新安裝或刪空常用路線後，主頁目前只展示簡單空狀態和兩個操作按鈕，第一印象偏空、偏粗糙，無法讓用戶立即理解保存常用路線後的價值。這次改造要把無路線主頁從「缺資料提示」提升為友好、清晰、精緻的首次使用入口，同時保持已有路線後的高頻查詢效率。

## What Changes

- 將無保存路線且無臨時查詢結果的主頁改為首次引導版面：
  - 頂部只保留低權重 `乘車碼` 入口，不顯示沒有可管理對象的 `管理路線`。
  - `乘車碼` 在首次頁左上展示為緊湊 tonal 小按鈕；有保存路線時仍保持正常主頁雙主色按鈕。
  - 主文案固定為兩行：`把常走的路線放在這裡，` 與 `出門前一按即查。`。
  - 主體採用中上偏中位置，順序為主標題、`示例預覽`、示例卡、按鈕組。
  - 主按鈕為 `新增常用路線`，次按鈕為 `直接查詢一次`。
- 在首次頁展示一張固定示例結果卡，內容為 `118`、`柴灣 → 中環`、`等候 4 分鐘`、`下一班 11 分鐘 ›`、`HK$ 11.8 · 耗時 38 分鐘 · 步行 160 米`。
- 示例結果卡 SHALL 共用真實結果卡片的 layout、formatter 和 binding 邏輯；不得手寫另一套靜態 UI。示例卡外只顯示 `示例預覽` 標籤，不顯示 `不可點擊` 文案。
- 無保存路線時，若用戶完成 `直接查詢一次` 並產生臨時查詢上下文、查詢中狀態或結果，主頁 SHALL 隱藏首次頁並展示臨時查詢結果區；若只打開後關閉臨時查詢彈層而未發起查詢，仍回到首次頁。
- 新增輕量首次頁進入動效：標題、示例卡和按鈕依次淡入／輕微上移，僅在首次頁顯示時觸發，尊重系統動畫設定，不循環、不阻塞操作。
- 完整 Activity 根背景統一調整為克制的淡綠到近白漸變：
  - 主頁、路線管理頁、新增／編輯／複製路線頁採用同一根背景。
  - 結果區和列表背後透出同一背景；卡片、輸入框、狀態卡、chips、底部彈層仍保持白色或淺色實體表面。
- 路線管理頁與路線編輯頁做輕微配套優化，只限於標題／副文案層級、間距、表單承載面和列表留白；不改資料模型、字段、保存流程、搜尋流程或刪除確認。
- 有保存路線後的正常主頁頂部保持現狀：`乘車碼` 與 `管理路線` 仍使用現有位置和主要按鈕樣式。
- 不引入 Compose，不改 Citybus 查詢、排序、路線保存資料結構、乘車碼拉起規則或通知監控能力。

## Capabilities

### New Capabilities

<!-- No new standalone capability. This change refines existing homepage, result-card, and UI style capabilities. -->

### Modified Capabilities

- `main-route-selection`: 修改無保存路線主頁的首次引導、頂部入口顯示、臨時查詢結果切換和新增／直接查詢 CTA 行為。
- `route-query-results-layout`: 要求首次頁示例結果卡共用真實結果卡片 layout、formatter 和 binding，並保持示例不可觸發真實結果卡交互。
- `app-ui-style-system`: 修改完整頁面的根背景、動效基線和首次頁視覺原則，確保漸變背景與白色實體內容表面共存。
- `route-management-actions`: 補充路線管理頁與新增／編輯／複製路線頁在新背景下的輕微視覺配套要求。

## Impact

- 主要影響：
  - `app/src/main/res/layout/activity_main.xml`
  - `app/src/main/java/com/example/busiscoming/ui/main/MainActivity.kt`
  - `app/src/main/java/com/example/busiscoming/ui/main/BusRouteAdapter.kt`
  - `app/src/main/java/com/example/busiscoming/ui/main/RouteResultCardFormatter.kt`
  - `app/src/main/res/layout/item_bus_route.xml`
  - `app/src/main/res/layout/activity_route_manage.xml`
  - `app/src/main/res/layout/activity_route_edit.xml`
  - 相關 drawable、color、string、style 與 UI/contract 測試。
- 需要抽出可復用的結果卡片 binder 或等效封裝，讓 RecyclerView 真實結果卡和首次頁示例卡共用同一套綁定邏輯。
- 不改外部 Citybus、DATA.GOV.HK 或 Google API；不新增網路請求；不改 SQLite schema。
- 需要更新現有主頁布局測試、管理入口測試、結果卡片測試與可能的 screenshot/demo 測試期望。
- 驗證需要包含：
  - `./gradlew testDebugUnitTest`
  - `./gradlew build`
  - 模擬器視覺驗收：無保存路線首次頁、有保存路線正常主頁、臨時查詢結果、路線管理頁、新增路線頁。
