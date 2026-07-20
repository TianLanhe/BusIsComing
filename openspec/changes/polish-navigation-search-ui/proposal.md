## Why

三個頂層模組已建立，但底部導航在點擊完成後缺少清楚的持續選中狀態，搜尋表單的起終點、交換操作與候選回饋亦未形成一致的欄位歸屬；搜尋結果排序和首次空狀態又沿用不同的視覺優先級。現在需要在不改變查詢與資料流程的前提下收斂這些界面細節，讓用戶能辨認目前模組、順暢完成地點輸入，並清楚理解「建立常用行程」是首次使用與搜尋後保存的核心價值。

## What Changes

- 為「常用／搜尋／設定」底部導航加入持續顯示的選中膠囊、較大的選中圖示及較粗大的選中文字，同時固定項目量度，避免切換時產生布局跳動。
- 把搜尋頁起點與終點整理為連體路線輸入器，將交換圖示移至右側固定操作區；各欄位獨立承載候選、載入、helper、無結果、錯誤及 Google attribution。
- 讓起點候選直接插在起點下方、終點候選直接插在終點下方；搜尋頁最多顯示三項候選，超出後內部滾動，同一時間只展開一個候選清單。
- 交換起終點時同步交換已選地點、未完成文字與 Google attribution 歸屬，並在重建後恢復 attribution 到正確欄位。
- 讓搜尋結果的「儲存為常用行程」成為較明顯的延伸操作，並讓結果摘要與操作在緊湊寬度、大字體及長英文下採用不裁切的響應式排列。
- 讓搜尋結果排序控件與常用頁共用相同高度、內距、間距、描邊、選中填充和升降序箭頭樣式，但保留既有排序字段、方向及結果順序。
- 首次沒有常用行程時保留靜態路線結果預覽，把標籤明確為「路線結果預覽」，只保留「新增常用行程」主入口；一次性查詢繼續由底部「搜尋」Tab 承接。
- 所有新增或修改的 runtime 文案同步提供香港繁體、獨立審校簡體及自然英文；深淺色使用相同幾何與互動，只切換語意色。
- 本 change 不重做頂層資訊架構、不接入新的地圖互動、不修改 Citybus／Google／DATA.GOV.HK、SQLite、查詢、ETA、刷新、監控或排序算法，也不把輸入器抽成跨所有頁面的完整共用元件。

## Capabilities

### New Capabilities

無。

### Modified Capabilities

- `app-chrome-layout`: 為頂層底部導航增加可持續辨認、切換時不跳動且適配大字體的選中狀態要求。
- `app-ui-style-system`: 統一底部導航、搜尋輸入器、結果操作與排序控件的深淺色、字體、間距、觸控及狀態視覺規則。
- `main-route-selection`: 將首次空狀態收斂為路線結果預覽與單一「新增常用行程」主入口，移除頁內一次性查詢次按鈕並保留底部搜尋入口。
- `route-place-selection`: 明確搜尋頁連體起終點輸入器、欄位級候選／狀態歸屬、三項候選上限，以及交換與重建時的 Google attribution 行為。
- `route-query-results-layout`: 讓搜尋結果摘要與保存操作響應式排列，並使搜尋與常用頁排序控件採用同一套展示狀態。

## Impact

- **Android UI**：主要影響 `app/src/main/res/layout/activity_main.xml`、`fragment_search.xml`、`fragment_frequent_routes.xml`、導航 menu、共用 style／drawable／color selector、`values-night` 及三語 string resources。
- **UI 協調**：主要影響 `ui/main/MainActivity.kt`、`ui/main/SearchFragment.kt` 及 `ui/common/PlaceInputController.kt`；保留既有 Fragment、query owner、generation、debounce、callback 和 instance state 邊界。
- **規格基線**：本 change 採用已確認的「常用行程 -> 查詢 -> 多條路線」術語。若實作分支尚未合併 `rename-saved-routes-to-journeys` 的最新術語基線，應先補齊該基線；本 change 不重新執行全 App 術語遷移。
- **外部服務與資料**：不修改 `bsearch_p3.php`、`ppsearch_p3.php`、Google Geocoding v4、DATA.GOV.HK ETA、請求參數、解析、cache、SQLite、`.bicroutes` 或用戶資料，亦不新增依賴。
- **相容性與恢復**：現有查詢、保存、刷新、路線詳情、ETA 和通知監控流程保持相容；地點搜尋失敗、無候選、目前位置失敗、過期 callback、旋轉及 Fragment 重建須保持既有恢復能力。
- **驗證**：需要補充 JVM／instrumentation 或 UI contract 測試，並在繁體／簡體／英文、淺／深色、`360dp`、font scale `1.0／1.3／2.0` 下驗證導航選中態、候選位置、交換、attribution、結果操作、排序及首次空狀態；最終運行 `./gradlew build`。
