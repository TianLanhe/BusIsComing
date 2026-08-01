## Context

目前新增／編輯／複製行程使用獨立 XML 表單，起點與終點的 `TextInputLayout` 以常駐 `helperText` 提示選擇候選，導致未發生任何狀態時亦佔用第二行。搜尋 destination 使用 `PlacePairEditorView`、兩個 `PlaceInputController`、`SearchFragment` 內的 `RouteQueryCoordinator`、`RouteQueryState` 與 `RouteResultControlsView`；候選高度被固定為 3 行，候選 RecyclerView 的 nested scroll 會先被帶有 `layout_scrollFlags="scroll"` 的 AppBar 消費。

搜尋查詢開始時，`SearchFragment` 雖會更新 `RouteQueryState`，但查詢按鈕可用性只依起終點選擇推導，裸 `ProgressBar` 與單行狀態文字亦未沿用常用頁的狀態卡。成功結果的保存按鈕放在完整輸入區下方，未形成清楚的「編輯 → 查詢 → 結果上下文」狀態轉換。第一輪折疊實作又在 `360dp` 下把操作強制重排為整行等寬 tonal 按鈕，外加大型圓角卡片；`EditingResults` 同時顯示完整編輯器與行程／保存組件，並不等同已確認的「整體替換」。

常用與搜尋頁目前保留 AppBar 的 direct drag，因此即使沒有有效結果列表，用戶仍可按住頂部快捷行程或搜尋區上下拖動。需求邊界是只有結果列表可以驅動頁面級捲動；候選列表則在展開時繼續擁有更高優先級的獨佔手勢。

`MainActivity` 沒有明確指定 IME 視窗策略，因此系統可選擇 resize 主佈局，把底部導航推到輸入法上方。`PlaceInputController` 已使用 `WindowInsetsCompat.Type.ime()` 計算候選可用高度，可在主視窗不 resize 的前提下繼續避開 IME。

本 change 只涉及 UI 展示、手勢 ownership、WindowInsets 與查詢頁狀態協調。Citybus／DATA.GOV.HK／Google、repository、SQLite、ETA、排序及結果卡片資料流保持不變。

## Goals / Non-Goals

**Goals:**

- 讓行程編輯頁在沒有動態狀態時恢復緊湊單行地點欄位，同時保留所有可操作的動態提示。
- 讓搜尋頁在標準配置展示 5 至 6 個候選，並由候選列表完整擁有展開期間的縱向手勢。
- 讓 IME 覆蓋物理底部導航，而不是把導航推到輸入法上方。
- 以單一可測試的搜尋展示狀態推導查詢按鈕、狀態卡、輸入器、結果、「本次行程」與保存狀態，並分離正在編輯的輸入與上一次成功結果快照。
- 讓搜尋初次查詢的 loading／空／失敗回饋與常用頁使用同一狀態卡視覺結構。
- 查詢成功後以輕量「本次行程」欄取代完整輸入器；點擊鉛筆後再由完整輸入器整體替換行程欄，不提供取消編輯，重新成功查詢後才恢復保存。
- 以雙向高度變化及交叉淡化讓行程欄／編輯器切換保持空間連續，不令下方結果突然跳位。
- 只讓有效結果列表驅動常用與搜尋頁的 AppBar 捲動；頂部區域不可直接拖動，無結果時保持固定。
- 保持三語、大字體、窄屏、無障礙、生命週期與過期 callback 行為可驗收。

**Non-Goals:**

- 不修改外部接口、參數、header、解析、cache、route variant 或 stop id 對齊。
- 不修改每個頁面既有 `RouteQueryCoordinator` 的 query id／generation／callback 驗證邊界，亦不把常用與搜尋重構為同一完整查詢控制器。
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

### 3. 候選展開期間由 RecyclerView 擁有搜尋手勢

`SearchFragment` 沿用 `PlaceInputController.onCandidateVisibilityChanged`，在任一候選可見時：

- 停用 `SwipeRefreshLayout`；
- 保持 `searchContent` 的 AppBar scroll flags 與目前 offset 完全不變；
- 搜尋候選 RecyclerView 停用 nested scrolling，並在 `OnItemTouchListener` 的
  `ACTION_DOWN` 起向父層要求不攔截觸控、停止既有 nested scroll；
- 同一手勢的 `ACTION_MOVE` 持續保有父層不攔截狀態，`ACTION_UP`／`ACTION_CANCEL`
  才釋放；
- 保持候選自身的觸控滾動和點擊。

