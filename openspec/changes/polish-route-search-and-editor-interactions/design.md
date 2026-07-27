## Context

目前新增／編輯／複製行程使用獨立 XML 表單，起點與終點的 `TextInputLayout` 以常駐 `helperText` 提示選擇候選，導致未發生任何狀態時亦佔用第二行。搜尋 destination 使用 `PlacePairEditorView`、兩個 `PlaceInputController`、`SearchFragment` 內的 `RouteQueryState` 與 `RouteResultControlsView`；候選高度被固定為 3 行，候選 RecyclerView 的 nested scroll 會先被帶有 `layout_scrollFlags="scroll"` 的 AppBar 消費。

搜尋查詢開始時，`SearchFragment` 雖會更新 `RouteQueryState`，但查詢按鈕可用性只依起終點選擇推導，裸 `ProgressBar` 與單行狀態文字亦未沿用常用頁的狀態卡。成功結果的保存按鈕放在完整輸入區下方，未形成清楚的「編輯 → 查詢 → 結果上下文」狀態轉換。

`MainActivity` 沒有明確指定 IME 視窗策略，因此系統可選擇 resize 主佈局，把底部導航推到輸入法上方。`PlaceInputController` 已使用 `WindowInsetsCompat.Type.ime()` 計算候選可用高度，可在主視窗不 resize 的前提下繼續避開 IME。

本 change 只涉及 UI 展示、手勢 ownership、WindowInsets 與查詢頁狀態協調。Citybus／DATA.GOV.HK／Google、repository、SQLite、ETA、排序及結果卡片資料流保持不變。

## Goals / Non-Goals

**Goals:**

- 讓行程編輯頁在沒有動態狀態時恢復緊湊單行地點欄位，同時保留所有可操作的動態提示。
- 讓搜尋頁在標準配置展示 5 至 6 個候選，並由候選列表完整擁有展開期間的縱向手勢。
- 讓 IME 覆蓋物理底部導航，而不是把導航推到輸入法上方。
- 以單一可測試的搜尋展示狀態推導查詢按鈕、狀態卡、輸入器、結果、「本次行程」與保存狀態。
- 讓搜尋初次查詢的 loading／空／失敗回饋與常用頁使用同一狀態卡視覺結構。
- 查詢成功後以緊湊「本次行程」欄取代完整輸入器，提供清楚的編輯、取消編輯、保存與已保存流程。
- 保持三語、大字體、窄屏、無障礙、生命週期與過期 callback 行為可驗收。

**Non-Goals:**

- 不修改外部接口、參數、header、解析、cache、route variant 或 stop id 對齊。
- 不修改 `RouteQueryState` 的常用頁 query owner 語義，不把常用與搜尋重構為同一完整查詢控制器。
- 不修改路線排序規則、ETA 補齊、結果卡片欄位或下拉刷新資料來源。
- 不修改 SQLite schema、行程保存格式或既有行程資料。
- 不回滾引入 helper 或搜尋流程的整個歷史 commit。

## Decisions

### 1. 精確移除常駐地點 helper，不回滾整體提交

新增／編輯／複製行程頁只移除起點與終點 XML 中的常駐 `helperText`。浮動 label／hint 繼續表達欄位用途；`PlaceInputController` 或 Activity 設定的 loading、無結果、失敗、定位失敗、校驗和 Google attribution 仍按欄位顯示。行程名稱的 helper 保持不變。

此選擇避免回滾同時包含本地化、主題、快捷方式與其他修復的大型 commit。替代方案「把常駐文案改到輸入框上邊框」仍會長期佔用視覺注意力，與恢復最初緊湊外觀的目標不符，因此不採用。

### 2. 搜尋候選上限改為 6，標準配置至少顯示 5 行

搜尋頁將 `PlaceInputController` 的 `maxVisibleRows` 由 3 改為 6，沿用既有候選行高和 IME 可視區計算。在 360dp／1080×2400、font scale 1.0 與常規 IME 的標準驗收配置，列表 SHALL 至少完整顯示 5 行；空間足夠時顯示 6 行；空間不足時只顯示可容納的完整行並在列表內滾動。

不採用「所有配置強制至少 5 行」，因為小屏或 font scale 2.0 下會遮擋輸入框或侵入 IME。

