## Context

BusIsComing 已以 `BottomNavigationView`、`FragmentContainerView` 和三個頂層 Fragment 提供「常用／搜尋／設定」模組。`SearchFragment` 使用兩個 `PlaceInputController` 管理起終點文字、debounce、候選、載入與過期搜尋；`MainActivity` 和 `SearchFragment` 各自維護結果排序展示。首次空狀態已沿用真實路線結果卡的 layout、formatter 和 binder，但目前仍包含頁內搜尋次按鈕。

現有實作的主要問題位於 UI 組合與狀態展示：底部導航只有點擊漣漪，搜尋交換按鈕位於兩個輸入框之間，候選／loading／attribution 使用分散的獨立 view，搜尋排序只更新 `isChecked` 與箭頭而沒有套用常用頁的選中視覺。Google 目前位置 attribution 目前只由起點 view 承載，交換後無法跟隨地點移動，重建時亦沒有獨立保存來源歸屬。

本 change 只調整 Android UI、狀態協調和測試，不修改 Citybus、Google、DATA.GOV.HK、SQLite、查詢 repository、ETA、刷新或監控資料流。實作應以已確認的 `docs/superpowers/specs/2026-07-20-navigation-search-ui-polish-design.md` 為視覺基線；若當前分支尚未包含該文件，則以本 design 與 specs 作為 apply 的直接來源。

## Goals / Non-Goals

**Goals:**

- 使用持續且穩定的選中狀態讓用戶辨認目前頂層 destination。
- 以連體路線輸入器建立起點、終點、交換及欄位級候選／狀態的清楚關係。
- 讓 Google attribution 跟隨實際欄位值交換、清除和重建。
- 讓搜尋與常用頁排序控件共享視覺規則，同時保留既有排序行為。
- 讓搜尋結果保存操作和首次空狀態突出建立常用行程的產品價值。
- 在三語、深淺色、`360dp` 和大字體下保持完整、可點擊及無重疊。

**Non-Goals:**

- 不調整三個頂層 destination、返回規則或 query owner 邊界。
- 不新增 Google Maps SDK 地圖、地圖選點、站序地圖或結果地圖聯動。
- 不修改 HTTP endpoint、參數、header、解析、cache、SQLite 或 `.bicroutes`。
- 不改變查詢、刷新、排序、保存、ETA、詳情或監控業務邏輯。
- 不把搜尋輸入器抽成新增／編輯頁共用的完整新元件，不新增第三方 layout 依賴。
- 不重新執行全 App 的行程／路線術語遷移。

## Decisions

### 1. 沿用 Material 底部導航，以資源狀態建立持續選中層級

保留現有 `BottomNavigationView`、menu id 和 destination 切換。啟用 Material active indicator，使用 state-aware text appearance／color，以及固定 `28dp` 圖示槽配合未選中 drawable inset，讓選中圖示視覺為 `28dp`、未選中為 `24dp`。選中文字為 `14sp` 粗體，未選中為 `12sp` 常規字重。

這個方案不改導航事件、selected item id 或 Fragment 狀態保存。固定槽位讓項目在切換時不重新量度；大字體可令整個導航欄在該配置下採用足夠高度，但三個 Tab 的高度一致。

否決自建三個按鈕的導航列，因為它會重做 Material 的 selection、無障礙、menu 和狀態恢復。否決只調整 tint，因為用戶仍難以在未操作時辨認目前 destination。

### 2. 搜尋頁重組版面，但保留兩個 `PlaceInputController`

`fragment_search.xml` 將起點和終點放入左側垂直輸入欄，右側為固定 `48dp` 交換操作區。起點及終點基礎高度為 `56dp`；候選 RecyclerView、欄位訊息及 attribution 放在對應輸入框後方，因此起點候選插在兩個欄位之間，終點候選插在終點後方。清單寬度只對齊左側輸入欄，不延伸到交換按鈕下方。

兩個既有 `PlaceInputController` 繼續分別處理文字、debounce、repository 呼叫、generation、候選和錯誤；`SearchFragment` 只協調「另一個候選必須收起」和版面狀態。為 controller／presentation policy 增加可選的最大可見候選項參數：預設保持新增／編輯頁既有 3–6 項策略，搜尋頁傳入上限 3。IME 可用空間優先，空間不足時容許少於 3 項。

起點的目前位置操作和搜尋 loading 共用固定尾端工具槽：搜尋時隱藏目前位置操作並顯示小型進度，完成後恢復；終點預留相同寬度，避免兩個欄位文字寬度不同。helper 只在聚焦指引、無結果或錯誤時顯示。

否決建立新的跨頁輸入器 class，因為會同時影響已穩定的行程新增／編輯流程。否決把候選統一放在整個輸入器下方，因為它會失去與目前欄位的直接歸屬。否決 overlay dropdown，因為會遮擋結果、增加 IME 與無障礙焦點風險。

### 3. attribution 使用搜尋欄位 metadata，不改 `Place` 或 repository

`SearchFragment` 分別保存起點及終點是否需要顯示 Google attribution 的布林 metadata。使用 Google 目前位置成功填入起點時設為 true；手動編輯、清除或選擇 Citybus 候選時清除對應旗標。交換操作同時交換兩個 `Place`／文字和 metadata，並關閉兩側候選。兩個 attribution view 分別位於對應欄位下方。

兩個旗標寫入 `onSaveInstanceState`，與現有起終點 `Place`／文字一起恢復。attribution 只影響輸入上下文顯示，不加入 `Place.name`、SQLite、匯入匯出或查詢參數。