候選到達頂部或底部後，剩餘手勢亦不交給外層頁面；只有選擇地點、點擊空白、按返回或其他既有關閉流程收起候選後，才恢復刷新 eligibility。新增／編輯／複製行程的 `NestedScrollView` 邊界傳遞策略保持不變。

只設定 `nestedScrollingEnabled=true` 的替代方案無法避免 AppBar 在 nested pre-scroll 階段先移動；把 AppBar flags 設為 `0` 則會重算 total scroll range 並可能重置部分捲動 offset，故不採用。把候選改成 overlay／bottom sheet 會破壞現有欄位級內嵌語義，亦不採用。

### 4. `MainActivity` 使用不 resize 或等效的 IME 策略

只為承載三個頂層 destination 的 `MainActivity` 設定 `windowSoftInputMode="adjustNothing"` 或等效策略。Android 11 以上直接使用 IME Insets；Android 10 以下因 `adjustNothing` 無法可靠回報 IME 可視區，改用 `adjustResize` 取得舊系統可見視窗高度，並以相反位移把底部導航保持在原物理座標及鍵盤後方。IME 收起後導航恢復原量度、位置、destination 與可操作狀態。

搜尋輸入與候選在 Android 11 以上依 `WindowInsetsCompat.Type.ime()` 計算可視區，舊系統則依視窗可見區及頂層內容容器計算，只展示可完整容納的候選項。舊系統的全局佈局回呼不得在用戶已按返回、點擊空白或完成選擇後重新拉起候選；只有新候選結果或用戶再次點擊輸入框才可重新展示。IME 覆蓋期間，底部導航不可被觸控或無障礙焦點誤操作。`RouteEditActivity` 等次級頁保留既有 IME 行為。

替代方案「IME 顯示時把導航移到鍵盤上方」正是目前問題；「主動隱藏再重新建立導航」會引入額外佈局變化和 destination 狀態風險，因此不採用。

### 5. 新增搜尋展示狀態，保留既有 query generation

新增搜尋頁專用的純展示狀態，例如：

- `Editing`
- `Querying`
- `Results`
- `EditingRetainedResults`
- `Saved`

狀態只描述 UI 模式、正在編輯的起終點、上一次成功結果快照與保存狀態；既有 `RouteQueryCoordinator` 繼續負責 query id、generation 與 callback 有效性驗證，`RouteQueryState` 繼續負責結果、查詢進行中與刷新狀態。`SearchFragment` 只在 coordinator 驗證 callback 後更新兩個 state，並使用單一 `renderSearchUi()`（或等效入口）推導：

- 完整輸入器／「本次行程」欄可見性；
- 查詢按鈕文字、enabled 和防重入；
- 狀態卡、結果控制器、結果列表與刷新 eligibility；
- 編輯、保存和已保存操作；
- 舊結果是否仍可閱讀、排序、查看詳情或啟動監控，以及刷新是否可用。

查詢入口在更新狀態前再次檢查 `RouteQueryState.isQueryInProgress`，防止快速連點繞過 View enabled 狀態。點擊鉛筆即讓保存入口失效，但保留舊結果快照；輸入實際改變時仍保留舊結果作參考。只有提交新查詢時，才由 `RouteQueryCoordinator` 使舊 generation 失效並移除舊結果、摘要、ETA／站點補齊及監控上下文。編輯期間禁用下拉刷新，避免以新輸入誤刷新舊結果；舊結果卡的監控入口必須使用成功結果快照，而非正在編輯的輸入。

不採用在 `SearchFragment` 追加更多獨立 boolean 的最小補丁，因為查詢、編輯、刷新、保存和生命週期組合容易互相矛盾；亦不抽取完整共用查詢 coordinator，以免擴大常用頁回歸範圍。

### 6. 抽取共用查詢狀態卡，但不共用完整查詢流程

把常用頁既有狀態卡視覺結構抽為 `ui/common` 下的共用 View 或等效可 include layout，提供 loading、空結果、失敗與隱藏狀態。常用頁保留目前查詢與刷新控制，只改為綁定共用狀態卡；搜尋頁移除裸 `ProgressBar` 與普通狀態 `TextView`。

搜尋初次查詢期間保留完整輸入器，按鈕置灰並顯示查詢中文字，結果區展示「正在查詢路線」狀態卡。失敗、空結果或取消後不折疊輸入器，並按目前兩端是否有效恢復查詢按鈕。

### 7. 成功結果折疊為搜尋頁專用輕量「本次行程」欄

只有查詢成功且返回至少一條路線後才進入 `Results`，隱藏完整起終點編輯器與查詢按鈕，顯示搜尋頁專用「本次行程」欄：

