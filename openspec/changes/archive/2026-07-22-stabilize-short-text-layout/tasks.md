## 1. 契約與掃描準備

- [x] 1.1 新增或更新 JVM 契約測試，列出本次必須覆蓋的短文案位置：首頁首次引導按鈕、臨時查詢底部彈層標題與主按鈕、常用路線底部彈層、排序 chips，以及路線詳情/ETA/監控底部彈層的短操作項。
- [x] 1.2 在契約測試中確認 XML 中的關鍵短文案控件具備穩定排版屬性或引用等效 style，例如 `android:justificationMode="none"`、`android:letterSpacing="0"` 和明確的 `gravity`/`textAlignment`。
- [x] 1.3 在契約測試或源碼級測試中確認 Kotlin 動態 UI 文件使用統一短文案 helper 或等效策略，至少覆蓋 `TemporaryRouteBottomSheet.kt`、`MainActivity.kt`、`RouteDetailBottomSheet.kt`、`EtaArrivalsBottomSheet.kt`、`MonitorSettingsBottomSheet.kt` 和 `TemporaryRouteSaveDialog.kt`。
- [x] 1.4 搜尋 `TextView(`、`MaterialButton(`、XML `android:text=` 和排序 chips 等 App 自有短文案控件，形成實作檢查清單；明確排除路線站名、候選地點、用戶輸入、第三方動態資料和長正文。

## 2. 共用短文案排版策略

- [x] 2.1 在 `app/src/main/java/com/golink/busiscoming/ui/common/` 或等效共用 UI 位置新增短文案穩定 helper，集中處理 `TextView` 的 `justificationMode = Layout.JUSTIFICATION_MODE_NONE`（API 26+）、`letterSpacing = 0f`、`textAlignment` 和 `gravity`。
- [x] 2.2 helper SHALL 允許調用方指定 `Gravity.START`、`Gravity.CENTER` 或 `Gravity.END`，不得把所有短文案硬性居中。
- [x] 2.3 helper SHALL NOT 全局強制所有短文案 `maxLines = 1` 或固定高度；只保留控件原本已有的單行/省略策略。
- [x] 2.4 補充必要的 XML style 或屬性組合，讓 XML 中短按鈕、chips 和短標籤可以使用同一穩定排版基線。

## 3. 動態 UI 套用

- [x] 3.1 更新 `TemporaryRouteBottomSheet.kt`，讓 `臨時查詢`、`使用此路線查詢`、`保存為常用`、loading 短文案和定位歸因短文案中屬於短標題/短操作的控件套用穩定排版策略。
- [x] 3.2 更新 `MainActivity.kt` 中動態建立的常用路線底部彈層、`附近` 標籤、`查詢臨時起點和終點` 行等短文案控件；不得改變常用路線選擇、臨時查詢入口或結果排序行為。
- [x] 3.3 更新 `RouteDetailBottomSheet.kt`、`EtaArrivalsBottomSheet.kt`、`MonitorSettingsBottomSheet.kt` 和 `TemporaryRouteSaveDialog.kt` 中的短標題、短按鈕與短操作項；長段說明與動態路線內容保持既有排版。
- [x] 3.4 檢查 `PlaceInputController.kt` 的候選地點項目，確認候選名稱不套用短文案 helper，保留既有單行省略和距離顯示策略。

## 4. XML UI 套用

- [x] 4.1 更新 `activity_main.xml`，覆蓋 `乘車碼`、首頁首次引導主/次按鈕、`常用路線`、`查詢`、臨時上下文條短按鈕和排序 chips 的穩定短文案屬性。
- [x] 4.2 更新 `activity_route_edit.xml`，覆蓋返回、標題、loading 短文案和 `儲存` 等短控件；不得改變輸入框、候選列表、交換按鈕或保存校驗。
- [x] 4.3 更新 `activity_route_manage.xml`、`item_route_config.xml`、`activity_settings.xml` 和 `activity_about.xml` 中的短標題、短按鈕和短操作項；不得改變設定、關於、分享、反饋或刪除確認行為。
- [x] 4.4 檢查 `item_bus_route.xml` 等結果卡片 layout，僅在短標籤/短操作需要時補齊排版屬性；路線號、站點預覽、候車摘要、價格、耗時和步行距離保持既有可讀與省略策略。

## 5. 文檔與規格同步

- [x] 5.1 更新 `docs/ui-style-guide.md`，新增短文案排版穩定規則：短標題、按鈕、chips 和短標籤應顯式禁用字符間兩端對齊並保持自然字距。
- [x] 5.2 在同一文檔中明確說明短文案規則不適用於長正文、動態路線內容、候選地點、用戶輸入或系統通知模板文本。
- [x] 5.3 確認本 change 的 `proposal.md`、`design.md`、`specs/app-ui-style-system/spec.md` 和 `tasks.md` 與實作範圍一致；如實作中發現必要範圍擴張，先更新 OpenSpec 工件再繼續。

## 6. 自動化驗證