### 3. 候選展開期間凍結搜尋外層滾動

`SearchFragment` 沿用 `PlaceInputController.onCandidateVisibilityChanged`，在任一候選可見時：

- 暫停 `searchContent` 的 AppBar scroll flags；
- 停用 `SwipeRefreshLayout`；
- 阻止候選 RecyclerView 把 nested scroll 傳給外層 Coordinator／AppBar；
- 保持候選自身的觸控滾動和點擊。

候選到達頂部或底部後，剩餘手勢亦不交給外層頁面；只有選擇地點、點擊空白、按返回或其他既有關閉流程收起候選後，才恢復外層 scroll flags 與刷新 eligibility。新增／編輯／複製行程的 `NestedScrollView` 邊界傳遞策略保持不變。

只設定 `nestedScrollingEnabled=true` 的替代方案無法避免 AppBar 在 nested pre-scroll 階段先移動；把候選改成 overlay／bottom sheet 則會破壞現有欄位級內嵌語義，均不採用。

### 4. `MainActivity` 使用不 resize 的 IME 策略

只為承載三個頂層 destination 的 `MainActivity` 設定 `windowSoftInputMode="adjustNothing"` 或等效不 resize 策略。IME 顯示後覆蓋仍位於物理底部的導航；IME 收起後導航在原位置重新可見，不執行平移或補償動畫。

搜尋輸入與候選繼續依 `WindowInsetsCompat.Type.ime()` 計算可視區；IME 覆蓋期間，底部導航不可被觸控或無障礙焦點誤操作。`RouteEditActivity` 等次級頁保留既有 IME 行為。

替代方案「IME 顯示時把導航移到鍵盤上方」正是目前問題；「主動隱藏再重新建立導航」會引入額外佈局變化和 destination 狀態風險，因此不採用。

### 5. 新增搜尋展示狀態，保留既有 query generation

新增搜尋頁專用的純展示狀態，例如：

- `Editing`
- `Querying`
- `Results`
- `EditingResults`
- `DirtyEditing`
- `Saved`

狀態只描述 UI 模式、目前查詢快照與保存狀態；既有 `RouteQueryState` 繼續負責 query id、進行中狀態與 callback generation。`SearchFragment` 使用單一 `renderSearchUi()`（或等效入口）從兩者推導：

- 完整輸入器／「本次行程」欄可見性；
- 查詢按鈕文字、enabled 和防重入；
- 狀態卡、結果控制器、結果列表與刷新 eligibility；
- 編輯、取消編輯、保存和已保存操作。

查詢入口在更新狀態前再次檢查 `isQueryInProgress`，防止快速連點繞過 View enabled 狀態。輸入實際改變時增加 generation 並讓舊結果、ETA／站點補齊、摘要、刷新與保存資格同時失效。

不採用在 `SearchFragment` 追加更多獨立 boolean 的最小補丁，因為查詢、編輯、刷新、保存和生命週期組合容易互相矛盾；亦不抽取完整共用查詢 coordinator，以免擴大常用頁回歸範圍。

### 6. 抽取共用查詢狀態卡，但不共用完整查詢流程

把常用頁既有狀態卡視覺結構抽為 `ui/common` 下的共用 View 或等效可 include layout，提供 loading、空結果、失敗與隱藏狀態。常用頁保留目前查詢與刷新控制，只改為綁定共用狀態卡；搜尋頁移除裸 `ProgressBar` 與普通狀態 `TextView`。

搜尋初次查詢期間保留完整輸入器，按鈕置灰並顯示查詢中文字，結果區展示「正在查詢路線」狀態卡。失敗、空結果或取消後不折疊輸入器，並按目前兩端是否有效恢復查詢按鈕。

### 7. 成功結果折疊為搜尋頁專用「本次行程」欄

只有查詢成功且返回至少一條路線後才進入 `Results`，隱藏完整起終點編輯器與查詢按鈕，顯示搜尋頁專用「本次行程」欄：

- 顯示當次查詢的 `起點 → 終點` 快照；
- 提供「編輯」與「儲存為常用行程」；
- 排序、摘要、結果列表與下拉刷新沿用既有結果流程。

