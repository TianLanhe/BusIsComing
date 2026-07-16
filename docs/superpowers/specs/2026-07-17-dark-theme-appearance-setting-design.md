# 深色模式與外觀主題設定設計

日期：2026-07-17

## 背景與問題診斷

BusIsComing 已使用 `Theme.MaterialComponents.DayNight.NoActionBar`，並具有 `values-night/themes.xml`，但目前深色模式只替換少量 Material 主題主色。App 自有的頁面背景、卡片、文字、分隔線、chip、狀態卡等顏色全部定義在單一 `values/colors.xml`，沒有對應的 `values-night/colors.xml`。

此外，部分 XML 與 Kotlin 動態 UI 直接使用 `@color/white`、`@android:color/white` 或 `Color.WHITE`。這些固定顏色不會隨 `uiMode` 改變。結果是系統切換到深色模式後，Material 元件可能採用夜間預設色，但 App 自有元件仍使用日間色，造成背景、文字、按鈕或描邊對比不足，甚至互相融在一起。

目前設定頁沒有外觀偏好，Manifest 亦沒有自訂 `Application` 用於在 Activity 建立前套用已保存模式。現有程式碼已有 SharedPreferences store 的先例，可沿用相同的輕量做法。

本次探索沒有可用的 adb 裝置，因此問題重現與根因判斷來自程式碼、資源和近期提交證據；實作完成後仍須補做模擬器或實機視覺驗收。

## 目標

- 修正所有 App 自有 Activity、卡片、表單、列表、Dialog 與 Bottom Sheet 的深色模式對比和一致性。
- 在設定頁新增「外觀主題」，支援「跟隨系統」、「淺色模式」與「深色模式」。
- 選擇後立即保存並套用；重新啟動 App 後保持選擇。
- 新安裝與沒有既有偏好的升級用戶預設「跟隨系統」。
- 保留目前淺色模式視覺，只做語意色整理和相容性修正。
- 深色模式採用已確認的「深青綠夜行」方向，延續青綠主色與琥珀輔助色。
- 一般文字對背景至少達到 4.5:1；大型文字、必要圖示和元件邊界至少達到 3:1。

## 非目標

- 不引入 Compose、DataStore、動態色或 Material You 取色。
- 不重新設計淺色模式的資訊架構、間距、字體或元件形狀。
- 不新增外觀設定二級頁、預覽頁、自訂色票或 OLED 純黑模式。
- 不修改 SQLite 路線資料、Citybus 查詢、ETA、通知監控 session 或匯入匯出協議。
- 不為主題切換建立新的全域查詢狀態架構；即時查詢結果可在 Activity 重建後重新查詢。
- 不重繪系統通知模板、第三方頁面或 App 圖示。

## 已確認的產品行為

1. 預設與升級策略均為「跟隨系統」。
2. 深色視覺採用「深青綠夜行」，不採用中性炭灰或 OLED 純黑。
3. 淺色模式維持現有外觀。
4. 不接入 Android 12+ 動態色。
5. 「外觀主題」位於設定頁「偏好」分組第一項，「語言」位於下一項。
6. 設定列右側顯示已保存模式；點擊後使用 Material 單選對話框。
7. 選擇不同模式後立即保存並套用，不顯示成功 Toast。
8. 選擇目前模式只關閉對話框，不做無效 Activity 重建。
9. 跟隨系統時，系統日／夜變更會更新 App；鎖定淺色或深色時忽略系統變更。
10. 常用路線、表單文字及匯入流程不得因主題切換丟失；正在查詢或已顯示的即時 ETA／結果允許重新查詢。

## 方案比較與決策

### 採用：語意色資源加 Material 主題映射

保留既有 `bus_*` 語意色，新增夜間限定色票，並將 Material 主題屬性映射到相同語意 token。XML 和 Kotlin 動態 UI 均從資源取得顏色，移除把一般表面或文字固定為白色的路徑。

此方案改動集中、能保留品牌色，也符合目前 XML、AppCompat 與 Material Components 架構。新增畫面只要複用語意 token，便能自然支援兩種模式。

### 未採用：全面改用 Material 標準屬性

把所有 App 色彩替換為 `?attr/colorSurface`、`?attr/colorOnSurface` 等標準屬性，標準化程度較高，但會大幅改動已穩定的淺色介面，也較難表達目前多層青綠表面和通勤狀態色。

### 未採用：執行時逐元件換色

由 Kotlin 在模式切換後逐一設定 View 顏色，初期直接但容易遺漏 RecyclerView item、動態 Bottom Sheet、Dialog、按下態與停用態，會把主題知識散落到 UI 邏輯中。

## 架構與責任邊界

### `AppThemeMode`

純 Kotlin 模型，固定包含：

- `SYSTEM`
- `LIGHT`
- `DARK`

它負責：

- 提供穩定、與顯示文案無關的儲存值。
- 對應 `AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM`、`MODE_NIGHT_NO` 與 `MODE_NIGHT_YES`。
- 對未知或損壞的儲存值回退 `SYSTEM`。

### `AppThemePreferenceStore`