- [x] 6.1 運行新增或更新的短文案契約測試，確認關鍵 XML 和動態 UI 文件已覆蓋穩定排版策略。
- [x] 6.2 運行 `./gradlew testDebugUnitTest`，確認 JVM 單元測試通過。
- [x] 6.3 運行 `./gradlew build`，確認編譯、單元測試、lint、debug/release assemble 通過。
- [x] 6.4 運行 `openspec validate stabilize-short-text-layout --strict`，確認 proposal、design、specs 和 tasks 均通過 OpenSpec 驗證。
- [x] 6.5 搜尋全倉庫，確認沒有因本修復新增全局強制單行或固定高度策略，也沒有修改 Citybus、DATA.GOV.HK、ETA、排序、本機資料或通知監控邏輯。

## 7. API 36.1 / API 37 視覺驗收

- [x] 7.1 在 API 36.1 模擬器、預設系統語言、`font_scale=1.0` 下安裝同一 debug build，打開無常用路線首頁首次引導，確認 `新增常用路線`、`直接查詢一次` 等短按鈕自然可讀、不被逐字拉滿。
- [x] 7.2 在 API 36.1 模擬器打開臨時查詢底部彈層，確認 `臨時查詢`、`使用此路線查詢` 和 `保存為常用` 自然可讀、不被逐字拉滿。
- [x] 7.3 保存 API 36.1 首頁首次引導和臨時查詢底部彈層截圖到 `openspec/changes/stabilize-short-text-layout/visual-review/`。
- [x] 7.4 在 API 37 模擬器、預設系統語言、`font_scale=1.0` 下重複 7.1 和 7.2，確認短文案同等自然可讀。
- [x] 7.5 保存 API 37 首頁首次引導和臨時查詢底部彈層截圖到 `openspec/changes/stabilize-short-text-layout/visual-review/`。
- [x] 7.6 人工檢查常用路線底部彈層、結果排序 chips、路線詳情底部彈層、ETA 底部彈層和監控底部彈層中的代表性短文案；允許系統欄、字體 fallback、圓角或彈層高度存在非語義差異，不要求像素一致。

## 8. 驗證記錄

- [x] 8.1 在本文件記錄 API 36.1 與 API 37 使用的 emulator 名稱、Android 版本、`font_scale`、驗收頁面、截圖文件名和結論。
- [x] 8.2 若某個代表性短文案不適合套用 helper，記錄原因和保留的排版策略。
- [x] 8.3 完成 `/opsx-apply` 後依專案規則檢查 `git status --short`、驗證結果和提交範圍，然後自動提交本次改動。

## 驗證記錄

- 自動化：`./gradlew testDebugUnitTest --tests com.golink.busiscoming.ShortTextLayoutContractTest` 已先紅燈，再於實作後通過；`./gradlew testDebugUnitTest` 通過；`./gradlew build` 通過；`openspec validate stabilize-short-text-layout --strict` 通過。
- 搜尋檢查：`PlaceInputController.kt` 未套用 `applyStableShortTextLayout`；`app/src/main/java/com/golink/busiscoming/data`、`data/repository` 和 `service` 未被本修復改動或套用 helper；`ShortTextLayout.kt` 未新增全局 `maxLines = 1` 或固定高度策略。
- API 37：`Pixel_8`，Android release `17`，SDK `37`，`font_scale=1.0`；已驗收首頁首次引導與臨時查詢底部彈層；截圖為 `api37-first-run.png`、`api37-temporary-query-sheet.png`；結論：`新增常用路線`、`直接查詢一次`、`臨時查詢`、`使用此路線查詢`、`保存為常用` 自然可讀，未逐字拉滿。
- API 36.1：`Pixel_9_API_36_1`，Android release `16`，`ro.build.version.sdk_full=36.1`，extension level `20`，`font_scale=1.0`；已驗收首頁首次引導與臨時查詢底部彈層；截圖為 `api36-1-first-run.png`、`api36-1-temporary-query-sheet.png`；結論：`新增常用路線`、`直接查詢一次`、`臨時查詢`、`使用此路線查詢`、`保存為常用` 自然可讀，未逐字拉滿。
- API 36 補充：`Pixel_9_API_36`，Android release `16`，SDK `36`，`font_scale=1.0`；截圖為 `api36-first-run.png`、`api36-temporary-query-sheet.png`；結論：同一組短文案自然可讀，未逐字拉滿。
- 代表性畫面：在 `Pixel_9_API_36` 使用同一 debug build 人工檢查常用路線底部彈層、結果排序 chips、路線詳情底部彈層、ETA 底部彈層和監控底部彈層；短標題、chips、短按鈕和短操作項均保持自然字距、未逐字拉滿，且未與相鄰控件重疊。
- 未套用 helper 的代表性內容：路線站名、候選地點、用戶輸入、路線名稱、起終點路徑、站點預覽、候車摘要、價格、耗時、步行距離和長段說明保持既有省略、換行、對齊或動態資料展示策略。
