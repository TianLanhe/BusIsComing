## Context

`BusMonitorService` 目前在服務建立時建立 `bus_monitor_status_v2` 與 `bus_monitor_alert_v2`，並在通知本體及 public version 設置公開鎖屏可見性；`MainActivity.startMonitor()` 只檢查 `POST_NOTIFICATIONS`，沒有讀取 App 通知總開關或 channel 經用戶修改後的實際狀態。Android 8+ channel 建立後，重要性與部分展示行為由系統及用戶控制，重複呼叫 `createNotificationChannel()` 無法修復已停用或被降低的 channel。

高優先級調度目前保留 `SCHEDULE_EXACT_ALARM`、`setExactAndAllowWhileIdle()` fallback、受控 `PARTIAL_WAKE_LOCK` 與 `dataSync` 前台服務，但 `f9350cb` 移除了 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`、`PowerManager.isIgnoringBatteryOptimizations()` 檢查及 `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 直接系統確認頁。用戶已確認恢復這一段修改前行為，同時保留其後的多語言、TTS、通知與自動停止改進。

## Goals / Non-Goals

**Goals:**

- 在啟動監控前辨識 App 通知整體停用、普通監控 channel 停用、緊急 channel 異常及可確定的鎖屏隱藏狀態。
- 對具體 channel 問題直達對應系統頁，並提供 App 通知設定、App 詳情與本地化文字的逐級回退。
- 從系統頁返回後重新查詢真實狀態，避免依賴不可靠的 Activity result code。
- 恢復直接電池最佳化豁免權限、狀態檢查、說明與系統確認頁；拒絕或不可用時保留既有 best-effort 監控。
- 保持純狀態判斷與 Intent 導航可單元測試，Activity 只協調畫面、pending start 與生命週期。

**Non-Goals:**

- 不遷移或提高 `v2` channel 的 App 預設 importance；本變更處理用戶／系統實際設定，不重定義通知打擾程度。
- 不嘗試讀取 Android 未公開的全局鎖屏通知開關或保證所有 OEM 的最終鎖屏呈現。
- 不加入 MIUI、ColorOS、EMUI 等非公開電池設定 Intent。
- 不改動 ETA 查詢、狀態門檻、刷新頻率、通知內容、TTS 音頻屬性或停止邊界。

## Decisions

### 1. 以獨立 channel manager 統一建立、檢查與狀態模型

新增 service 層的 channel manager 或等效小型元件，集中承擔：

- 以目前 App 語言確保兩個 `v2` channel 已建立；
- 讀取 `NotificationManager.areNotificationsEnabled()`；
- Android 8+ 讀取兩個 channel 的 `importance` 與 `lockscreenVisibility`；
- 將平台值映射為不依賴 Android framework 物件的純狀態模型，供 UI 與單元測試使用。

狀態分級如下：

- `BLOCKING`：App 通知總開關關閉、普通監控 channel 缺失或 `IMPORTANCE_NONE`。監控依賴可見的 ongoing notification，因此不得啟動。
- `WARNING`：緊急提醒 channel 缺失／停用／低於預期重要性，或任一 channel 明確為 `VISIBILITY_SECRET`。用戶仍可啟動基本監控，但面板需提供修復入口。
- `READY`：沒有發現公開 API 可確定的異常。
- `UNKNOWN`：舊 Android 或 OEM／全局鎖屏策略無法由 App 確認；不得假裝已保證鎖屏顯示。

channel 不存在時先建立再查詢；建立後普通 channel 仍不存在視為 blocking，緊急 channel 仍不存在視為 warning。Android 13+ runtime permission 仍沿用既有授權流程，狀態檢查不取代權限請求。

替代方案是只檢查 `POST_NOTIFICATIONS`。它無法辨識單一 channel 被停用，正是目前缺口，因此不採用。另一方案是建立 `v3` channel 重置設定；這會繞過用戶既有選擇並新增通知分類，超出本次已確認範圍。

### 2. 通知設定 navigator 採用可解析、可捕獲的逐級回退

新增可注入 resolver／starter 的 navigator，沿用 `XiaomiShortcutPermissionNavigator` 的安全模式：

1. Android 8+ 且已知具體異常 channel：`ACTION_CHANNEL_NOTIFICATION_SETTINGS` + package + channel id；
2. `ACTION_APP_NOTIFICATION_SETTINGS` + package；
3. `ACTION_APPLICATION_DETAILS_SETTINGS` + `package:` URI；
4. 全部不可解析或啟動拋出 `ActivityNotFoundException`、`SecurityException`／其他 runtime exception：返回 `MANUAL_GUIDANCE`，由 UI 顯示本地化操作路徑。

若 App 通知整體停用，優先進入 App 通知設定；若普通與緊急 channel 同時異常，blocking 的普通 channel 優先。Settings Activity 返回結果不代表用戶已修改，Activity／面板在 `onResume` 或 launcher callback 中重新查詢並渲染。

替代方案是直接呼叫單一 settings Intent。部分 ROM 不提供或拒絕該 Activity，會把可恢復問題變成崩潰或死路，因此不採用。

### 3. 啟動面板展示準備狀態，blocking 問題在開始時攔截

