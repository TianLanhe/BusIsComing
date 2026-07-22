## Context

目前 `Theme.BusIsComing` 繼承 `Theme.MaterialComponents.DayNight.NoActionBar`，但 `app/src/main/res/values-night/themes.xml` 只覆蓋少量 Material 主色；App 自有的 `bus_*` 色彩全部位於 `values/colors.xml`。三模組導航合併後，`MainActivity` 承載 `FrequentRoutesFragment`、`SearchFragment`、`SettingsFragment` 與固定 `BottomNavigationView`；所有 layout、Drawable 和多個 Kotlin 動態元件因此仍在日夜模式下取得相同的頁面、卡片、文字與描邊色。

已知固定日間色包括常用／搜尋版面與動態按鈕表面、`table_row_background.xml` 的 `@android:color/white`，以及 `RouteDetailBottomSheet` 的 `Color.WHITE` 根背景。`fragment_frequent_routes.xml`、`fragment_search.xml`、`fragment_settings.xml`、底部導航、Material Dialog／Bottom Sheet 和 App 自有 View 由多套色彩來源渲染，造成深色模式層級不一致。

設定已遷移至 `ui/main/SettingsFragment.kt` 與 `fragment_settings.xml`，是底部導航中的頂層 destination，不顯示返回箭頭；`偏好` 目前只有語言入口，後面另有路線資料、支援與關於分組。專案已有 SharedPreferences store 的輕量模式，但沒有自訂 Application 在 Activity 建立前套用外觀偏好。另一個進行中的多語言 change 亦會使用 AppCompat configuration，因此外觀與語言偏好必須獨立且共用同一啟動協調。

本 change 橫跨 App 啟動、設定 UI、所有 App 自有表面與 Activity 重建生命週期，但不涉及 Citybus、DATA.GOV.HK、Google 地址解析、SQLite schema、通知 channel 或新的第三方依賴。設計遵循 `docs/ui-style-guide.md` 的「安靜實用的現代通勤工具」方向；該指南目前只描述淺色背景的段落將一併改為明暗模式語意，深色模式不視為偏離整體風格。

## Goals / Non-Goals

**Goals:**

- 在 XML、Drawable、Kotlin 動態 UI、Material 元件與系統欄建立一致的模式感知語意色。
- 保留現有淺色外觀，新增已確認的「深青綠夜行」深色調色盤。
- 提供跟隨系統、固定淺色與固定深色三種可持久化模式，預設跟隨系統。
- 讓設定選擇立即套用，並以 AppCompat 標準 Activity 重建和 Android 資源限定符完成切換。
- 保護既有本機資料、表單文字、匯入流程與通知監控 session；明確界定可重新查詢的即時結果。
- 保留目前頂層 destination、常用路線與搜尋表單等安全狀態，並讓外觀與語言設定互不重設。
- 以自動化測試、對比檢查與模擬器／實機矩陣驗證可讀性、無障礙及跨版本相容性。

**Non-Goals:**

- 不引入 Compose、DataStore、Material You 動態色、自訂調色盤或 OLED 純黑模式。
- 不重新設計淺色模式的資訊架構、間距、字體、圓角或排序。
- 不攔截 `uiMode` config change，不以逐 View 換色避免 Activity 重建。
- 不新增跨 Fragment／Activity 的查詢結果持久化或 ViewModel 架構重寫。
- 不修改外部 API、解析器、路線資料格式、資料庫、通知模板或 App 圖示。

## Decisions

### Decision 1: 以獨立模型、store 和 Application 套用外觀模式

做法：

- 新增純 Kotlin `data/model/AppThemeMode.kt`，固定定義 `SYSTEM`、`LIGHT`、`DARK`，提供穩定儲存值及對 `AppCompatDelegate` night mode 的映射。
- 新增 `data/local/AppThemePreferenceStore.kt`，以 application context 包裝獨立 SharedPreferences；無值、空白或未知值均回退 `SYSTEM`。
- 新增根 package 的 `BusIsComingApplication.kt`，或在多語言 change 先建立同名 Application 時擴充該唯一類別。Application `onCreate()` 讀取外觀 store，並在任何 Activity inflate 前呼叫 `AppCompatDelegate.setDefaultNightMode()`；Manifest 只註冊一個 Application。語言啟動策略與外觀 store 使用不同 key，任何一方均不得清除另一偏好。

