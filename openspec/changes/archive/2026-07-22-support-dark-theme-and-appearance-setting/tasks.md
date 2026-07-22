## 1. 外觀模式契約與測試基線

- [x] 1.1 新增 `AppThemeModeTest`，先以失敗測試固定 `SYSTEM`／`LIGHT`／`DARK` 的穩定儲存值、AppCompat night mode 映射及未知值回退 `SYSTEM`。
- [x] 1.2 新增外觀資源 contract 測試，先要求日／夜色票具有完整同名語意 token，包含 on-color、強描邊、頁面、表單、卡片、文字、狀態與危險色。
- [x] 1.3 擴充 `AppSettingsSupportContractTest`，先要求頂層 `SettingsFragment` 的 `外觀主題` 位於 `偏好` 第一項、`語言` 位於其後，並保持 `路線資料`／`支援`／`關於` 分組、目前值摘要、三個繁體中文選項和至少 48dp 觸控高度。
- [x] 1.4 新增固定色審核 contract，列出允許保持固定的 App 圖示／路線識別色例外，並讓一般 UI 的 `@android:color/white`、固定白色 surface 或未說明 literal hex 先觸發失敗。
- [x] 1.5 執行上述針對性 JVM 測試並保存紅燈證據，確認失敗原因分別指向尚未建立的模式模型、設定入口及夜間資源。

## 2. 模式模型、持久化與冷啟動套用

- [x] 2.1 新增 `data/model/AppThemeMode.kt`，實作三種模式、穩定儲存值、解析回退及 AppCompat night mode 映射，使 1.1 測試通過。
- [x] 2.2 新增 `data/local/AppThemePreferenceStore.kt`，以 application context 和獨立 SharedPreferences 保存單一模式；無值、空白或未知值均返回 `SYSTEM`。
- [x] 2.3 新增或擴充唯一的 `BusIsComingApplication.kt`，在任何 Activity 建立前讀取外觀 store 並呼叫 `AppCompatDelegate.setDefaultNightMode()`；在 `AndroidManifest.xml` 只註冊一個 Application，與語言啟動策略／偏好使用獨立 key，且不新增權限、服務或外部依賴。
- [x] 2.4 新增或擴充 instrumentation／contract 測試，覆蓋 store 預設、保存後重載、損壞值回退、Manifest Application 註冊，以及外觀與語言偏好互不覆寫。
- [x] 2.5 驗證無既有偏好的新安裝與升級路徑均跟隨系統，並確認程序重啟前後保存模式一致且第一個畫面不先以錯誤主題閃爍。

## 3. 設定頁外觀主題互動

- [x] 3.1 在 `strings.xml` 新增 `外觀主題`、`跟隨系統`、`淺色模式`、`深色模式` 及必要無障礙字串，全部使用繁體中文。
- [x] 3.2 更新 `fragment_settings.xml` 與設定列 style，在 `偏好` 第一項加入完整可點擊的外觀列和模式摘要，保持 `語言` 及後續 `路線資料`／`支援`／`關於` 順序，大字體下不得重疊。
- [x] 3.3 在 `SettingsFragment` 載入 store 並渲染摘要；以 Material 單選對話框按固定順序顯示三個模式和目前選中態。
- [x] 3.4 實作選擇不同模式時先保存、關閉對話框並立即套用；重建後仍選中設定 destination 且語言偏好不變；選擇目前模式只關閉，不顯示 Toast、不額外手動 `recreate()` 或執行可見的無效重載。
- [x] 3.5 新增設定 UI instrumentation／contract 測試，覆蓋摘要、選項順序、RadioButton 選中態、立即套用、Activity 重建後仍在設定 destination、語言摘要不變、重選無效模式及 TalkBack 可讀名稱／目前值。

## 4. 日夜語意色與 Material 主題

- [x] 4.1 在 `values/colors.xml` 新增缺少的 `bus_on_accent`、`bus_on_secondary`、`bus_on_danger`、`bus_outline_strong` 等淺色 token，保持既有淺色畫面色值與層級不變。
- [x] 4.2 新增 `values-night/colors.xml`，按 design 的「深青綠夜行」基準值定義完整夜間 token，並以測試驗證主要文字 4.5:1、必要圖示／邊界 3:1 的組合。
- [x] 4.3 更新 `values/themes.xml` 與 `values-night/themes.xml`，明確映射 primary、secondary、surface、error 及所有 on-color，確保 Material Button、Dialog、Bottom Sheet、TextInputLayout、RadioButton、ripple、進度、停用及錯誤狀態使用同一語意。
- [x] 4.4 更新 status bar／navigation bar 背景與圖示明暗；使用適當 API 版本限定資源處理 light navigation bar，確保 Android 7.1 與近期 edge-to-edge 行為均可辨識。
- [x] 4.5 清理 layout 與 Drawable 中的一般固定日間表面，至少覆蓋 `activity_main.xml`、`fragment_frequent_routes.xml`、`fragment_search.xml`、`fragment_settings.xml`、底部導航 tint／indicator、`table_row_background.xml`、卡片／狀態／候選／表單／chip 背景，並讓固定色 contract 通過。
- [x] 4.6 更新 `docs/ui-style-guide.md` 與 README 外觀說明，把淺色背景規則限定於淺色模式並加入深色語意、對比、固定色例外和設定入口。