`MonitorSettingsBottomSheet` 增加小型「監控準備」區，使用目前設計語言展示通知／鎖屏狀態與可操作入口：

```text
監控準備
通知與鎖屏    已就緒／需要設定／請確認    [前往設定]
```

區塊只承載狀態與設定操作，不新增工程化開關。按鈕保持至少 48dp 觸控目標、三語可換行且具內容描述。面板每次顯示及從設定返回時刷新狀態。

用戶點擊開始時：

- blocking：保存既有 `PendingMonitorStart`，打開最具體設定頁；返回後重新檢查，修復成功才續辦，仍 blocking 則留在面板並提示；
- warning／unknown：允許啟動，狀態區保留可選設定入口；
- ready：繼續既有權限與高優先級調度流程。

替代方案是每次遇到 warning 都強制跳轉。這會讓 OEM 無法確認或用戶主動保留隱私設定時無法使用基本監控，因此只對無可見 ongoing notification 的 blocking 狀態強制攔截。

### 4. 直接恢復 `f9350cb` 移除的電池豁免能力

恢復以下能力而不回退整個提交：

- Manifest 重新宣告 `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`；
- `BusMonitorSchedulingCapability` 恢復 `PowerManager.isIgnoringBatteryOptimizations(packageName)` 檢查；
- 恢復以 `package:<applicationId>` 建立 `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` Intent；
- 啟動監控時，若 Android 6+ 尚未豁免，先以目前 App 語言說明「提高鎖屏刷新與語音及時性，但可能增加耗電」，再進入系統確認頁；
- 已豁免時不重複提示；系統頁不可解析、啟動失敗或用戶拒絕時，不中止監控，沿用 exact alarm／fallback alarm、前台服務與 WakeLock。

為避免同時堆疊系統頁，啟動協調按「通知權限與 channel → exact alarm → 電池豁免 → 啟動服務」順序一次處理一個步驟。`PendingMonitorStart` 保存本次路線、步行分鐘及語音選擇；從系統頁返回後重新讀取能力。每個步驟在單次啟動嘗試中最多展示一次，拒絕後繼續降級啟動，下一個全新監控 session 可再次提示。

替代方案一是完整 `git revert f9350cb`；它會與後續包名、多語言、規格和測試變更衝突，且回退無關內容，因此不採用。替代方案二是只開啟通用電池設定列表；用戶已明確選擇恢復直接修改前行為，因此不採用。

### 5. 測試以純 policy 與 navigator 契約為主

先寫失敗測試，再實作：

- channel policy：App 關閉、普通 channel 缺失／停用、緊急 channel 停用／降級、鎖屏 secret、ready／unknown；
- notification navigator：具體 channel、App 通知、App 詳情、解析失敗與啟動例外回退；
- launch coordinator：blocking 暫停、返回重查、exact alarm 與電池豁免順序、單次拒絕不循環；
- battery capability：版本判斷、已豁免、未豁免、正確 package URI；
- manifest／三語資源／啟動面板契約。

完整 `./gradlew build` 覆蓋編譯、單測、lint 與 assemble。人工驗證需使用本任務新啟動或已由本任務擁有的模擬器／實機，測試後關閉任務啟動的模擬器。

## Risks / Trade-offs

- [Risk] `getLockscreenVisibility()` 正常仍可能被全局鎖屏或 OEM 設定覆蓋。→ Mitigation：只將公開 API 明確異常標為問題，正常狀態文案使用「未發現問題」而非保證，保留設定入口。
- [Risk] 直接電池豁免增加耗電、權限敏感度與 Google Play 審核風險。→ Mitigation：只在用戶主動啟動短時監控時、尚未豁免時請求；先說明用途與耗電影響，拒絕可繼續，session 停止時仍完整釋放資源。
- [Risk] 多個系統設定頁造成重複跳轉或 `onResume` 循環。→ Mitigation：單次啟動 coordinator 記錄已嘗試步驟，每次只開一頁，返回後重新查詢並最多嘗試一次。
- [Risk] 普通 channel blocking 時中止啟動改變既有「點擊即開始」行為。→ Mitigation：保存 pending start，修復後自動續辦；無法跳轉時展示可操作的手動路徑。
- [Risk] 面板增加狀態行後小屏／大字體擁擠。→ Mitigation：使用可換行文字、最小觸控尺寸和既有 scroll container，驗證 360dp、font scale 1.0／1.3／2.0 與明暗模式。

## Migration Plan

1. 先增加純 policy、navigator 與 coordinator 的失敗測試。
2. 抽取 channel 建立／檢查元件，讓服務與啟動 UI 共用同一 channel 定義。
3. 加入 notification settings navigator、面板狀態行與返回重查。
4. 恢復電池豁免 manifest 權限、能力 helper、三語說明及啟動順序。
5. 運行窄單測與 `./gradlew build`，再做 task-owned 裝置驗證。
6. 若直接豁免造成無法接受的政策或耗電影響，可只反向移除 manifest 權限與直接請求步驟；channel 診斷與設定導航保持獨立可用。

## Open Questions

無。channel warning 保持非阻塞、普通 channel blocking 阻止啟動，以及直接恢復電池豁免均已由用戶確認。
