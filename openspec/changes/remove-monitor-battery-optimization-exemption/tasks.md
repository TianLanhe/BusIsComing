## 1. 契約與測試準備

- [x] 1.1 更新 `BusMonitorNotificationContractTest` 或等效契約測試，確認 manifest 不再包含 `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`。
- [x] 1.2 在同一組測試中保留正向斷言，確認 `SCHEDULE_EXACT_ALARM`、`WAKE_LOCK`、`FOREGROUND_SERVICE_DATA_SYNC` 與 `foregroundServiceType="dataSync"` 仍存在。
- [x] 1.3 補充啟動面板契約測試或源碼級測試，確認鎖屏顯示路線與 ETA、開啟語音後鎖屏狀態變更可能播報的提示文案存在。

## 2. 移除電池最佳化豁免請求

- [x] 2.1 從 `app/src/main/AndroidManifest.xml` 移除 `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`，不改動通知、前台服務、wake lock 或 exact alarm 相關權限。
- [x] 2.2 調整 `MainActivity.promptHighPriorityMonitorSettingsIfNeeded()`，保留 exact alarm special access 檢查與跳轉，移除電池最佳化豁免檢查、toast 與 `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 跳轉。
- [x] 2.3 清理 `BusMonitorSchedulingCapability` 中只服務於電池豁免直接請求的 helper，或確認其不再被生產監控啟動流程調用。
- [x] 2.4 搜尋全倉庫，確認不存在生產路徑直接使用 `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 或 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`。

## 3. 啟動面板提示

- [x] 3.1 更新 `MonitorSettingsBottomSheet` 的提示區，加入自然文案說明鎖屏會顯示路線與 ETA，開啟語音後狀態變更可能在鎖屏時播報。
- [x] 3.2 檢查提示文字在底部面板的小屏與大字體條件下不被截斷；必要時調整行數、間距或拆分提示文字。
- [x] 3.3 確認 `語音播報` 開關、`開始監控`、步行分鐘調整、步速預設和場景修正不因提示文案改動而改變行為。

## 4. 回歸驗證

- [x] 4.1 運行通知與調度相關單元測試，至少覆蓋 `BusMonitorNotificationContractTest` 和 `BusMonitorSchedulingPolicyTest`。
- [x] 4.2 運行 `./gradlew testDebugUnitTest`，確認 JVM 單元測試通過。
- [x] 4.3 運行 `./gradlew build`，確認編譯、測試、lint 與 assemble 通過。
- [x] 4.4 使用模擬器或實機驗證監控啟動流程：通知權限、exact alarm special access 引導、啟動面板提示、監控啟動、鎖屏通知、狀態切換語音、刷新與停止。
- [x] 4.5 記錄人工驗證結果；如當前無設備或語音環境不可用，明確記錄未驗證項與剩餘風險。

## 5. 驗證記錄

- 2026-06-30 使用 `Pixel_8_API_36` 模擬器安裝 debug build，授予 `POST_NOTIFICATIONS` 後從既有常用路線執行查詢，點擊結果卡片 `通知欄監控` 入口。
- 啟動面板顯示「鎖屏會顯示路線與 ETA；開啟語音後，狀態變更可能在鎖屏時播報。」提示，`語音播報` 開關、步行時間調整、步速預設、場景修正與 `開始監控` 均保持可用。
- 點擊 `開始監控` 後進入 Android `Alarms & reminders` exact alarm 設定頁，未跳轉電池最佳化豁免頁。
- `dumpsys package com.example.busiscoming` 確認 release manifest 宣告包含 `POST_NOTIFICATIONS`、`FOREGROUND_SERVICE_DATA_SYNC`、`SCHEDULE_EXACT_ALARM`、`WAKE_LOCK`，不包含 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`。
- `dumpsys notification --noredact` 確認 BusIsComing 監控通知 id `8201` 使用 `bus_monitor_status_v2` channel、`actions=2`、`vis=PUBLIC`，標題與正文展示路線、狀態、ETA、步行和更新時間。
- 展開通知欄確認 BusIsComing 通知顯示 `刷新` 和 `停止` action；點擊 `停止` 後通知移除，`dumpsys activity services com.example.busiscoming` 無 `BusMonitorService` 殘留。
- `logcat` 確認狀態切換語音流程完成：TTS 初始化成功、取得 navigation guidance audio focus、utterance started、utterance completed，並釋放 audio focus。
