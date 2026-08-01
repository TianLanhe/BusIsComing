## 1. 固定回歸契約與測試基線

- [x] 1.1 更新 `LocationFeatureContractTest`、`PlaceInputInlineCandidatesContractTest`、`AppPageStyleContractTest` 及相關版面契約，明確要求 `RouteEditActivity` 使用歷史獨立欄位結構、搜尋頁保留 `PlacePairEditorView`，並刪除兩頁必須共用完整幾何的舊斷言。
- [x] 1.2 為行程編輯器補充 instrumentation 回歸測試，覆蓋 helper、Material 尾端定位圖示、獨立載入列、欄位與候選間距、透明交換按鈕及候選展開時隱藏交換按鈕。
- [x] 1.3 為 `SearchFragment` 的位置快照狀態補充可注入測試，覆蓋首次進入、返回／重建、手動定位成功、地址解析失敗、無權限／定位不可用及過期 callback，並驗證起終點候選均使用同一有效快照顯示距離。
- [x] 1.4 為 Xiaomi 品牌判斷、`GRANTED／DENIED／UNKNOWN` 權限狀態、本機閘門、設定 Intent 回退及只續辦一次建立 JVM 測試基線。
- [x] 1.5 更新 `TransitCodeShortcutContractTest` 與乘車碼啟動測試，固定靜態／pinned shortcut 的無界面 target、穩定 shortcut id、既有支付候選順序及失敗降級契約。

## 2. 恢復行程頁並修正搜尋輸入器

- [x] 2.1 以 `62f1abf` 前的實作為基準恢復 `activity_route_edit.xml` 的起點、終點、helper、載入列、候選容器與歷史 view id，保持至少 `56dp` 欄位高度、`16dp` 水平內距、`14dp` 欄位間距及 `6dp` 候選間距。
- [x] 2.2 重接 `RouteEditActivity` 的歷史 view binding 與 `PlaceInputController`，恢復新增、編輯、複製、定位、候選選擇、載入提示、交換及返回鍵行為；唯一視覺改動為交換按鈕使用透明／borderless ripple 背景。
- [x] 2.3 保留搜尋專用 `PlacePairEditorView` 的緊湊版面，把定位與交換控制設為固定 `48dp` 觸控槽、`24dp` 圖示／loading 並在水平及垂直方向居中，不改動行程頁幾何。
- [x] 2.4 在 `SearchFragment` 建立 View 生命週期級 `CurrentLocationSnapshot`，於已有前台定位權限且定位可用時非阻塞請求一次，與自動填入起點及 Reverse Geocoding 並行且互不依賴。
- [x] 2.5 把最新有效快照同步傳給起點與終點 `PlaceInputController`，在手動定位成功及 View 恢復時更新／重套；對 View 銷毀、語言 generation 或新請求造成的過期 callback 不更新 UI。
- [x] 2.6 驗證搜尋頁在位置不可用時仍可立即輸入、交換與查詢，候選只省略距離；位置可用時兩個欄位的每個候選均顯示以手機目前位置計算的距離。

## 3. 實作 Xiaomi 桌面快捷方式權限流程

- [x] 3.1 新增可注入的 Xiaomi shortcut 權限策略，只識別正規化後的 Xiaomi、Redmi、POCO manufacturer／brand，並以公開能力輸出 `GRANTED`、`DENIED` 或 `UNKNOWN`，不得使用隱藏 AppOps 或反射。
- [x] 3.2 新增 Xiaomi 權限設定 navigator，先以可解析的 Xiaomi 系統權限 Intent 開啟 BusIsComing 權限頁；不可解析、啟動失敗或 Activity 不存在時降級至 `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`。
- [x] 3.3 在 `TransitCodeShortcutManager`／設定流程加入 `PINNED`、`NEEDS_PERMISSION`、`REQUESTED`、`UNSUPPORTED`、`FAILED` 等結構化結果，區分 Launcher 接受請求、成功 callback 與重新查詢確認 pinned 三種狀態。
- [x] 3.4 在 `SettingsFragment` 實作 Xiaomi `DENIED` 與首次 `UNKNOWN` 權限閘門、返回後一次性自動續辦及重入保護；只有成功 callback 或確認 pinned 後才持久化閘門通過，後續請求仍未 pinned 時清除標記。
- [x] 3.5 移除 `requestPinShortcut() == true` 後立即顯示「請在系統視窗確認新增」的誤導回饋；保留 pinned 成功、取消後可重試、Launcher 不支援指引及失敗重試狀態。
- [x] 3.6 為新增／修改的權限、等待、未新增與失敗狀態補齊 `values`、`values-b+zh+Hans`、`values-en` 三語資源，並確認淺色與深色設定頁均使用資源而非硬編碼文案。

## 4. 讓桌面乘車碼快捷方式直接轉發

- [x] 4.1 新增 `TransitCodeShortcutActivity`，以無內容、無預覽、`noHistory`、`excludeFromRecents` 的 manifest／theme 配置，在 `onCreate()` 僅呼叫既有 `TransitCodePaymentLauncher` 一次並立即 `finish()`。
- [x] 4.2 讓轉發 Activity 完整沿用現有 AlipayHK／支付寶安裝偵測、scheme／HTTPS 候選順序與「Intent 被接受後停止降級」語義；全部候選失敗時顯示既有三語提示後結束。
- [x] 4.3 更新 `shortcuts.xml` 與 pinned shortcut builder，使靜態及 pinned shortcut 在保留 `transit_code` 穩定 id 的前提下都指向轉發 Activity，且不把支付 URI 或候選清單寫入 shortcut 定義。
- [x] 4.4 在啟動或 shortcut 管理流程使用原位更新能力處理既有同 id pinned copy，驗證升級後不建立第二個桌面圖示；App 內與候車通知入口維持現有生命週期並繼續共用同一支付啟動器。
- [x] 4.5 增加 instrumentation 驗證冷啟動桌面轉發不建立 `MainActivity`、不留下 BusIsComing 最近任務或返回堆疊，且外部支付入口失敗時不會停留空白頁面。

## 5. 自動化與跨狀態 UI 驗證

- [x] 5.1 執行相關 JVM 與 instrumentation 測試，確認行程頁歷史布局、搜尋工具居中與候選距離、Xiaomi 狀態機、設定回饋和 shortcut target 全部通過。
- [x] 5.2 在繁體、簡體、英文及淺／深色下驗證行程頁、搜尋頁和設定頁，覆蓋 `360dp`、font scale `1.0／1.3／2.0`、文字截斷、觸控目標、焦點、載入與無障礙描述。
- [x] 5.3 在 Pixel 模擬器驗證非 Xiaomi 標準流程：系統 pinned 確認面板、成功 callback、取消後可重試、移除快捷方式後狀態更新、靜態與 pinned 圖示直達轉發且主頁不閃現。
- [x] 5.4 透過無線或 USB 偵錯在 Xiaomi 14／HyperOS 驗證權限開關關閉、Xiaomi 設定跳轉、通用設定回退、返回後只續辦一次、固定成功與桌面點擊直接進入正確支付頁；記錄系統版本與實際可解析 Intent。
- [x] 5.5 執行 `./gradlew build`，再檢查 `git status --short`、測試結果及提交範圍；若 Xiaomi 真機驗證無法完成，明確記錄未驗證項與剩餘風險，不宣稱專項流程通過。
