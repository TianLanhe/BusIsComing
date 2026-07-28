## 1. 固定展示狀態與資源契約

- [x] 1.1 在 `app/src/test` 新增搜尋展示狀態的純邏輯測試，覆蓋 Editing／Querying／Results／EditingResults／DirtyEditing／Saved、空結果／失敗、取消編輯、輸入失效及新 generation 重設保存狀態
- [x] 1.2 實作搜尋頁專用展示狀態與 reducer／policy，讓其只保存 UI 模式、查詢快照及保存狀態；保留 `RouteQueryCoordinator` 作為 query id、generation 與 callback 驗證的真相來源，並由 `RouteQueryState` 保存結果、進行中與刷新狀態
- [ ] 1.3 擴充 XML／Manifest contract tests，先固定地點靜態 helper、共用狀態卡、「本次行程」欄、搜尋頁舊保存入口移除及 `MainActivity` IME 策略
- [ ] 1.4 擴充 locale resource contract tests，要求本 change 新增或修改的查詢、編輯、取消編輯、保存、已保存、狀態卡及無障礙文案同時存在於香港繁體、簡體及英文資源

## 2. 恢復緊湊地點欄位

- [x] 2.1 只移除 `activity_route_edit.xml` 起點與終點的常駐 `helperText`，保留行程名稱 helper、label／hint、錯誤位置及現有動態 helper 控件
- [x] 2.2 補充或更新 `PlaceInputController`／行程編輯 contract tests，驗證 loading、無結果、搜尋失敗、定位失敗、校驗及 Google attribution 仍在所屬欄位按狀態顯示並可清除
- [ ] 2.3 回歸新增、編輯及複製行程，確認三種入口均為初始緊湊單行地點外觀，且行程名稱說明和保存校驗未改變

## 3. 擴充搜尋候選並修正手勢歸屬

- [x] 3.1 擴充 `PlaceCandidatePresentationPolicyTest` 與搜尋布局 contract tests，固定標準配置至少 5 個完整候選、最多 6 個，以及空間不足時只容納完整項目的降級規則
- [x] 3.2 將搜尋 destination 的 `PlaceInputController.maxVisibleRows` 調整為 6，沿用既有約 `52dp` 項目高度與 IME 可視區計算，不改變新增／編輯／複製行程的候選策略
- [x] 3.3 在 `SearchFragment` 聚合起終點候選可見性：任一候選展開時保存並停用 `searchContent` AppBar scroll flags、停用下拉刷新並阻止候選 nested scroll 傳給外層；全部關閉後精確恢復原狀態
- [x] 3.4 擴充 `PlaceInputControllerInstrumentedTest`／`SearchDestinationInstrumentedTest`，驗證候選內部可上下滑動、到頂或到底均不帶動 AppBar／頁面／結果列表，關閉候選後外層滾動、刷新資格及結果位置恢復

## 4. 固定底部導航的 IME 行為

- [ ] 4.1 只為 `MainActivity` 設定 `adjustNothing` 或等效不 resize 的窗口策略，保留 `RouteEditActivity` 等次級頁的既有 IME 行為
- [ ] 4.2 調整主 Activity／搜尋頁 Insets 協調，確保 IME 覆蓋物理底部導航時該導航不可觸控或取得無障礙焦點，收起 IME 後原 destination、量度與位置不變
- [ ] 4.3 擴充 `TopLevelNavigationInstrumentedTest` 與搜尋候選 instrumentation，驗證底部導航不被抬到鍵盤上方、候選仍依 IME Insets 完整停留在鍵盤上方，並覆蓋收起鍵盤後恢復

## 5. 共用查詢狀態卡與防重入

- [ ] 5.1 把常用頁既有 loading／空結果／失敗狀態卡抽為 `ui/common` 共用 View 或可 include layout，保留常用頁既有尺寸、文案、ID 契約及刷新回饋
- [ ] 5.2 讓搜尋頁移除裸 `ProgressBar`／普通狀態文字並接入共用狀態卡，首次或新查詢時顯示「正在查詢路線」，空結果與失敗時展示對應卡片且保持完整編輯器
- [ ] 5.3 讓 `SearchFragment` 的單一 `renderSearchUi()` 或等效入口同時依展示狀態與 `RouteQueryState` 推導搜尋按鈕 enabled、置灰、文字及狀態卡；查詢入口再次檢查進行中狀態以阻止快速連點、鍵盤 action 及刷新期間重複提交
- [ ] 5.4 擴充常用頁及搜尋頁狀態 contract／instrumentation，覆蓋 loading、空、失敗、取消、舊結果刷新保留、刷新浮層，以及過期狀態回呼不得覆蓋新畫面