原因：模式映射可純單測；持久化不依賴 Activity；程序冷啟動不會先以錯誤主題繪製再切換。SharedPreferences 足以保存單一小型 enum，不需要新增 DataStore 依賴或遷移；唯一 Application 也避免兩個 change 互相覆蓋 Manifest `android:name`。

替代方案：在每個 Activity `onCreate()` 讀取偏好。否決原因是責任重複，容易有頁面漏套用並出現啟動閃爍。

替代方案：只依賴 `AppCompatDelegate` 的程序內靜態狀態。否決原因是程序重啟後不保證保留用戶選擇，也無法處理損壞值回退。

### Decision 2: 頂層設定 Fragment 使用含摘要的單列與 Material 單選對話框

做法：在 `SettingsFragment` 的 `偏好` 分組第一項新增 `外觀主題`，右側或次要文字顯示已保存值；現有 `語言` 位於其後，`路線資料`、`支援`、`關於` 分組保持原順序。點擊後以 `MaterialAlertDialogBuilder` 或等效 Material 單選 API 顯示：

1. `跟隨系統`
2. `淺色模式`
3. `深色模式`

打開時選中 store 中的模式。選擇不同模式時先保存，再呼叫 `AppCompatDelegate.setDefaultNightMode()`；畫面重建後仍選中設定 destination，摘要更新就是成功反饋，不顯示 Toast。選擇目前模式只關閉對話框，不重建。若多語言能力已套用，語言摘要與 locale 偏好保持不變，且不額外手動呼叫 `recreate()`。

原因：設定只有三個互斥值，單選對話框比二級頁更短，也比在列表內放三段按鈕更適合窄螢幕和大字體。摘要讓用戶不打開對話框便能確認目前選擇。

替代方案：專用外觀頁。否決原因是目前沒有預覽、自訂色或其他外觀項，新增頁面沒有足夠資訊密度。

替代方案：設定列內嵌 segmented control。否決原因是繁體中文選項在窄螢幕或大字體下容易擠壓，TalkBack 焦點也較複雜。

### Decision 3: 使用日／夜語意色資源並映射 Material 主題

做法：

- 保留 `values/colors.xml` 作為淺色來源，新增必要的 `bus_on_accent`、`bus_on_secondary`、`bus_on_danger`、`bus_outline_strong` 等 token，但維持目前淺色視覺。
- 新增 `values-night/colors.xml`，以同名 token 提供深色值。
- 在 `values/themes.xml` 與 `values-night/themes.xml` 明確映射 `colorPrimary`／`colorOnPrimary`、`colorSecondary`／`colorOnSecondary`、`colorSurface`／`colorOnSurface`、`colorError`／`colorOnError`。
- Drawable、layout 與 Kotlin 動態 UI 使用語意色或主題屬性；一般表面與文字不得固定為白色。
- `BottomNavigationView` 的 surface、icon／label tint、indicator 與 ripple 使用模式感知資源；`values-en`／`values-b+zh+Hans` 只提供文字等 locale 資源，不複製或固定色票，讓 `values-night` 可與任何語言共同解析。
- 系統狀態列與導覽列使用模式對應背景及圖示明暗；`android:windowLightNavigationBar` 依 Android 資源 API 契約放入 `values-v27`／夜間限定資源，API 25–26 使用能配合系統能力的欄背景與淺色圖示；API 26 的文字 justification override 仍留在 `values-v26`。
- Android 7.1 沒有可供一般 App 跟隨的系統深色模式；即使測試 shell 接受 `night yes`，`UiModeManager` 亦不會計算或發布夜間 configuration。因此最低版本驗證跟隨系統淺色、固定淺色、固定深色與系統欄可見性，完整「跟隨系統＋系統深色」只在實際提供該 configuration 的近期 Android 驗證。
- 更新 `docs/ui-style-guide.md`，把目前「背景使用淺色中性表面」與「Dialog 使用白色表面」限定為淺色模式，新增深色語意規則。

原因：Android 資源限定符能自動涵蓋 XML、Drawable 和 Activity 重建；Material 屬性映射可讓 Dialog、Bottom Sheet、輸入框、ripple、停用與按下狀態和 App 色票一致。

