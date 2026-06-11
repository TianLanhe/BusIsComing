## Context

通知欄監控是用戶手動啟動的短時候車輔助功能，核心價值是退後台、鎖屏或切到其他 App 時仍能盡量準時更新候車狀態並在需要出門時播報。現有 `BusMonitorService` 已使用前台服務和 ongoing notification，但刷新調度仍主要依賴 `Handler.postDelayed(60_000)` 與 `AlarmManager.setAndAllowWhileIdle()`；這類調度在背景、省電和 Doze 場景下屬於 best-effort，不能匹配“幾分鐘候車期間盡可能準時提醒”的產品優先級。

自動停止也存在同類耦合問題。當前停止條件只在服務恢復或成功刷新後檢查，沒有為停止時間本身安排獨立喚醒；如果刷新被系統延遲，停止也會延遲。另一方面，成功刷新會持續保存最新的第 2 班 ETA，這會讓原本應該在本次監控第 2 班車到站後停止的邊界被後續刷新滾動推後。

## Goals / Non-Goals

**Goals:**
- 在用戶手動啟動監控後，盡可能提高每分鐘刷新、通知更新和語音播報的系統調度優先級。
- 使用 exact alarm、受控 wake lock 和電池最佳化豁免入口作為高優先級候車監控組合。
- 讓通知欄通知和鎖屏通知在固定停止邊界到達後自動停止，即使沒有新的 ETA 刷新也能退出。
- 固定單次監控的停止目標，避免後續 ETA 刷新把停止時間滾動延後。
- 在手動停止、自動停止、連續失敗、服務銷毀和 Android 15+ 前台服務 timeout 時完整釋放資源。

**Non-Goals:**
- 不修改通知折疊態、展開態或鎖屏通知文案布局。
- 不新增刷新頻率、停止條件或高級調度策略的用戶配置項。
- 不改造 ETA 網絡請求、路線查詢或 TTS 文案語言策略。
- 不嘗試繞過用戶強制停止 App、系統明確禁用通知或廠商 ROM 的不可繞過限制。
- 不使用 WorkManager 作為分鐘級監控刷新方案。

## Decisions

### 1. Refresh alarm 優先使用 exact alarm

`BusMonitorRefreshScheduler` 應抽象出刷新 alarm 調度策略：

- Android 12+ 有 `SCHEDULE_EXACT_ALARM` 能力且 `AlarmManager.canScheduleExactAlarms()` 返回 true 時，使用 `setExactAndAllowWhileIdle()` 安排下一次刷新。
- Android 12+ 缺少 exact alarm 能力時，啟動流程應引導用戶到系統授權頁；授權不可用或用戶拒絕時 fallback 到 `setAndAllowWhileIdle()`。
- Android 12 以下不需要 special exact alarm 授權，仍優先使用 `setExactAndAllowWhileIdle()`。

選擇這個方案是因為當前需求明確優先保障短時候車提醒，普通 inexact alarm 已經不符合預期。沒有選擇 `setAlarmClock()`，因為它屬於鬧鐘語義，會引入系統鬧鐘級 UI 和過度打斷；沒有選擇 WorkManager，因為其週期與 Doze 行為不適合分鐘級強時效場景。

### 2. 監控 session 期間持有受控 partial wake lock

服務在開始監控後取得一個 `PARTIAL_WAKE_LOCK`，用於提高退後台和鎖屏期間服務刷新、通知更新和語音播報實際執行的概率。wake lock 的生命週期與監控 session 綁定，並需要有硬上限：

- 正常停止時立即釋放；
- 服務銷毀、Android 15+ timeout、連續失敗停止時釋放；
- 不晚於 session 最大時長或停止目標後的保護窗口釋放。

這會增加耗電，但本功能是用戶手動啟動的短時監控，且用戶已確認可以接受更高耗電換取提醒可靠性。仍需避免無界持有。

### 3. 提供電池最佳化豁免入口

在啟動監控前或首次啟動高優先級監控時，若系統未對 App 豁免電池最佳化，App 應提供授權入口。若需要直接請求豁免，manifest 需要加入 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`；若不直接彈出請求，也應至少跳轉到電池最佳化設定頁。授權被拒絕時不阻塞監控啟動，但調度器需要 fallback 並保持前台服務。

這個入口對部分省電策略較激進的設備尤其重要。exact alarm 可提高喚醒準確性，但被電池最佳化限制時，網絡、CPU 和 wake lock 行為仍可能受影響。

### 4. 自動停止使用獨立 stop alarm

刷新 alarm 和停止 alarm 應分離。刷新 alarm 仍按 60 秒週期安排；停止 alarm 則在獲得停止目標後安排到具體時間點：

- 使用 `ACTION_AUTO_STOP` 啟動 `BusMonitorService`；
- stop alarm 優先使用 `setExactAndAllowWhileIdle()`，不可用時 fallback 到 `setAndAllowWhileIdle()`；
- 觸發後服務只做停止清理，不重新發起 ETA 刷新。

這樣即使刷新被系統延遲，停止也有自己的喚醒機會，不會依附於下一輪 ETA 返回。

### 5. 單次監控固定停止目標

`BusMonitorSessionSnapshot` 應新增固定停止目標，例如：

- `stopAtMillis: Long?`
- `stopReason: SECOND_ETA | FIRST_ETA_FALLBACK | MAX_SESSION | FAILURE_LIMIT`

首次成功取得 ETA 時，如果 session 尚未有 `stopAtMillis`：

- 有第 2 班 ETA：`stopAtMillis = secondEtaMillis`；
- 沒有第 2 班 ETA 但有第 1 班 ETA：`stopAtMillis = firstEtaMillis + 2 分鐘`。

後續刷新可以更新通知展示的第 1 班與第 2 班 ETA，但不得把 `stopAtMillis` 滾動延後。恢復服務時若 `now >= stopAtMillis`，應立即停止並移除通知；若尚未到達，應重新安排 stop alarm。

### 6. Android 15+ foreground service timeout 做安全清理

目標 SDK 已到 Android 15+ 行為範圍，`dataSync` 前台服務存在系統總時長限制。服務應實作 `onTimeout(int, int)`，在系統通知 timeout 時標記/清理 session、取消 alarm、釋放 wake lock 和 TTS，並停止前台通知。正常候車 session 遠短於 6 小時限制，但這個保護能避免異常狀態殘留。

## Risks / Trade-offs

- [Risk] exact alarm、wake lock 和電池最佳化豁免會增加耗電與權限敏感度。→ Mitigation：只在用戶手動啟動的短時監控 session 中啟用，停止時完整釋放，並保留最大 session 時長。
- [Risk] Android Doze 對 idle alarm 仍有頻率限制，廠商 ROM 也可能額外限制後台行為。→ Mitigation：採用前台服務、exact alarm、wake lock 和電池豁免組合提高概率，但不承諾能繞過用戶強制停止或所有系統政策。
- [Risk] 直接請求忽略電池最佳化可能帶來應用商店審核或用戶信任成本。→ Mitigation：文案限定為用戶手動啟動的候車提醒，拒絕時 fallback，不影響基本查詢功能。
- [Risk] 固定停止目標可能在 ETA 大幅跳變時比最新 ETA 更早停止。→ Mitigation：本次需求優先解決通知無限延後；停止目標只在首次成功 ETA 上建立，避免刷新造成滾動延長。
- [Risk] stop alarm 與 refresh alarm 競態同時觸發。→ Mitigation：`ACTION_AUTO_STOP` 優先清理 session；刷新流程開始前必須檢查 session 是否已過停止條件，已過則立即停止而不是刷新。