以 application context 包裝獨立 SharedPreferences 檔案，只負責讀取和保存 `AppThemeMode`。預設值為 `SYSTEM`，不得依賴 Activity 或 View。

### `BusIsComingApplication`

在程序啟動、任何 Activity inflate 版面前：

1. 建立 `AppThemePreferenceStore`。
2. 讀取已保存模式。
3. 呼叫 `AppCompatDelegate.setDefaultNightMode()`。

Manifest 以 `android:name` 註冊此 Application。Application 不處理設定 UI、色碼或頁面狀態。

### `SettingsActivity`

只負責：

- 顯示「外觀主題」列和目前保存值。
- 使用 Material 單選對話框展示三個選項。
- 在選擇新模式時先保存，再呼叫 AppCompatDelegate 套用。
- Activity 重建後重新從 store 渲染摘要和對話框選中態。

設定頁不直接保存色碼，也不逐一通知其他 Activity 換色。

### 資源層

- `values/colors.xml`：保留淺色模式語意色。
- `values-night/colors.xml`：提供相同 token 的深色值。
- `values/themes.xml` 與 `values-night/themes.xml`：將 Material 主題屬性映射到對應語意色。
- Drawable、layout 與 Kotlin 動態 UI 只引用語意 token 或 Material 主題屬性。

這些邊界使未來設定頁由 Activity 遷移為 Fragment 時，偏好 store、啟動套用和色票均可保持不變。

## 資料流

### App 啟動

```text
Process start
-> BusIsComingApplication
-> AppThemePreferenceStore.load()
-> AppThemeMode 對應 AppCompat night mode
-> AppCompatDelegate.setDefaultNightMode()
-> Activity 建立
-> Android 選擇 values 或 values-night 資源
```

### 用戶切換模式

```text
外觀主題列
-> Material 單選對話框
-> 選擇不同模式
-> store.save(newMode)
-> AppCompatDelegate.setDefaultNightMode(newMode)
-> 必要 Activity 重建
-> 新資源與設定摘要生效
```

選擇目前模式時，在比較新舊模式後直接關閉對話框，不呼叫 AppCompatDelegate。

## 設定 UI 與互動

「偏好」分組順序：

```text
偏好
├── 外觀主題       跟隨系統／淺色模式／深色模式  ›
└── 語言           目前既有狀態
```

點擊「外觀主題」後展示標題同為「外觀主題」的 Material 單選對話框，選項順序固定為：

1. 跟隨系統
2. 淺色模式
3. 深色模式

對話框打開時選中已保存模式。點擊選項即完成選擇並關閉，不額外提供「確定」按鈕。若選擇「跟隨系統」但系統目前外觀與畫面相同，畫面可以沒有明顯明暗變化，但設定摘要必須更新為「跟隨系統」。

整個設定列維持至少 48dp 觸控高度。TalkBack 應能讀出設定名稱、目前值和 RadioButton 選中狀態。

## 色彩系統

### 淺色模式

現有淺色調色盤保持不變。實作必須新增 `bus_on_accent`、`bus_on_secondary`、`bus_on_danger` 與 `bus_outline_strong` 等缺少的語意 token，並為淺色模式提供維持目前外觀的值；不得藉此重新設計淺色畫面。

### 深色模式

深色模式使用以下基準色。實作時若對特定 Material 狀態色做微調，必須保持相同視覺方向並通過對比要求。

| 語意 token | 建議值 | 用途 |
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
| `bus_on_accent` | `#08241E` | 亮青綠主操作上的文字／圖示 |
| `bus_on_secondary` | `#2A1700` | 琥珀操作上的文字／圖示 |
| `bus_divider` | `#354A44` | 非必要裝飾分隔線 |
| `bus_outline_strong` | `#5A766E` | 必須可辨識的輸入框或控制邊界 |
| `bus_wait_unavailable` | `#80918B` | 不可用候車文字 |
| `bus_danger` | `#FFB4AB` | 錯誤與刪除操作 |
| `bus_on_danger` | `#3B0907` | 錯誤色表面上的文字／圖示 |
| `bus_action_secondary` | `#F0B25B` | 琥珀色次級操作與提醒 |

基準對比結果：主要文字／卡片約 14.72:1、次要文字／卡片約 7.85:1、青綠強調／卡片約 8.91:1、accent 上文字約 9.21:1、強描邊／卡片約 3.21:1。

### Material 主題映射

Material 主題至少應明確提供：

- `colorPrimary` / `colorOnPrimary`
- `colorSecondary` / `colorOnSecondary`
- `colorSurface` / `colorOnSurface`
- `colorError` / `colorOnError`
- status bar 與 navigation bar 顏色及圖示明暗

Dialog、Bottom Sheet、按鈕、TextInputLayout、進度元件、ripple、選中態、按下態及停用態須使用同一組語意，不依賴可能和 App 色票衝突的預設白色。

## 覆蓋範圍

必須檢查下列 App 自有介面：