## 6. 實作「本次行程」折疊與編輯流程

- [ ] 6.1 在 `fragment_search.xml` 加入搜尋專用「本次行程」上下文列與自適應容器，展示起點至終點、編輯及保存操作，並移除完整輸入區下方的常駐保存入口
- [ ] 6.2 在 `SearchFragment` 接入展示狀態 renderer：只有相符 generation 的非空成功結果進入折疊 Results；查詢中、空結果、失敗及取消均保持 Editing
- [ ] 6.3 實作編輯與取消編輯：展開時暫時保留原結果、控制器、刷新和保存資格，未修改可取消並重新折疊；隱藏輸入器不得接受觸控或無障礙焦點
- [ ] 6.4 將文字、已選 Place、清除及交換的實際改變統一接到失效流程，立即隱藏舊結果、摘要、上下文與保存入口，並作廢 query／刷新／ETA／站點預覽回呼
- [ ] 6.5 保持頂層 destination 切換後的有效折疊／編輯狀態；補充重建恢復策略，只有結果仍存在時才恢復折疊，否則只恢復起終點到完整編輯器
- [ ] 6.6 擴充 `SearchInteractionPolicyTest`、`RouteQueryGenerationTest` 及 `SearchDestinationInstrumentedTest`，覆蓋折疊時機、編輯未改、取消、實際修改、過期 callback、destination 切換和系統重建

## 7. 接回保存流程與多配置布局

- [ ] 7.1 將「本次行程」保存操作接回既有命名對話框、空值／重名處理及行程 repository，並使用目前有效 generation 的起終點快照且不重做 Geocoding
- [ ] 7.2 只在資料庫成功後把目前 generation 切換為填滿書籤與「已保存」停用狀態；失敗或取消時保留可操作入口，新成功查詢重設為可保存
- [ ] 7.3 為新增 UI 文案與 content description 完成香港繁體、獨立簡體及自然英文資源，禁止在 XML／Kotlin 硬編碼 App 可見文字
- [ ] 7.4 更新 `RouteSearchInputVisualMatrixInstrumentedTest` 或等效布局測試，驗證正常字體且空間足夠時單列展示，`360dp`、英文及 font scale `1.3／2.0` 時路徑與操作可分兩列，核心文字不縮小、操作不裁切且各有至少 `48dp` 觸控目標
- [ ] 7.5 補充保存流程 instrumentation，覆蓋成功後防重複、重名處理、取消／失敗可重試、編輯未改保留資格及新查詢重設狀態

## 8. 文件、回歸與交付驗證

- [ ] 8.1 更新 `docs/ui-style-guide.md` 與 `docs/localization-validation-matrix.md`，記錄動態 helper、候選 5 至 6 行與手勢 ownership、IME 覆蓋導航、共用狀態卡及「本次行程」響應式行為
- [ ] 8.2 執行相關 JVM contract／policy／generation 測試及可用的 instrumentation 測試，修正本 change 引入的失敗且不改動外部 API、SQLite schema、排序、ETA 或結果卡片語義
- [ ] 8.3 在模擬器或實機以繁體／簡體／英文、淺／深色、360dp、font scale `1.0／1.3／2.0` 驗證主要編輯、候選、查詢、失敗、折疊、保存和無障礙流程
- [ ] 8.4 在可用裝置覆蓋 API 25／36、手勢／三按鍵導航、常見 IME 及真實 Citybus 地點／路線查詢；若沒有相應裝置，記錄未完成矩陣及剩餘風險
- [ ] 8.5 執行 `./gradlew build`，再檢查 `git status --short` 與 staged 範圍，確認沒有構建產物或無關改動後依專案規則建立簡潔 conventional commit