替代方案：把所有自訂 token 全面換成 `?attr/colorSurface` 等 Material 屬性。否決原因是會擴大淺色回歸面，且現有多層青綠表面、ETA 與通勤狀態色仍需要 App 語意。

替代方案：由 Kotlin 逐一修改每個 View 的顏色。否決原因是動態列表、Bottom Sheet 和新增元件容易漏掉，且會把主題邏輯散落到 UI。

### Decision 4: 深色模式採用固定「深青綠夜行」色票

深色基準值如下；若 Material 狀態色需要微調，必須保持相同方向並通過 specs 對比門檻。

| 語意 token | 深色值 | 用途 |
|---|---:|---|
| `bus_page_gradient_start` | `#091816` | 頁面漸層起點 |
| `bus_page_gradient_center` | `#0C1A18` | 頁面漸層中點 |
| `bus_page_gradient_end` / `bus_surface` | `#101715` | 根背景與最低表面 |
| `bus_form_gradient_start` | `#172522` | 表單漸層起點 |
| `bus_form_gradient_end` | `#20312D` | 表單漸層終點 |
| `bus_card_surface` / `bus_state_surface` | `#172522` | 卡片、狀態卡與主要容器 |
| `bus_surface_variant` / `bus_chip_surface` | `#20312D` | 次級表面、輸入區與未選 chip |
| `bus_text_primary` | `#F1F8F5` | 標題與核心資訊 |
| `bus_text_secondary` | `#A6BBB4` | 輔助文字與站名 |
| `bus_chip_selected` / `bus_wait_accent` | `#72D3C1` | 主操作、選中態與 ETA |
| `bus_on_accent` | `#08241E` | 青綠操作上的文字／圖示 |
| `bus_action_secondary` | `#F0B25B` | 琥珀次級操作與提醒 |
| `bus_on_secondary` | `#2A1700` | 琥珀操作上的文字／圖示 |
| `bus_divider` | `#354A44` | 非必要裝飾分隔線 |
| `bus_outline_strong` | `#5A766E` | 必須可辨識的控制邊界 |
| `bus_wait_unavailable` | `#80918B` | 不可用候車文字 |
| `bus_danger` | `#FFB4AB` | 錯誤與刪除操作 |
| `bus_on_danger` | `#3B0907` | 錯誤色表面上的文字／圖示 |

主要文字／卡片約 14.72:1、次要文字／卡片約 7.85:1、青綠強調／卡片約 8.91:1、accent 上文字約 9.21:1、強描邊／卡片約 3.21:1。裝飾 divider 不承擔必要邊界語義；輸入框和必要控制使用 `bus_outline_strong` 或更高對比狀態色。

原因：此色票延續現有青綠品牌，深色但不採純黑，能保持「安靜實用」而非高對比娛樂介面。固定品牌色也使不同裝置的驗收和截圖一致。

替代方案：Material You 動態色。否決原因是會讓品牌與對比依裝置桌布漂移，且 Android 12 以下無法一致提供。

替代方案：OLED 純黑。否決原因是視覺過於銳利，卡片層級較難以現有克制描邊表達，也偏離已確認方向。

### Decision 5: 使用 AppCompat 標準重建並接受明確的暫態邊界

不在 Manifest 以 `configChanges` 攔截 `uiMode`。AppCompat 變更 night mode 後，Activity 依標準生命週期重建並重新選取 `values` 或 `values-night`。

必須保持：

- 外觀偏好、SQLite 路線與使用統計。
- Android 已能恢復的表單文字和 scroll state。
- `RouteTransferActivity` 已保存的 stage、URI、檔名及 summary。
- `BusMonitorSessionStore` 與前台監控服務狀態。

必須另外保持：

- 目前選中的常用／搜尋／設定 destination。
- 常用 destination 的已選 route id 與排序；搜尋 destination 的起終點、未提交文字與排序。

可重置：

- 常用與搜尋 owner 進行中的網路查詢、已顯示的即時 ETA／結果；保留上下文供用戶重新查詢。
- 已打開的 ETA、詳情或監控 Bottom Sheet。

原因：使用框架標準路徑能避免同一 Activity 內混用舊新資源。即時 ETA 本身具有時效，重新查詢比新增跨 Activity／Fragment 序列化更可靠；保留 destination 與起終點則符合三模組導航的安全恢復契約。