- 顯示當次查詢的 `起點 → 終點` 快照；
- 外層使用扁平輕量 surface 與底部分隔，不使用大型圓角卡片；
- 正常字體下路徑保持單行並尾部省略；
- 編輯入口使用至少 `48dp` 的鉛筆圖示按鈕；
- 保存入口使用書籤圖示加「儲存／保存／Save」的緊湊描邊按鈕；
- 禁止把編輯、取消與保存排列成整行等寬 tonal 按鈕；
- 折疊結果狀態的排序、摘要、結果列表與下拉刷新沿用既有結果流程。

點擊鉛筆進入 `EditingRetainedResults`，以完整輸入器整體替換「本次行程」欄。此狀態不提供「取消編輯」，不自動聚焦任一欄位或彈出鍵盤，並立即移除保存／已保存入口及其資格；只有下一次成功非空查詢才再次建立保存資格。

編輯期間保留原結果、排序、詳情和監控能力；輸入文字、已選 Place、清除或交換實際改變後仍不清空舊結果。舊結果明確由上一次成功快照擁有，監控不得讀取正在編輯的新起點；下拉刷新在此狀態停用。用戶提交新查詢時才移除舊結果並顯示首次查詢狀態卡。新查詢成功後折疊並展示新結果；失敗或空結果保持完整編輯器。

正常字體下，即使在 `360dp` 或地點較長，亦優先以路徑尾部省略保持同列；font scale `1.3／2.0` 或操作實際無法完整容納時，路徑與操作可自適應為兩行。兩行模式的操作組仍靠尾端並使用 `wrap_content`，不得變成整行等寬按鈕。核心文字不縮字，操作保留至少 `48dp` 觸控目標。

曾考慮 Snackbar、結果摘要按鈕、查詢按鈕同行、FAB 與常駐底部行動條。Snackbar 不可再次發現，同行方案被否決，FAB／底部條會遮擋結果；折疊行程欄同時釋放輸入區高度並保持語義清楚，因此採用。

### 8. 保存成功綁定目前查詢

「儲存為常用行程」繼續使用既有命名、空值／重名校驗、repository 和完成提示。只有資料庫成功後，狀態才改為填充書籤與「已儲存」，並停用重複點擊。保存失敗保留可操作入口與既有錯誤回饋。

`Saved` 只綁定目前起終點與 query generation。點擊鉛筆後無論是否修改輸入，保存／已保存入口均消失；只有完成一次新的成功非空查詢後，才建立新的可保存狀態。同一組起終點若需要另一名稱，仍由既有命名／重複處理流程決定，不因舊結果保留而直接恢復保存入口。

### 9. 雙向行程欄切換動效

行程欄與完整編輯器使用同一容器內的雙向 Content Transform：

- 鉛筆點擊：行程欄淡出並收起，編輯器從同一頂部錨點淡入並展開；
- 新查詢成功：編輯器淡出並收起，行程欄從同一頂部錨點淡入；
- 高度與透明度約在 `240ms` 內協調完成，使用 Material 標準減速／強調 easing，讓下方結果平滑位移；
- 動畫進行時只有目標狀態可接收觸控與無障礙焦點，快速重複事件不得留下兩個可見組件或中間高度；
- 系統動畫比例為 `0`、View 尚未完成 layout、Fragment 已停止或重建時，直接落到最終狀態，不延遲資料與可見性真相。

不使用純交叉淡化，因為容器高度會瞬間跳變；不使用垂直共享軸滑入滑出，因為該方向感更像頁面導覽而非原位編輯。

### 10. 只有結果列表可驅動頁面級捲動

常用行程與搜尋頁的 `AppBarLayout` 保留結果列表 nested scroll 所需的 `scrollFlags`，但安裝拒絕 direct drag 的共用行為：

- 在快捷行程、查詢按鈕、完整搜尋編輯器或折疊「本次行程」欄內上下滑動，AppBar offset 保持不變；
- 只有有效結果 `RecyclerView` 發起 nested scroll 時，頂部查詢區才可捲出或恢復，共用結果控制器繼續吸頂；
- 初始、查詢中、空結果或沒有保留結果的失敗狀態不存在結果列表驅動源，因此頁面頂部固定；
- `EditingRetainedResults` 仍保留結果列表驅動能力，但直接拖動編輯器不移動頁面；
- 常用頁空狀態若因小屏或大字體需要內部捲動，可在自身容器內捲動，但不得把 nested scroll 傳給 AppBar；
- 搜尋候選展開時仍從 `ACTION_DOWN` 起獨佔垂直手勢，優先於結果列表與 AppBar，且到頂或到底不轉交。

