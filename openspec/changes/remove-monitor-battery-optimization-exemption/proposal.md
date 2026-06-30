## Why

App 準備上架 Google Play，通知欄監控目前會在啟動流程中直接引導用戶授予電池最佳化豁免，這會增加審核與用戶信任成本。監控的核心效果已由前台服務、ongoing notification、鎖屏可見通知、controlled wake lock、exact alarm 調度與 TextToSpeech 播報共同支撐；本變更只移除高敏感的電池豁免請求，並在啟動前自然告知鎖屏展示與語音播報行為。

## What Changes

- 移除通知欄監控對 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 的 manifest 宣告與直接系統豁免請求入口。
- 監控啟動流程不再因未取得電池最佳化豁免而跳轉系統豁免頁或顯示相關 toast。
- 保留現有 exact alarm special access 依賴、`SCHEDULE_EXACT_ALARM` 權限、`setExactAndAllowWhileIdle()` 優先調度與 fallback 行為。
- 保留現有 `dataSync` 前台服務類型、ongoing notification、鎖屏通知可見性、通知 action、TextToSpeech 狀態切換播報與 wake lock 保護。
- 在通知欄監控啟動面板補充一句自然提示，說明鎖屏會顯示路線與 ETA，開啟語音後狀態變更可能在鎖屏時播報。
- 不改變 Citybus/DATA.GOV.HK 查詢、ETA 解析、站點對齊、排序、路線結果卡片資料或通知內容格式。

## Capabilities

### New Capabilities

- 無。

### Modified Capabilities

- `monitor-high-priority-scheduling`: 移除高優先級監控啟動流程中的電池最佳化豁免入口要求，保留 exact alarm、前台服務、ongoing notification 和可用 alarm fallback。
- `notification-bar-monitoring`: 啟動面板新增鎖屏通知內容與語音播報行為提示，讓用戶在開始監控前理解本次 session 的鎖屏可見與語音影響。

## Impact

- 受影響代碼：
  - `app/src/main/AndroidManifest.xml`: 移除 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`。
  - `app/src/main/java/com/example/busiscoming/ui/main/MainActivity.kt`: 移除監控啟動時的電池最佳化豁免檢查與系統頁跳轉；保留 exact alarm special access 引導。
  - `app/src/main/java/com/example/busiscoming/service/BusMonitorSchedulingCapability.kt`: 移除或收斂電池豁免 intent/helper，避免生產路徑再直接請求豁免。
  - `app/src/main/java/com/example/busiscoming/ui/main/MonitorSettingsBottomSheet.kt`: 補充啟動前提示文案。
- 受影響測試：
  - 更新監控通知與調度契約測試，不再期望 manifest 含 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`。
  - 補充或更新啟動面板測試/契約，確認提示文案存在，且 `語音播報` 開關、`開始監控`、步行時間設定不受影響。
- 兼容性與驗收：
  - Android 12+ exact alarm special access 行為保持不變。
  - Android 14+/15+ 前台服務類型與 timeout 清理保持不變。
  - 電池豁免移除後，深度 Doze 或廠商省電策略下仍可能導致刷新延遲；既有「每分鐘嘗試更新」與資料延遲語義保持不變。
  - 需要在模擬器或實機驗證：啟動面板提示、通知權限流程、exact alarm 引導仍可用、監控啟動、鎖屏通知、狀態切換語音、手動刷新與停止。