- 主頁：首次使用、常用路線、查詢控制、loading、失敗、無結果、結果清單、排序與刷新狀態。
- 路線卡與常用路線卡：一般、選中、附近、候車不可用和刪除／危險狀態。
- 路線編輯：表單、TextInputLayout、起終點交換、候選地點、搜尋中／失敗／無結果。
- 路線管理：清單、空狀態與刪除確認 Dialog。
- 設定、關於、匯入匯出頁及匯入確認 Dialog。
- 臨時查詢、ETA、路線詳情與監控設定 Bottom Sheet。
- Kotlin 動態建立的 View、GradientDrawable、Card、chip 和文字。
- status bar 與 navigation bar 的背景及圖示明暗。

目前已知需要清理的固定日間色包括：主頁部分白色按鈕背景、`table_row_background.xml` 的系統白色，以及路線詳情 Bottom Sheet 的固定白色根背景。實作時須重新掃描所有 XML 與 Kotlin，按語意判斷是否改為 theme-aware 資源。

App 圖示、第三方／系統通知模板、巴士路線識別色可以保持固定。固定路線色上的前景色仍須逐一確認對比；不能因為屬於例外便忽略可讀性。

## Activity 重建與狀態邊界

主題變更使用 AppCompat 的標準重建行為，不在 Manifest 宣告 `configChanges` 來攔截 `uiMode`，也不以手動遍歷 View 的方式避免重建。

必須保持：

- 已保存的外觀模式。
- SQLite 中的常用路線和使用統計。
- Android 已能恢復的表單文字和 scroll state。
- 路線匯入頁現有的 stage、URI、檔名和 summary 恢復行為。
- 通知監控 session 與前台服務狀態。

主頁正在進行的網路查詢、已顯示的即時 ETA／結果和已打開的臨時 Bottom Sheet 可以在重建後關閉或要求重新查詢。本次不新增跨 Activity 的結果持久化。

## 錯誤與回退

- 未保存偏好：使用 `SYSTEM`。
- 未知、空白或損壞的偏好值：回退 `SYSTEM`，不得阻止 App 啟動。
- 選擇目前模式：不重建、不顯示 Toast。
- 模式套用後：畫面變色和設定摘要就是完成反饋，不顯示成功 Toast。
- 系統模式在「跟隨系統」下改變：允許標準 Activity 重建。
- 深色資源缺失：以資源契約測試在交付前阻止，而不是在執行時靜默修補。

## 測試與驗收

### 單元測試

- 三種 `AppThemeMode` 與 AppCompat night mode 的映射。
- 無偏好時預設 `SYSTEM`。
- 保存後重載保持模式。
- 未知或損壞儲存值回退 `SYSTEM`。

### 資源與程式碼契約

- 日／夜調色盤具有完整的必要語意 token。
- 一般 UI 表面與文字不得再使用破壞主題切換的固定白色。
- 固定品牌、路線識別或圖示色必須有明確例外理由並通過對比檢查。
- 設定頁「外觀主題」位於「偏好」第一項，「語言」位於其後。

### Instrumentation 與 UI

- 設定摘要和 RadioButton 選中態與已保存模式一致。
- 選擇新模式會套用並在重建後保持；重新選擇目前模式不觸發無效變更。
- App 重啟後保持已選模式。
- 跟隨系統時響應系統日／夜變更；鎖定淺色或深色時忽略系統變更。
- TalkBack 能讀取設定名稱、目前值和單選狀態。

### 手動視覺矩陣

至少檢查：

- 跟隨系統＋系統淺色。
- 跟隨系統＋系統深色。
- 強制淺色＋系統深色。
- 強制深色＋系統淺色。
- Android 7.1 與近期 Android 版本。
- 預設字體與大字體。
- 正常、按下、選中、停用、loading、空、失敗和危險狀態。

一般文字對背景至少 4.5:1；大型文字、必要圖示與控制邊界至少 3:1。最終執行 `./gradlew build`。若沒有連接裝置，交付時必須列出尚未完成的手動視覺驗收，不得把程式碼檢查描述為實機驗證。

## 風險與緩解

- 動態建立的 UI 遺漏深色：以固定色掃描、資源契約測試和 Bottom Sheet 視覺矩陣共同防止。
- Material 預設色與 App 色票衝突：在日／夜主題中明確映射 surface、on-surface、error 和系統欄屬性。
- Activity 重建造成即時結果消失：保持已確認的狀態邊界，設定頁即時套用但不擴大為全域狀態重構。
- 深色描邊層級不足：裝飾 divider 與必要 outline 分離，必要控制使用至少 3:1 的強描邊。
- 未來設定頁導航改造造成重工：store、Application 和資源層不依賴 SettingsActivity，設定列可遷移到 Fragment。

## 完成標準

- 三種外觀模式均可選、立即生效並在重啟後保持。
- 所有 App 自有畫面和動態元件使用一致的淺／深色語意資源。
- 深色模式沒有文字、按鈕、卡片或必要邊界與背景融在一起。
- 淺色外觀沒有非必要視覺改版。
- 對比、無障礙、重建、跟隨系統和跨版本驗收滿足本設計。
- 自動化測試及 `./gradlew build` 通過；手動驗收完成或清楚列出環境限制。