點擊編輯進入 `EditingResults`，展開原輸入器並暫時保留舊結果，同時顯示明確「取消編輯」。未修改時取消編輯可返回折疊模式；任一輸入文字、已選 Place 或交換結果實際改變後進入 `DirtyEditing`，立即隱藏舊結果和保存入口並使 generation 失效。

正常字體與足夠寬度下，路徑、編輯及保存同列；360dp、英文長文案或 font scale 1.3／2.0 下，路徑與操作自適應為兩行。核心地點不縮字，操作使用 `wrap_content` 與至少 `48dp` 觸控高度。

曾考慮 Snackbar、結果摘要按鈕、查詢按鈕同行、FAB 與常駐底部行動條。Snackbar 不可再次發現，同行方案被否決，FAB／底部條會遮擋結果；折疊行程欄同時釋放輸入區高度並保持語義清楚，因此採用。

### 8. 保存成功綁定目前查詢

「儲存為常用行程」繼續使用既有命名、空值／重名校驗、repository 和完成提示。只有資料庫成功後，狀態才改為填充書籤與「已儲存」，並停用重複點擊。保存失敗保留可操作入口與既有錯誤回饋。

`Saved` 只綁定目前起終點與 query generation。編輯但未修改可回到同一已保存結果；輸入改變並完成新查詢後恢復可保存狀態。同一組起終點若需要另一名稱，沿用行程管理的複製能力，不在搜尋頁提供重複保存捷徑。

### 9. 生命週期、本地化與無障礙

頂層 destination 切換後返回時，如果 Fragment 與有效結果仍存在，保留折疊／編輯／已保存狀態。進程重建未恢復結果時只恢復起終點輸入，不建立沒有結果的折疊狀態。

新增或修改的按鈕、狀態卡、內容描述與提示提供香港繁體、獨立簡體及自然英文。折疊時清除隱藏輸入器焦點並避免無障礙服務遍歷不可見控件；編輯、取消編輯、保存、已保存及查詢狀態均有可理解語義。

## Risks / Trade-offs

- [Risk] `adjustNothing` 令主視窗不再因 IME 自動縮小，候選或輸入可能被覆蓋 → 以既有 IME Insets 計算候選高度，並在 API 25／36、常見 IME、手勢／三鍵導航驗證。
- [Risk] 凍結 AppBar 與關閉 nested scroll propagation 可能影響候選關閉後的結果滾動 → 由單一候選可見性聚合函式保存／恢復 scroll flags，加入候選開關和列表位置 instrumentation。
- [Risk] 展示狀態與 `RouteQueryState` 重複成為真相來源 → 展示狀態不保存 query id 或進行中布林值，所有 callback 仍以 `RouteQueryState` generation 為準，renderer 只組合兩者。
- [Risk] 抽取狀態卡可能令常用頁產生視覺回歸 → 保留既有 ID、尺寸、文案與動畫行為，使用 contract test 和常用頁 loading／空／失敗回歸。
- [Risk] 「本次行程」欄在長地點和大字體下過高 → 以量度／font scale 切換雙行，不縮字；覆蓋三語、360dp 與 font scale 2.0。
- [Trade-off] 保存後阻止同一查詢再次保存，犧牲搜尋頁直接建立同起終點多名稱的能力 → 使用行程管理的複製流程，換取更清楚的防重複回饋。

## Migration Plan

1. 先以純邏輯與 contract 測試固定展示狀態、XML／Manifest、候選高度和三語契約。
2. 精確移除兩個常駐 helper，調整搜尋候選與外層 scroll ownership。
3. 加入主 Activity IME 策略與候選 Insets 驗證。
4. 抽取狀態卡並讓常用頁／搜尋頁接入。
5. 加入搜尋展示狀態與「本次行程」欄，接回既有保存流程。
6. 更新 UI 指南、驗收矩陣與 OpenSpec task，執行相關單測、instrumentation（有裝置時）及 `./gradlew build`。

若需回退，應按決策邊界逐項撤回：可先停用折疊狀態與共用狀態卡，再恢復搜尋候選上限或 IME 策略；不得回滾包含本地化及其他功能的大型歷史 commit。本 change 不涉及資料遷移。

## Open Questions

無。候選降級、邊界手勢、IME 覆蓋、折疊時機、取消編輯、保存後狀態及大字體重排均已在設計審查中確認。