採用 AppBar direct drag callback，而非動態清除 `scrollFlags`，避免結果出現／消失時重算 scroll range 與 offset。亦不把頂部移出 CoordinatorLayout，以免重做既有吸頂控制器、下拉刷新和 Insets 協調。

### 11. 生命週期、本地化與無障礙

頂層 destination 切換後返回時，如果 Fragment 與有效結果仍存在，保留折疊或編輯保留結果狀態。進程重建未恢復結果時只恢復起終點輸入，不建立沒有結果的折疊狀態。

新增或修改的按鈕、狀態卡、內容描述與提示提供香港繁體、獨立簡體及自然英文。折疊時清除隱藏輸入器焦點並避免無障礙服務遍歷不可見控件；鉛筆操作具有「編輯本次行程」內容描述，保存、已保存及查詢狀態均有可理解語義。

## Risks / Trade-offs

- [Risk] `adjustNothing` 令主視窗不再因 IME 自動縮小，候選或輸入可能被覆蓋 → 以既有 IME Insets 計算候選高度，並在 API 25／36、常見 IME、手勢／三鍵導航驗證。
- [Risk] 候選手勢 ownership 可能影響關閉後的結果滾動 → 只在候選可見的完整觸控手勢期間禁止父層攔截，不修改 AppBar flags／offset，並加入候選開關與列表位置 instrumentation。
- [Risk] 展示狀態與既有查詢 state 重複成為真相來源 → 展示狀態不保存 query id 或進行中布林值；所有 callback 仍由 `RouteQueryCoordinator` 以 generation 驗證，`RouteQueryState` 保存結果／進行中／刷新，renderer 只組合兩個 state。
- [Risk] 編輯輸入與保留結果屬於不同起終點，結果卡操作可能錯用新輸入 → 把成功結果快照保留為結果操作的唯一 owner；監控、刷新和 callback 驗證不讀取編輯器暫存值。
- [Risk] 編輯期間保留結果會讓用戶誤以為已套用新輸入 → 保存與下拉刷新立即停用，只有點擊搜尋才提交新輸入；提交後舊結果立即移除並顯示查詢中狀態。
- [Risk] 抽取狀態卡可能令常用頁產生視覺回歸 → 保留既有 ID、尺寸、文案與動畫行為，使用 contract test 和常用頁 loading／空／失敗回歸。
- [Risk] 「本次行程」欄在長地點和大字體下過高 → 正常字體以單行尾部省略，大字體才按實際量度切換雙行；操作組保持內容寬度，覆蓋三語、360dp 與 font scale 2.0。
- [Risk] 高度動畫與 AppBar nested scroll 同時改變版面可能閃動 → 以單一容器動畫目前已 layout 的高度，結束或取消時強制套用目標可見性及 layout params，並覆蓋 destination 切換／重建。
- [Risk] 禁止 AppBar direct drag 可能誤阻結果列表收折 → 只拒絕 direct drag，不改 nested scroll flags；instrumentation 分別驗證頂部手勢不動與結果列表仍可收折。
- [Trade-off] 保存後阻止同一查詢再次保存，犧牲搜尋頁直接建立同起終點多名稱的能力 → 使用行程管理的複製流程，換取更清楚的防重複回饋。

## Migration Plan

1. 先以純邏輯與 contract 測試固定展示狀態、XML／Manifest、候選高度和三語契約。
2. 精確移除兩個常駐 helper，調整搜尋候選與外層 scroll ownership。
3. 加入主 Activity IME 策略與候選 Insets 驗證。
4. 抽取狀態卡並讓常用頁／搜尋頁接入。
5. 加入搜尋展示狀態與輕量「本次行程」欄，接回既有保存流程，並以雙向動效在行程欄／完整編輯器間互斥切換。
6. 保留編輯期間的舊結果快照，隔離詳情／監控與新輸入，並在提交新查詢時才清除舊結果。
7. 禁止常用與搜尋 AppBar direct drag，只保留結果列表 nested scroll；空狀態內容不得帶動 AppBar。
8. 更新 UI 指南、驗收矩陣與 OpenSpec task，執行相關單測、instrumentation（有裝置時）及 `./gradlew build`。

若需回退，應按決策邊界逐項撤回：可先停用折疊狀態與共用狀態卡，再恢復搜尋候選上限或 IME 策略；不得回滾包含本地化及其他功能的大型歷史 commit。本 change 不涉及資料遷移。

## Open Questions

無。候選降級、邊界手勢、IME 覆蓋、輕量折疊樣式、無取消編輯、舊結果保留、保存重新取得條件、雙向動效、只由結果列表驅動頁面捲動及大字體重排均已在設計審查中確認。