## 5. Kotlin 動態 UI 與全畫面深色覆蓋

- [x] 5.1 更新 `MainActivity`／`FrequentRoutesFragment` 的動態常用路線卡、路線選擇 Bottom Sheet、排序 chip 與選中前景，改用模式感知 surface／on-color，保持既有排序與查詢行為。
- [x] 5.2 更新 `RouteDetailBottomSheet` 的固定白色根背景、header、站點、toggle、rail 與 route badge 前景；固定路線識別色逐一驗證前景對比並記錄例外理由。
- [x] 5.3 更新 `SearchFragment`、`EtaArrivalsBottomSheet`、`MonitorSettingsBottomSheet` 與保存 Dialog 的動態文字、容器、輸入、按鈕、進度及錯誤狀態，使搜尋初始／載入／成功／空／失敗／刷新在淺／深色均可辨識。
- [x] 5.4 更新 `SettingsFragment`、關於、路線匯入匯出、管理與編輯流程中的動態 View、表格、候選列表及確認 Dialog，移除一般 UI 的固定白色或日間文字色。
- [x] 5.5 重新掃描所有 `app/src/main` XML／Kotlin 的 `@color/white`、`@android:color/white`、`Color.WHITE` 及 literal hex；只保留 contract 允許且已驗證對比的 App 圖示、路線識別或第三方／系統例外。

## 6. Activity 重建、資料保護與回歸驗證

- [x] 6.1 新增 instrumentation 測試覆蓋跟隨系統日夜切換、固定淺色忽略系統深色、固定深色忽略系統淺色，以及程序重啟後保持選擇。
- [x] 6.2 驗證主題切換使用 AppCompat 標準生命週期，Manifest 未新增 `uiMode` `configChanges`；重建後恢復原頂層 destination、常用 route id、搜尋起終點／未提交文字、兩個 owner 的排序／scroll，以及 `RouteTransferActivity` stage／URI／檔名／summary。
- [x] 6.3 在已有常用路線與使用統計的情境切換三種模式，確認 SQLite 資料完全不變；在監控運行時切換模式，確認 `BusMonitorSessionStore`、前台服務、排程、通知 channel 與語音設定不變。
- [x] 6.4 在常用與搜尋 destination 的 loading、成功結果、ETA、無結果與失敗狀態切換模式，確認重建後上下文可重新查詢、兩個 owner 不交叉覆蓋，且不把過期 ETA 持久化為外觀狀態。
- [x] 6.5 執行既有頂層導航、常用／搜尋狀態、設定、路線管理、編輯、匯入匯出、查詢、排序、詳情與通知監控相關測試，確認本 change 未改動外部 API、解析、資料格式、排序或背景服務行為。

## 7. 完整驗收、OpenSpec 與提交

- [x] 7.1 執行外觀模式、設定、資源與固定色的針對性測試，再執行 `./gradlew testDebugUnitTest`，修正所有新增及既有單元／contract 回歸。
- [x] 7.2 執行 `./gradlew build`，確認 Kotlin 編譯、unit tests、lint 及 debug／release assemble 全部通過，且未重複套用 Kotlin Android plugin。
- [x] 7.3 在近期 Android 完成四種外觀矩陣：跟隨系統＋系統淺色、跟隨系統＋系統深色、固定淺色＋系統深色、固定深色＋系統淺色；在不提供系統深色 configuration 的 Android 7.1 覆蓋跟隨系統淺色、固定淺色、固定深色與系統欄可見性。多語言資源可用後，再對繁體、簡體、英文分別完成固定淺色與固定深色交叉驗證。
- [x] 7.4 人工檢查常用／搜尋／設定三個 destination、底部導航、兩個查詢 owner 各狀態、路線卡、編輯與候選、管理、關於／匯入頁、所有 Dialog／Bottom Sheet、status／navigation bar、預設與大字體、TalkBack、正常／按下／選中／停用／錯誤狀態；保存關鍵截圖與對比結果，無設備時明確記錄未完成項目及風險。
- [x] 7.5 執行 `openspec validate support-dark-theme-and-appearance-setting --strict`，確認 proposal、design、三份 specs 與 tasks 一致且可追溯。
- [x] 7.6 更新本文件所有實際完成任務為 `- [x]`，檢查 `git status --short`、`git diff --cached --stat` 與驗證紀錄，排除構建產物及無關改動後，依專案規則自動提交本次 `/opsx-apply` 實作。