替代方案：手動處理 `uiMode` 並逐 View 刷新。否決原因是容易保留舊 Context／Drawable，造成部分元件顏色不一致和生命週期錯誤。

### Decision 6: 固定色依語意審核，不機械禁止所有 `Color.WHITE`

實作先掃描 `@color/white`、`@android:color/white`、`Color.WHITE` 和 literal hex，再判斷用途：

- 一般表面、一般文字和控制背景必須改為模式感知 token。
- App 圖示、第三方／系統模板和巴士路線識別色可以保持固定。
- 固定路線色上的文字、rail dot 等前景仍須逐一計算或人工確認對比，不能只因是例外便略過。

原因：機械替換所有白色可能破壞品牌圖示或路線識別；完全不設契約則會再次漏掉一般 UI。

替代方案：單純禁止原始碼出現 `Color.WHITE`。否決原因是規則會產生沒有產品意義的替換，且不能驗證真正的前景／背景組合。

## Risks / Trade-offs

- [Risk] Kotlin 動態建立的卡片、Dialog 或 Bottom Sheet 遺漏深色 token。→ Mitigation：固定色掃描、資源 contract 測試和逐畫面模擬器矩陣共同驗證。
- [Risk] Material Components 預設色與 App 語意色衝突。→ Mitigation：日／夜主題明確映射 surface、on-surface、primary、secondary、error 及系統欄屬性。
- [Risk] Activity 重建使正在進行的查詢或 Bottom Sheet 消失。→ Mitigation：在設定與 specs 明確記錄可重置邊界；持久資料和既有可恢復狀態不得受影響。
- [Risk] 三個頂層 Fragment 或底部導航遺漏深色資源。→ Mitigation：將 `fragment_frequent_routes.xml`、`fragment_search.xml`、`fragment_settings.xml`、導航 menu/tint 與兩個查詢 owner 的動態狀態納入 contract 及人工矩陣。
- [Risk] 外觀與語言 change 各自建立啟動或重建邏輯。→ Mitigation：只保留一個 Application，使用獨立 store/key；外觀只設定 night mode、語言只設定 locale，兩者不額外手動重建並加入連續切換測試。
- [Risk] 深色裝飾 divider 被誤用為必要控制邊界。→ Mitigation：分離 `bus_divider` 與 `bus_outline_strong`，必要邊界至少 3:1。
- [Risk] 淺色語意化意外改變現有畫面。→ Mitigation：淺色 token 保持既有色值，加入淺色截圖／人工回歸矩陣。
- [Risk] API 25 與近期 edge-to-edge 系統欄行為不同，且 API 25 不提供一般 App 可跟隨的系統深色 configuration。→ Mitigation：按 API 能力限定 theme 資源，在 Android 7.1 驗證跟隨系統淺色、兩種固定模式及 status／navigation bar 圖示可見性，在近期版本驗證完整四態。
- [Trade-off] SharedPreferences 的 enum 是簡單持久化，不提供觀察流。→ 可接受，因模式只在設定頁改變，AppCompatDelegate 負責通知 Activity 重建。

## Migration Plan

1. 先以單元／contract 測試固定模式映射、預設值、設定入口順序和夜間 token 完整性。
2. 新增 `AppThemeMode`、store、唯一 Application 與 Manifest 註冊，確認無偏好時保持跟隨系統，並與語言偏好／啟動流程互不覆寫。
3. 擴充 `SettingsFragment`、`fragment_settings.xml` 與字串，接入單選對話框和立即套用。
4. 建立日／夜色票與 Material 主題映射，更新風格指南。
5. 逐頁清理固定日間表面，先處理三個頂層 Fragment、底部導航及已知 XML／Drawable，再處理所有 Kotlin 動態 UI。
6. 執行相關單測、UI／contract 測試、`./gradlew build` 和模擬器／實機矩陣；多語言資源可用後補齊三語 × 淺／深色交叉驗收。

本 change 不修改資料庫 schema 或外部資料格式，無資料遷移。回滾時可移除設定入口、Application 套用和夜間色票；既有外觀偏好 SharedPreferences 即使保留也不會影響舊版資料或啟動。已保存的常用路線、匯入資料和通知監控 session 均保持可用。

## Open Questions

無。預設策略、三種模式、設定互動、深色方向、淺色範圍、動態色、Activity 重建邊界、無障礙門檻與驗證方式均已在探索階段確認。
