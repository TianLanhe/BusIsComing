## Why

BusIsComing 雖然使用 DayNight 主題，但 App 自有顏色與部分動態元件仍固定為日間色，導致系統深色模式下出現文字、按鈕、卡片或背景難以辨識的問題。現在需要建立完整的明暗語意色系統，並讓用戶可在設定中選擇跟隨系統、固定淺色或固定深色，確保整個 App 的外觀一致且可控。

## What Changes

- 為 App 自有頁面、列表、表單、卡片、Dialog、Bottom Sheet 與系統欄建立完整的淺／深色語意色映射，清理會繞過主題的固定日間表面與文字顏色。
- 深色模式採用「深青綠夜行」方向，延續青綠主色與琥珀輔助色；淺色模式保持目前外觀，只做語意化與相容性整理。
- 在設定頁 `偏好` 分組第一項新增 `外觀主題`，右側顯示目前選擇，並以 Material 單選對話框提供 `跟隨系統`、`淺色模式`、`深色模式`。
- 新安裝和沒有既有偏好的升級用戶預設跟隨系統；選擇後立即保存並套用，App 重啟後保持設定，未知或損壞的偏好安全回退跟隨系統。
- 使用 AppCompat 標準 Activity 重建與資源限定符切換，不以 `configChanges` 或逐 View 執行時換色繞過生命週期。
- 驗證一般文字至少 4.5:1、大型文字與必要控制邊界至少 3:1，並覆蓋大字體、TalkBack、最低與近期 Android 版本的關鍵畫面。
- 不引入 Compose、DataStore、Material You 動態色、OLED 純黑模式或全域查詢狀態重構；不修改 Citybus／DATA.GOV.HK 請求、解析、路線排序、本機路線資料或通知監控協議。

## Capabilities

### New Capabilities

- `app-appearance-theme`: 定義三種外觀模式、預設與持久化、啟動與即時套用、跟隨系統、異常回退及 Activity 重建後的狀態邊界。

### Modified Capabilities

- `app-settings-support`: 在設定頁 `偏好` 分組加入位於語言之前的 `外觀主題` 入口、目前值摘要及 Material 單選互動。
- `app-ui-style-system`: 將目前只要求淺色背景與白色表面的視覺規格改為模式感知的語意色系統，保留淺色外觀並為深色模式規定一致表面、對比和元件覆蓋。

## Impact

- 受影響程式：App 啟動與 Manifest、外觀模式模型及 SharedPreferences store、`ui/settings/SettingsActivity`、Kotlin 動態 UI，以及各 Activity、Adapter、Dialog、Bottom Sheet 的顏色引用。
- 受影響資源：`values`／`values-night` 色票與主題、設定字串與版面、Drawable、Material 元件及系統狀態列／導覽列樣式。
- 受影響規格：新增 `app-appearance-theme`，修改 `app-settings-support` 與 `app-ui-style-system`；`app-chrome-layout` 的日夜模式無 ActionBar 行為保持不變。
- 外部接口與依賴：不修改 Citybus mobile、DATA.GOV.HK ETA、Google 地址解析或其他網路接口，不新增第三方依賴、權限或資料庫遷移。
- 驗證：新增模式映射與持久化單元測試、設定與資源 contract／UI 測試，執行 `./gradlew build`，並在 Android 7.1 與近期 Android 版本人工檢查主頁各狀態、編輯／管理／設定頁、Dialog、Bottom Sheet、大字體和 TalkBack；沒有設備時須明確記錄未完成項目。
