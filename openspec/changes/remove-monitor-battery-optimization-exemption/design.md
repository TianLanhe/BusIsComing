## Context

通知欄監控目前由 `BusMonitorService` 以前台服務承載，透過 ongoing notification 展示路線、ETA、步行到站與更新時間，並在狀態切換時由 `BusMonitorSpeechController` 使用 TextToSpeech 播報。`MainActivity.startMonitor()` 會先處理通知權限，再呼叫 `promptHighPriorityMonitorSettingsIfNeeded()`；該流程目前會依序處理 exact alarm special access 與電池最佳化豁免，後者使用 `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 直接跳轉系統豁免頁。

本變更針對 Google Play 上架前的通知權限與背景能力風險收斂：移除直接請求電池最佳化豁免，避免把短時公交 ETA 監控包裝成必須進入電池白名單的能力。同時，現有用戶滿意的效果需要保持不變：通知欄與鎖屏通知仍可見，狀態切換語音在系統允許時仍會於鎖屏或後台播報，exact alarm special access、`dataSync` 前台服務、wake lock、刷新/停止 action 和自動停止策略都不在本次收斂範圍內。

## Goals / Non-Goals

**Goals:**

- 移除 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` manifest 權限與直接豁免請求入口。
- 保留 exact alarm special access 引導、`SCHEDULE_EXACT_ALARM` 權限與既有調度策略。
- 保留現有前台服務、通知欄/鎖屏通知、TTS 狀態切換播報與通知 action。
- 在通知欄監控啟動面板補充自然提示，讓用戶在開始前理解鎖屏會顯示路線與 ETA，開啟語音後鎖屏狀態切換可能播報。
- 更新測試契約，防止未來重新引入電池豁免請求或破壞啟動面板提示。

**Non-Goals:**

- 不移除或降級 exact alarm special access。
- 不改變 `foregroundServiceType="dataSync"` 或 Play Console 前台服務申報策略。
- 不改變 Citybus/DATA.GOV.HK 請求、ETA 解析、路線排序、站點對齊或通知 body 格式。
- 不新增設定頁、全局隱私開關、通知 channel 遷移或語音試聽入口。
- 不承諾 Android Doze 或廠商省電策略下絕對每 60 秒完成網絡刷新；既有「每分鐘嘗試更新」語義保持不變。

## Decisions

### 1. 移除直接電池豁免請求，而不是改成較弱的二次確認

`AndroidManifest.xml` 應移除 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`。`MainActivity.promptHighPriorityMonitorSettingsIfNeeded()` 應保留 exact alarm 檢查與跳轉，但移除 `isIgnoringBatteryOptimizations()` 檢查、`batteryOptimizationSettingsIntent()` 跳轉與「允許忽略電池最佳化」toast。`BusMonitorSchedulingCapability` 中只服務於直接豁免請求的 helper 可刪除，或收斂到不再被生產路徑調用。

替代方案是保留 manifest 權限，但在啟動面板先展示二次說明再跳轉。這仍會把 Play 版包裝為會直接請求電池白名單，不能有效降低審核與用戶信任成本，因此不採用。

### 2. 保留 exact alarm special access 與現有調度契約

本變更只處理電池最佳化豁免。`SCHEDULE_EXACT_ALARM`、`setExactAndAllowWhileIdle()` 優先調度、缺少授權時 fallback 到 `setAndAllowWhileIdle()`、通知 ongoing 前台服務與 wake lock timeout 都應保持。這符合用戶明確要求，也避免把一次權限收斂擴大成監控可靠性重設計。

替代方案是同時移除 exact alarm special access，讓 Play 版完全 best-effort。這會改變鎖屏與進程被回收後的準時性邊界，超出本次需求。

### 3. 啟動面板補一句行為提示，不新增阻塞式確認

`MonitorSettingsBottomSheet` 現有 `limitNote()` 已說明「每分鐘嘗試更新」與省電/鎖屏/網絡限制。應在同一提示區補充一句自然文案，例如：「鎖屏會顯示路線與 ETA；開啟語音後，狀態變更可能在鎖屏時播報。」文案應保持短、自然、非警告式，並與 `語音播報` 開關放在同一決策上下文中。

替代方案是新增 modal 或要求用戶勾選同意。這會增加啟動摩擦，且目前功能是用戶主動啟動、通知持續可見、語音有明確開關，不需要阻塞式確認。

### 4. 以契約測試保護保留項與移除項

現有 `BusMonitorNotificationContractTest` 直接期望 manifest 含 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`，需要改成相反斷言。測試也應確認 `SCHEDULE_EXACT_ALARM`、`WAKE_LOCK`、`FOREGROUND_SERVICE_DATA_SYNC` 和 `foregroundServiceType="dataSync"` 仍存在，以避免本次移除電池豁免時誤傷其他能力。啟動面板文案可用現有字串/源碼契約測試覆蓋，或新增窄範圍 UI 相關測試。

替代方案是只做人工驗證。這無法防止後續再次引入敏感豁免權限，也不能保護用戶指定的 exact alarm/dataSync/TTS/鎖屏通知保留範圍。

## Risks / Trade-offs

- [Risk] 部分設備在深度 Doze 或廠商省電策略下刷新與語音播報可能更容易延遲。→ Mitigation：保留前台服務、ongoing notification、wake lock、exact alarm special access 與資料延遲展示；啟動面板仍使用「每分鐘嘗試更新」語義。
- [Risk] 移除 helper 時誤刪 exact alarm 引導或 wake lock 相關能力。→ Mitigation：實作任務要求先更新契約測試，明確保留 `SCHEDULE_EXACT_ALARM`、`FOREGROUND_SERVICE_DATA_SYNC`、`dataSync` service type 與 exact alarm settings intent。
- [Risk] 新增提示文案導致底部面板在小屏或大字體下擠壓。→ Mitigation：調整提示 TextView 行數/換行或拆成短段，並在模擬器檢查常見字體尺寸與窄屏布局。
- [Risk] 用戶把鎖屏展示與語音播報理解為無法關閉。→ Mitigation：提示與既有 `語音播報` 開關同屏展示，並保留通知停止 action；鎖屏內容仍尊重系統鎖屏通知隱私設定。

## Migration Plan

1. 更新測試期望：先讓測試表達 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 不應再存在，並保護 exact alarm、dataSync 與通知行為。
2. 移除 manifest 權限與電池豁免直接跳轉流程。
3. 更新啟動面板提示文案與布局約束。
4. 運行 `./gradlew testDebugUnitTest` 或相關單測；實作交付前運行 `./gradlew build`。
5. 在模擬器或實機驗證通知權限、監控啟動、exact alarm 引導、鎖屏通知、語音播報、刷新與停止。

## Open Questions

無。本次範圍已由用戶確認：移除電池最佳化豁免；其他通知、調度、前台服務與 TTS 效果保持不變。
