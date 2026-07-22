## 1. 基線與可驗收契約

- [x] 1.1 核對實作分支已包含完成的三模組導航、搜尋 destination、深淺色、多語言及 `rename-saved-routes-to-journeys` 術語結果；若缺少依賴，先補齊對應既有變更，不在本 change 重新實作全 App 術語遷移。
- [x] 1.2 盤點 `activity_main.xml`、`fragment_search.xml`、`fragment_frequent_routes.xml`、`MainActivity`、`SearchFragment`、`PlaceInputController`、排序資源及現有 UI contract／instrumentation 測試，記錄本 change 的實際修改邊界。
- [x] 1.3 先新增或更新可失敗的 UI contract 測試，覆蓋底部導航 active indicator／文字樣式、搜尋欄位候選歸屬、右側交換按鈕、共用排序 style、響應式結果操作及首次空狀態不含次搜尋按鈕。

## 2. 底部導航持續選中狀態

- [x] 2.1 建立底部導航選中／未選中的文字 appearance、狀態色、active indicator 和固定 `28dp` 圖示槽資源，讓未選中圖示以 inset 呈現 `24dp` 視覺尺寸。
- [x] 2.2 在 `BottomNavigationView` 套用持續選中資源，保留既有 menu id、destination 切換、selected item 恢復和點擊 ripple，並確認三個導航項等寬且切換不重新量度。
- [x] 2.3 補充 JVM／instrumentation 驗證，覆蓋三個 Tab 的選中／未選中狀態、Activity 重建、語言／主題切換及大字體下核心標籤不重疊。

## 3. 搜尋連體輸入器與欄位級候選

- [x] 3.1 重組 `fragment_search.xml`，將起點和終點放入左側連體輸入欄，將 `48dp` 交換圖示放入右側固定操作區，並讓起點／終點候選、helper、錯誤與 attribution 緊跟對應輸入框。
- [x] 3.2 為輸入欄位建立固定尾端工具槽：起點保留目前位置操作，地點搜尋時在同一槽顯示小型 loading，終點預留相同寬度；載入狀態不得另佔整行或壓縮文字。
- [x] 3.3 為 `PlaceInputController`／`PlaceCandidatePresentationPolicy` 增加可選最大可見項設定；搜尋頁傳入 3，新增／編輯／複製行程頁預設維持既有 3–6 項自適應策略。
- [x] 3.4 保持同一時間只展示目前聚焦欄位的候選；起點候選插在起點與終點之間、終點候選插在終點下方，清單寬度只對齊輸入欄且超出三項後內部滾動。
- [x] 3.5 讓欄位聚焦、文字修改、候選選中、空白點擊、第一次系統返回、無結果及失敗狀態沿用既有 debounce、generation 過期與恢復行為，並確認交換按鈕在候選開合期間保持固定可見。
- [x] 3.6 擴充候選 presentation policy 和輸入 controller 單元測試，分別驗證搜尋三項上限、IME 空間不足、行程表單 3–6 項策略、只顯示一側候選及候選關閉後布局恢復。

## 4. 起終點交換與 Google Attribution

- [x] 4.1 在 `SearchFragment` 建立起點／終點 Google attribution 欄位 metadata 與兩個對應 attribution view；Google 目前位置成功填入時只標記實際承載地址的欄位。
- [x] 4.2 調整交換流程，同步交換已選 Place、未確認文字和 attribution metadata，關閉兩側候選並清除不再適用的 loading、helper 和錯誤；不得發起新的地點或路線查詢。
- [x] 4.3 在手動編輯、清空或選擇 Citybus 候選時清除對應 attribution，並保持另一欄位狀態不變；不得把 attribution 寫入 `Place.name`、SQLite 或保存資料。
- [x] 4.4 將兩個 attribution metadata 寫入及恢復自 `onSaveInstanceState`，確保旋轉、語言、主題及系統重建後來源仍跟隨正確欄位。
- [x] 4.5 補充單元／instrumentation 測試，覆蓋 Google 地址填入起點、交換至終點、未確認文字交換、編輯後隱藏、解析失敗不顯示及 recreation 恢復。

## 5. 搜尋結果操作、排序與首次空狀態

- [x] 5.1 以常用頁現有外觀為基線建立共用 checkable sort button style 及狀態色，固定 `48dp` 最小高度、`14dp` 水平內距、`13sp` 文字和 `8dp` 間距。
- [x] 5.2 讓 `MainActivity` 與 `SearchFragment` 共用排序 style，只保留字段、`isChecked` 和升降序箭頭更新；確認 `SortField`、`SortDirection`、比較器、刷新及結果順序沒有改變。
- [x] 5.3 調整搜尋結果摘要：寬度小於 `600dp` 或 font scale 大於等於 `1.3` 時使用摘要在上、操作在下；寬度大於等於 `600dp` 且 font scale 小於 `1.3` 時允許同列，且不新增第三方 layout 依賴。
- [x] 5.4 將 `編輯` 保持次要文字操作，把「儲存為常用行程」改為 tonal 按鈕並使用 `wrap_content + minHeight 48dp`；沿用既有行程名稱輸入、重複校驗和保存起終點快照流程。
- [x] 5.5 調整首次空狀態，保留靜態 `FirstRunRoutePreview` 與禁用 actions，把標籤改為「路線結果預覽」，只保留「新增常用行程」並移除頁內搜尋按鈕、view 綁定和 listener；保留底部搜尋 Tab 及搜尋頁提交所需的 `search_routes` 字串。
- [x] 5.6 同步香港繁體、獨立審校簡體和自然英文 runtime 文案，更新受影響的 content description、UI contract、首次引導動畫與搜尋保存測試，不修改用戶自訂名稱或第三方原文。

## 6. 主題、無障礙與回歸驗證

- [x] 6.1 在 `values`／`values-night` 使用相同幾何和語意色層級，驗證底部 active indicator、輸入器、候選、attribution、tonal 保存按鈕和排序選中態在淺／深色均具足夠對比。
- [x] 6.2 更新 `docs/ui-style-guide.md` 中與底部導航選中狀態、搜尋連體輸入器、欄位級候選、結果操作及排序一致性直接相關的規則，保持行程／路線術語一致。
- [x] 6.3 運行受影響的 JVM 測試及 `./gradlew testDebugUnitTest`，確認候選 policy、交換／attribution 狀態、排序與首次空狀態 contract 通過。
- [x] 6.4 使用 `adb devices` 檢查設備；有可用模擬器／實機時運行相關 instrumentation 測試，驗證三個 Tab、搜尋輸入、候選、交換、保存與首次新增行程流程。
- [x] 6.5 在繁體／簡體／英文 × 淺／深色下，以 `360dp` 和一般寬屏驗證 font scale `1.0／1.3／2.0`，檢查核心文字、觸控區、TalkBack 焦點、候選三項上限、`Save as regular journey`、排序橫向滾動及所有狀態不重疊；無設備時記錄未完成項與剩餘風險。

## 7. 最終校驗與提交

- [x] 7.1 運行 `./gradlew build`，修正所有 Kotlin 編譯、unit test、lint 及 debug／release assemble 問題。
- [x] 7.2 逐項核對五份 delta spec 的 scenarios，確認沒有修改 Citybus／Google／DATA.GOV.HK endpoint、參數、解析、cache、SQLite、`.bicroutes`、查詢、ETA、刷新、監控或排序算法，並同步勾選所有已完成任務。
- [x] 7.3 檢查 `git status --short`、`git diff --check` 和提交範圍，確保不包含無關或其他任務改動，並依 `/opsx-apply` 專案規則自動建立簡潔英文 conventional commit。
