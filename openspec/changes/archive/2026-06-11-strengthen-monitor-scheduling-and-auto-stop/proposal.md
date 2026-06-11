## Why

用戶手動啟動通知欄監控後，候車時間通常只有數分鐘，通知刷新與語音播報需要盡可能準時；現有 `Handler.postDelayed` + `setAndAllowWhileIdle` 只能提供 best-effort 調度，退後台、鎖屏或省電場景下仍可能延遲或失效。

同時，通知欄與鎖屏通知的自動停止依賴刷新流程順手檢查，沒有獨立的到站停止喚醒；當系統延遲刷新或成功刷新持續覆寫第 2 班 ETA 時，通知可能沒有在原本預期的第 2 班車到站後退出。

## What Changes

- 將監控刷新從普通 idle-aware alarm 優先升級為 exact alarm：有權限時使用 `setExactAndAllowWhileIdle()`，缺少權限時引導用戶授權，授權不可用或被拒絕時才 fallback 到既有 best-effort alarm。
- 在用戶手動啟動的監控 session 期間引入受控 `PARTIAL_WAKE_LOCK`，提升退後台與鎖屏期間刷新、通知更新和語音播報被執行的概率。
- 啟動監控時提供電池最佳化豁免入口，讓用戶可以為短時候車監控提高系統保活優先級。
- 補齊 Android 15+ 前台服務超時處理，確保系統限制觸發時能清理 session、停止通知並釋放資源。
- 將自動停止從“刷新時順手檢查”升級為“停止目標獨立調度”：取得停止目標後安排獨立 stop alarm，時間到後即使沒有新的 ETA 刷新也應停止前台服務並移除通知。
- 固定單次監控的停止目標：有第 2 班 ETA 時以本次監控首次取得的第 2 班 ETA 作為停止邊界；缺少第 2 班 ETA 時以首次取得的第 1 班 ETA `+2 分鐘` 作為 fallback 停止邊界。後續刷新可以更新通知內容，但不得把本次監控的停止邊界滾動延後。
- 保留手動停止、刷新 action、最大 session 時長和連續失敗保護；停止時必須取消刷新 alarm、停止 alarm、handler tick、wake lock、TTS 和前台通知。

## Capabilities

### New Capabilities
- `monitor-high-priority-scheduling`: 定義通知欄監控在用戶手動啟動後使用 exact alarm、wake lock、電池最佳化豁免和前台服務超時處理來提高背景刷新可靠性。
- `monitor-auto-stop-boundary`: 定義通知欄與鎖屏監控通知的停止目標、獨立停止調度、停止邊界固定規則和完整資源釋放。

### Modified Capabilities
- 無。

## Impact

- 影響 Android 權限與 manifest：可能新增 `SCHEDULE_EXACT_ALARM`、`WAKE_LOCK`，並處理電池最佳化豁免設定入口。
- 影響監控服務：`BusMonitorService` 需要管理高優先級 session 生命週期、wake lock、Android 15 `onTimeout()`、停止 action 和自動停止 action。
- 影響調度器：`BusMonitorRefreshScheduler` 需要支持 exact refresh alarm、fallback refresh alarm、獨立 stop alarm、取消和權限檢查。
- 影響 session 資料：`BusMonitorSessionSnapshot` 需要持久化固定停止目標、停止目標來源與必要的調度狀態，避免服務重建後丟失停止邊界。
- 影響 UI/權限入口：監控啟動前或啟動流程中需要提供 exact alarm 與電池最佳化豁免的授權入口；不新增刷新頻率、停止條件等工程化設定。
- 影響測試與驗證：需要覆蓋 exact alarm 選擇、fallback、wake lock 釋放、自動停止目標固定、服務恢復後過期清理、Android 15 timeout、退後台/鎖屏/Doze 場景驗證。
