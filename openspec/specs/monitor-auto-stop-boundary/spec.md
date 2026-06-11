# monitor-auto-stop-boundary Specification

## Purpose
TBD - created by archiving change strengthen-monitor-scheduling-and-auto-stop. Update Purpose after archive.
## Requirements
### Requirement: 固定單次監控停止目標
系統 SHALL 為每次通知欄監控 session 固定一個停止目標，並禁止後續 ETA 刷新把停止目標滾動延後。

#### Scenario: 第 2 班 ETA 可用時固定停止目標
- **WHEN** 監控 session 首次成功取得 ETA
- **AND** ETA 結果包含第 2 班車到站時間
- **THEN** 系統 SHALL 將本次監控停止目標固定為該第 2 班車到站時間
- **AND** 系統 SHALL 持久化停止目標與停止來源

#### Scenario: 缺少第 2 班 ETA 時使用 fallback 停止目標
- **WHEN** 監控 session 首次成功取得 ETA
- **AND** ETA 結果只有第 1 班車到站時間
- **THEN** 系統 SHALL 將本次監控停止目標固定為第 1 班車到站時間後 `2 分鐘`
- **AND** 系統 SHALL 持久化停止目標與 fallback 停止來源

#### Scenario: 後續刷新不得延後停止目標
- **WHEN** 監控 session 已有固定停止目標
- **AND** 後續刷新取得新的第 2 班 ETA 或新的 fallback 時間
- **THEN** 系統 SHALL 保留既有停止目標
- **AND** 系統 SHALL NOT 將本次監控停止時間滾動延後

#### Scenario: 無可用 ETA 時暫不建立停止目標
- **WHEN** 監控 session 尚未成功取得任何可用 ETA
- **THEN** 系統 SHALL NOT 建立基於 ETA 的停止目標
- **AND** 系統 SHALL 依連續失敗保護與最大 session 時長限制避免長時間殘留

### Requirement: 獨立調度自動停止
系統 SHALL 在取得停止目標後安排獨立停止 alarm，讓通知欄與鎖屏通知能在停止時間到達後退出，而不依賴下一次 ETA 刷新。

#### Scenario: 建立停止目標後安排 stop alarm
- **WHEN** 系統為監控 session 建立停止目標
- **THEN** 系統 SHALL 安排一個指向自動停止 action 的 stop alarm
- **AND** stop alarm SHALL 優先使用 exact idle-aware alarm

#### Scenario: stop alarm 到點觸發
- **WHEN** stop alarm 到點觸發
- **THEN** 系統 SHALL 停止前台服務
- **AND** 系統 SHALL 移除通知欄通知與鎖屏通知
- **AND** 系統 SHALL NOT 因自動停止 action 發起新的 ETA 刷新

#### Scenario: 刷新被延遲但停止時間已到
- **WHEN** 系統延遲了下一次 ETA 刷新
- **AND** 固定停止目標已經到達
- **THEN** 系統 SHALL 仍可透過獨立 stop alarm 停止本次監控
- **AND** 系統 SHALL 清理 session 與監控通知

#### Scenario: stop alarm fallback
- **WHEN** exact alarm 不可用或未獲授權
- **THEN** 系統 SHALL 使用可用的 best-effort alarm 安排 stop alarm
- **AND** 系統 SHALL 在任何服務恢復入口再次檢查停止目標是否已到達

### Requirement: 服務恢復時補償停止條件
系統 SHALL 在監控服務恢復、刷新 action、手動刷新或 App 重新打開時先檢查固定停止目標，避免過期通知殘留。

#### Scenario: 恢復時停止目標已過
- **WHEN** 監控服務恢復既有 session
- **AND** 當前時間已經到達或超過固定停止目標
- **THEN** 系統 SHALL 立即停止監控
- **AND** 系統 SHALL 移除通知欄通知與鎖屏通知

#### Scenario: 恢復時停止目標未到
- **WHEN** 監控服務恢復既有 session
- **AND** 當前時間尚未到達固定停止目標
- **THEN** 系統 SHALL 重新安排 stop alarm
- **AND** 系統 SHALL 繼續安排下一次刷新

#### Scenario: 刷新與停止競態
- **WHEN** 刷新 action 和自動停止 action 在接近時間觸發
- **THEN** 系統 SHALL 優先尊重已到達的固定停止目標
- **AND** 系統 SHALL 停止監控而不是繼續刷新

### Requirement: 停止時完整釋放監控資源
系統 SHALL 在任何停止路徑中完整釋放通知欄監控資源，避免通知、alarm 或 wake lock 殘留。

#### Scenario: 手動停止
- **WHEN** 用戶點擊通知中的 `停止`
- **THEN** 系統 SHALL 清理監控 session
- **AND** 系統 SHALL 取消刷新 alarm、停止 alarm 和 handler tick
- **AND** 系統 SHALL 釋放 wake lock 與 TTS 資源
- **AND** 系統 SHALL 移除通知欄通知與鎖屏通知

#### Scenario: 自動停止
- **WHEN** 固定停止目標到達並觸發自動停止
- **THEN** 系統 SHALL 清理監控 session
- **AND** 系統 SHALL 取消刷新 alarm、停止 alarm 和 handler tick
- **AND** 系統 SHALL 釋放 wake lock 與 TTS 資源
- **AND** 系統 SHALL 移除通知欄通知與鎖屏通知

#### Scenario: 連續失敗保護停止
- **WHEN** 監控服務連續刷新失敗並達到保護上限
- **THEN** 系統 SHALL 停止本次監控
- **AND** 系統 SHALL 取消刷新 alarm、停止 alarm 和 handler tick
- **AND** 系統 SHALL 釋放 wake lock 與 TTS 資源
- **AND** 系統 SHALL 移除通知欄通知與鎖屏通知