否決修改全域 `Place` 增加 provider 欄位，因為這會擴大至持久化、候選與查詢模型；否決交換時直接隱藏 attribution，因為 Google 解析地址已移到終點，仍需要正確標示來源。

### 4. 排序視覺由共用 checkable style 表達

建立常用與搜尋頁共同使用的 MaterialButton style、背景／文字／stroke ColorStateList。按鈕使用 `minHeight 48dp`、水平內距 `14dp`、`13sp` 文字及 `8dp` 相鄰間距；選中狀態由 `state_checked` 表達填充背景和高對比文字。兩個頁面仍只負責設置 `isChecked`、字段文字和升降序箭頭。

這會移除或收斂 `MainActivity` 與 `SearchFragment` 中互不一致的手動顏色／stroke 設置，但不改 `SortField`、`SortDirection`、排序比較器或刷新後排序恢復。

否決複製 `MainActivity.updateSortControls()` 到搜尋頁，因為之後仍可能漂移；否決抽取包含排序行為的新 controller，因為行為已由各 query owner 和 `RouteQueryState` 正確管理。

### 5. 結果摘要使用確定斷點，不引入自動換行依賴

搜尋結果摘要保留起終點快照、更新時間、編輯和儲存入口。寬度小於 `600dp`，或 font scale 大於等於 `1.3` 時，摘要在上、操作列在下；寬度大於等於 `600dp` 且 font scale 小於 `1.3` 時維持同列。可用資源 qualifier 或輕量 UI 配置判斷完成，不新增 Flexbox 等依賴。

「編輯」維持次要文字操作；「儲存為常用行程」使用 tonal 按鈕和 `wrap_content + minHeight 48dp`。這個操作保存起終點行程，不保存某一條查詢結果。

否決保留固定 `48dp` 高的橫向按鈕列，因為三語尤其 `Save as regular journey` 在大字體下會裁切。否決所有尺寸永久垂直排列，因為平板和寬屏有足夠空間保留緊湊摘要。

### 6. 首次空狀態只保留建立行程主操作

保留現有首次標題、`FirstRunRoutePreview`、真實結果卡 layout／formatter／binder 和禁用 actions，不發起網絡請求。把預覽標籤改為三語的「路線結果預覽」語義，只保留「新增常用行程」主按鈕並沿用 `RouteEditActivity`。移除頁內搜尋次按鈕、view 綁定及 listener；底部「搜尋」Tab 繼續提供一次性查詢，`search_routes` 字串仍供搜尋提交使用。

否決移除預覽卡，因為它能直接展示建立行程後的一按查詢價值；否決保留雙按鈕，因為它讓首次決策再次分叉並弱化核心主操作。

### 7. 深淺色、三語及無障礙使用同一幾何

所有尺寸、展開位置、觸控區和焦點順序在淺色與深色保持一致，只由 `values`／`values-night` 語意色切換表面、描邊、indicator 及文字對比。新增或修改文案同步提供香港繁體、獨立審校簡體及自然英文。圖示按鈕保留 content description，觸控區不小於 `48dp`；靜態預覽不暴露無效 click action。

否決為深色模式建立另一套布局，因為會增加狀態漂移；否決縮小英文或大字體，改以換行、縱向重排和穩定高度承載。

## Risks / Trade-offs

- **[Material active indicator 與現有 ripple 疊加後過度突出]** → 分別使用低對比持續 indicator 和短暫 ripple，在淺／深色實機截圖檢查兩者層級。
- **[起點候選插入後令終點下移，交換按鈕與終點不再垂直居中]** → 交換按鈕錨定輸入器頂部的固定操作區，候選開合不得重新量度該位置；以選中前後截圖驗證穩定性。
- **[三項搜尋候選上限意外影響行程編輯頁]** → 以參數提供搜尋頁專用上限，controller 預設維持既有 3–6 項策略，分別補測兩類頁面。
- **[attribution 旗標與實際欄位值不同步]** → 所有 set／edit／clear／candidate select／swap／restore 路徑集中更新 metadata，增加交換及 recreation 測試。
- **[共用排序 style 改變常用頁已穩定外觀]** → 以常用頁現有選中／未選中效果作為 style 基線，先建立 contract／screenshot 再讓搜尋頁共用。
- **[固定斷點在部分裝置產生過多留白]** → 以 `360dp`、一般手機、平板及 font scale `1.0／1.3／2.0` 驗證；完整顯示優先於最小高度。
- **[實作分支缺少行程術語基線]** → apply 前檢查 `rename-saved-routes-to-journeys` 的結果；若未合併，先補齊基線而不在本 change 重複全域術語遷移。

## Migration Plan

1. 確認實作分支已有三模組導航、搜尋 destination、深淺色、多語言及行程／路線術語基線。
2. 先建立共用導航與排序狀態資源及相關 contract 測試，再調整各頁 XML。
3. 重組搜尋輸入版面並加入搜尋專用候選上限，保持 repository 和 controller 既有搜尋流程。
4. 加入 attribution 欄位 metadata、交換、清除和 instance state 恢復。
5. 調整搜尋結果操作響應式排列與首次空狀態，補齊三語資源。
6. 執行相關 JVM／instrumentation 測試、`./gradlew build` 及可用的模擬器視覺驗收。

此 change 沒有資料遷移或分階段發布。回退時可回退 UI 資源、版面與 UI 狀態協調；SQLite、外部服務和用戶資料不受影響。

## Open Questions

無。選中尺寸、候選歸屬與上限、交換行為、結果斷點、首次空狀態及深淺色／三語驗收範圍均已確認。
