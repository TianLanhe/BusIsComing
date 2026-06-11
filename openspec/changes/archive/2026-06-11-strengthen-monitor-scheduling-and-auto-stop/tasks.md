## 1. 權限與平台能力

- [x] 1.1 在 manifest 補充高優先級監控所需權限，至少包含 `SCHEDULE_EXACT_ALARM` 和 `WAKE_LOCK`；若採用直接豁免請求，補充 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`。
- [x] 1.2 新增平台能力 helper，封裝 Android 版本判斷、`canScheduleExactAlarms()`、exact alarm 授權 intent 和電池最佳化豁免狀態。
- [x] 1.3 在監控啟動流程中加入 exact alarm 權限檢查；缺少權限時引導用戶到系統授權頁，授權不可用或被拒絕時保留 fallback。
- [x] 1.4 在監控啟動流程中加入電池最佳化豁免入口；已豁免時不重複提示，拒絕時不阻塞基本監控啟動。

## 2. Session 停止目標模型

- [x] 2.1 擴展 `BusMonitorSessionSnapshot`，持久化固定停止目標時間、停止來源和必要的調度狀態。
- [x] 2.2 擴展 `BusMonitorSessionSnapshotCodec`，確保服務重建後能恢復停止目標與停止來源。
- [x] 2.3 調整 `BusMonitorSessionPolicy.recordSuccessfulRefresh(...)`，僅在 session 尚無停止目標時建立固定停止目標。
- [x] 2.4 實作第 2 班 ETA 可用時固定停止目標為第 2 班 ETA。
- [x] 2.5 實作缺少第 2 班 ETA 時固定停止目標為第 1 班 ETA `+2 分鐘`。
- [x] 2.6 確保後續刷新不得把既有停止目標滾動延後。

## 3. 高優先級刷新與停止調度器

- [x] 3.1 重構 `BusMonitorRefreshScheduler`，區分 refresh alarm 與 stop alarm 的 request code、action 和取消邏輯。
- [x] 3.2 實作 refresh alarm 優先使用 `setExactAndAllowWhileIdle()`，不可用時 fallback 到 `setAndAllowWhileIdle()`。
- [x] 3.3 實作 stop alarm 調度，根據固定停止目標安排獨立自動停止 action。
- [x] 3.4 確保 stop alarm 優先使用 exact idle-aware alarm，不可用時 fallback 到 best-effort alarm。
- [x] 3.5 確保取消監控時同時取消 refresh alarm 與 stop alarm。

## 4. 前台服務生命週期

- [x] 4.1 在 `BusMonitorService` 中新增 `ACTION_AUTO_STOP`，觸發後只執行停止清理，不發起 ETA 刷新。
- [x] 4.2 在開始監控、刷新完成和服務恢復時，如果存在固定停止目標，安排或補排 stop alarm。
- [x] 4.3 在所有服務入口先檢查 session 是否已過固定停止目標；已過時立即停止並移除通知。
- [x] 4.4 在監控 session 啟動時取得受控 `PARTIAL_WAKE_LOCK`，並設置硬性 timeout。
- [x] 4.5 在手動停止、自動停止、連續失敗停止、session 過期、服務銷毀時釋放 wake lock。
- [x] 4.6 補充 Android 15+ `onTimeout(int, int)` 處理，清理 session、alarm、handler tick、wake lock、TTS 和通知。
- [x] 4.7 確認用戶強制停止 App 或前台服務時不嘗試繞過系統限制自動復活；下次恢復時清理已中斷 session。

## 5. 通知與鎖屏自動退出

- [x] 5.1 確保固定停止目標到達後會停止前台服務並移除通知欄通知。
- [x] 5.2 確保固定停止目標到達後鎖屏 public notification 也會退出。
- [x] 5.3 確保刷新延遲時，獨立 stop alarm 仍可觸發自動停止。
- [x] 5.4 確保刷新 action 與自動停止 action 競態時，以已到達的停止目標為準並停止監控。
- [x] 5.5 保留通知 `刷新`、`停止`、`打開 App` 行為；本變更不修改通知折疊態或展開態文案布局。

## 6. 單元與契約測試

- [x] 6.1 補充 session policy 測試，覆蓋第 2 班 ETA 固定停止目標。
- [x] 6.2 補充 session policy 測試，覆蓋第 1 班 ETA `+2 分鐘` fallback 停止目標。
- [x] 6.3 補充測試，確認後續刷新不會把既有停止目標滾動延後。
- [x] 6.4 補充 scheduler 或 helper 測試，覆蓋 exact alarm 可用、不可用 fallback、refresh alarm 和 stop alarm 分離。
- [x] 6.5 補充 service 契約測試或可驗證 helper 測試，覆蓋自動停止 action 不發起 ETA 刷新且會清理通知/資源。
- [x] 6.6 補充 wake lock 生命週期測試或封裝 helper 測試，覆蓋取得、正常釋放與 timeout 保護。

## 7. 驗證

- [x] 7.1 運行 `openspec validate strengthen-monitor-scheduling-and-auto-stop --strict`。
- [x] 7.2 運行 `./gradlew test`。
- [x] 7.3 運行 `./gradlew build`。
- [x] 7.4 使用模擬器或真機驗證通知監控啟動、exact alarm 權限入口、電池最佳化豁免入口、退後台刷新、鎖屏刷新和手動停止。
- [x] 7.5 使用模擬器或真機驗證第 2 班 ETA 到站後通知欄通知與鎖屏通知自動退出。
- [x] 7.6 使用 `adb shell dumpsys deviceidle force-idle` 或等效方式驗證 Doze 場景下 refresh alarm fallback 與 stop alarm 到點清理行為。
- [x] 7.7 檢查 `git status --short` 和 `git diff --cached --stat`，確認僅包含本 change 實作範圍內的文件。

驗證備註：7.5 以「第 2 班 ETA stop alarm 已排程」加「同 UID 觸發 auto-stop action 後通知與 pending alarm 均清理」作等效驗證，未等待真實 ETA 倒數完成。
