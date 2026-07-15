## Why

相同 debug build 在兩個 Android 模擬器上呈現不同：API 37 模擬器中文短文案顯示正常，API 36.1 模擬器中 `臨時查詢`、`使用此路線查詢`、`保存為常用`、`新增常用路線` 等短文字被按容器寬度拉開，形成不自然的逐字分散效果。這會讓主頁首次引導、臨時查詢底部彈層及其他短按鈕看起來像排版錯誤，削弱 App 在不同 Android 版本與模擬器上的可信度。

該症狀符合 Android `TextView` 字符間兩端對齊或 letter spacing 相關排版差異的表現。本變更不依賴判定它是平台 bug、Material 預設樣式或設備設定差異，而是讓 App 自有短文案顯式使用穩定排版屬性，確保常見 Android 版本下短標題、按鈕、chips 和短標籤保持自然字距。

## What Changes

- 為 App 自有 UI 中的短標題、主/次按鈕、text button、chips、短標籤、底部彈層標題、對話框標題及短操作項建立穩定排版規則。
- Kotlin 動態建立的 `TextView`、`MaterialButton` 等短文案控件應使用集中 helper 或等效方式，顯式禁用字符兩端對齊並重置字距與對齊。
- XML 中已有的短文案控件應透過 style 或顯式屬性補齊穩定排版設定。
- 更新 `app-ui-style-system` 規格，將短文案跨 Android 版本自然字距列為 App 自有 UI 基線。
- 更新 `docs/ui-style-guide.md`，沉澱後續 UI 改動的短文案排版規則。
- 新增或更新輕量契約測試，防止關鍵短文案控件再次缺少穩定排版策略。
- 在 API 36.1 與 API 37 模擬器保存首頁首次引導和臨時查詢底部彈層截圖，作為硬性視覺驗收證據。
- 不改變路線查詢、Citybus/DATA.GOV.HK 解析、ETA、排序、本機資料、通知監控或任何業務文案內容。

## Capabilities

### New Capabilities

- 無。

### Modified Capabilities

- `app-ui-style-system`: 擴展 App 自有 UI 的視覺基線，要求短標題、按鈕、chips、短標籤和短操作項在 API 36.1 與 API 37 等常見 Android 版本下保持自然字距、語義完整且不被字符間兩端對齊拉開。

## Impact

- 受影響代碼：
  - `app/src/main/java/com/golink/busiscoming/ui/common/` 或等效 UI helper 位置：新增短文案穩定排版 helper。
  - `app/src/main/java/com/golink/busiscoming/ui/main/TemporaryRouteBottomSheet.kt`: 修復臨時查詢底部彈層標題和主操作按鈕。
  - `app/src/main/java/com/golink/busiscoming/ui/main/MainActivity.kt`: 修復動態建立的常用路線底部彈層、臨時查詢入口等短文案。
  - `app/src/main/java/com/golink/busiscoming/ui/main/EtaArrivalsBottomSheet.kt`、`RouteDetailBottomSheet.kt`、`MonitorSettingsBottomSheet.kt`、`TemporaryRouteSaveDialog.kt`：檢查並修復底部彈層、對話框與短操作文案。
  - `app/src/main/res/layout/*.xml` 與必要的 `app/src/main/res/values/` style：補齊首頁首次引導按鈕、排序 chips、路線管理/編輯頁短按鈕等 XML 短文案控件。
- 受影響規格與文檔：
  - `openspec/specs/app-ui-style-system/spec.md`: 增加短文案排版穩定 requirement。
  - `docs/ui-style-guide.md`: 增加短文案排版穩定設計規則。
- 受影響測試：
  - 新增或更新 JVM 契約測試，確認關鍵 XML 控件與動態 UI 文件使用穩定短文案排版策略。
  - 實作完成後運行 `./gradlew build`。
- 兼容性與驗收：
  - 硬門禁為 API 36.1 與 API 37 模擬器，預設系統語言與 `font_scale=1.0`。
  - 需要保存 API 36.1/API 37 首頁首次引導和臨時查詢底部彈層截圖到本 change 的 `visual-review/` 目錄。
  - 驗收不要求像素級一致；允許不同 Android 版本在狀態欄、導航欄、字體 fallback、圓角或彈層高度上存在小差異。
  - 不要求真機驗證，不要求切換系統語言。
- 非目標：
  - 不升級 Android Gradle Plugin、compileSdk、targetSdk、Material Components 或 AppCompat。
  - 不引入 Compose，不遷移現有 XML 或動態 UI 結構。
  - 不把硬編碼中文文案遷移到 `strings.xml`。
  - 不把路線站名、候選地點、Citybus/DATA.GOV.HK 動態內容、長正文或系統通知模板文本套用短文案規則。
