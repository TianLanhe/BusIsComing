## Why

目前主頁同時承載常用路線、臨時查詢、查詢結果與設定入口，讓高頻通勤與一次性探索互相干擾。將臨時查詢升級為獨立的「搜尋」destination，並把設定移至頂層導航，可讓「常用」維持快速通勤查詢，同時為後續地圖路線展示建立穩定畫面邊界。

## What Changes

- 新增固定的底部導航，提供「常用」「搜尋」「設定」三個頂層 destination；App 冷啟動預設進入「常用」。
- 將目前主頁的常用路線與結果流程遷移為「常用」內容，移除主頁的臨時查詢入口、臨時結果上下文條與右上角設定入口。
- 將臨時起終點查詢遷移至表單優先的「搜尋」頁，保留 Citybus 候選地點、目前位置、交換起終點、查詢、結果、排序、刷新、ETA、詳情、監控與存為常用流程。
- 將現有設定、支援與關於內容作為頂層「設定」頁，讓頂層設定頁不以返回箭頭呈現。
- 保留三個 destination 的查詢、列表與選中狀態；次級路線管理、編輯、支援與關於頁維持既有返回導航。
- **BREAKING** 臨時查詢不再從「常用」頁的底部彈層或完整常用路線列表啟動；其結果不再顯示於「常用」頁。
- 本 change 不接入 Google Maps SDK、地圖選點、站序地圖或路線詳情地圖；該工作由後續獨立 change 處理。

## Capabilities

### New Capabilities

- `top-level-module-navigation`: 三個頂層 destination 的底部導航、預設頁、狀態保留與次級頁返回規則。
- `route-search-destination`: 將一次性起終點查詢作為獨立搜尋頁，並承接既有查詢、保存、結果與降級行為。

### Modified Capabilities

- `main-route-selection`: 將主頁限定為常用路線查詢，移除臨時查詢入口、臨時結果上下文與相關空狀態分支。
- `route-place-selection`: 將臨時查詢的 Citybus 地點輸入、候選、校驗、交換與目前位置行為遷移到搜尋頁。
- `route-query-results-layout`: 將臨時查詢結果、排序與下拉刷新承載位置由主頁改為搜尋頁，並以搜尋摘要取代臨時上下文條。
- `app-ui-style-system`: 讓新增的頂層頁、搜尋表單與狀態回饋遵循既有通勤工具視覺基線，並移除臨時查詢必為底部彈層的假設。
- `app-chrome-layout`: 將主界面的內部頁面語義由單一主頁改為底部導航中的三個頂層 destination，保留次級頁的既有標題與返回約束。

## Impact

- 受影響 UI：`ui/main`、設定頁、臨時查詢彈層、路線結果 Adapter 與相關 XML 資源；實作預期將建立清楚的常用、搜尋及設定畫面邊界。
- 受影響行為：常用路線選擇、一次性查詢、保存為常用、排序、下拉刷新、ETA、路線詳情、通知監控、乘車碼、設定與支援入口。
- Citybus 與 DATA.GOV.HK 查詢端點、參數、解析、`showstops2` 對齊、ETA 回應與既有 fixture 不改變；遷移後仍須以現有回歸案例驗證相同起終點查詢結果。
- 需要維護查詢取消／generation 過期、Fragment 或 destination 切換後的遲到回應、目前位置權限降級、WindowInsets、48dp 觸控目標和字體縮放。
- 需要新增 JVM 與 instrumentation 覆蓋，並在模擬器驗證三個 destination 切換、搜尋流程、狀態保留與既有次級頁返回行為；最終執行 `./gradlew build`。
