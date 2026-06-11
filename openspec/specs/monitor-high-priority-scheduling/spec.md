# monitor-high-priority-scheduling Specification

## Purpose
TBD - created by archiving change strengthen-monitor-scheduling-and-auto-stop. Update Purpose after archive.
## Requirements
### Requirement: 優先使用 exact alarm 調度刷新
系統 SHALL 在用戶手動啟動通知欄監控後，優先使用 exact alarm 安排分鐘級刷新，並在 exact alarm 不可用時降級到 best-effort alarm。

#### Scenario: exact alarm 權限可用
- **WHEN** 用戶啟動通知欄監控
- **AND** 系統允許 App 安排 exact alarm
- **THEN** 系統 SHALL 使用 `setExactAndAllowWhileIdle()` 安排下一次監控刷新
- **AND** 系統 SHALL 保留前台服務與 ongoing notification

#### Scenario: Android 12 以下設備
- **WHEN** 用戶在 Android 12 以下設備啟動通知欄監控
- **THEN** 系統 SHALL NOT 要求 `SCHEDULE_EXACT_ALARM` special access
- **AND** 系統 SHALL 優先使用 exact idle-aware alarm 安排下一次刷新

#### Scenario: exact alarm 權限缺失
- **WHEN** 用戶啟動通知欄監控
- **AND** 系統要求 exact alarm special access
- **AND** App 尚未取得安排 exact alarm 的能力
- **THEN** 系統 SHALL 引導用戶前往系統授權頁開啟鬧鐘與提醒能力
- **AND** 系統 SHALL 在授權不可用或被拒絕時降級使用 `setAndAllowWhileIdle()` 安排刷新

#### Scenario: 手動刷新不等待下一次 alarm
- **WHEN** 用戶點擊通知中的 `刷新`
- **THEN** 系統 SHALL 立即嘗試執行一次監控刷新
- **AND** 系統 SHALL 在本次刷新完成或失敗後重新安排下一次刷新 alarm

### Requirement: 監控期間持有受控 wake lock
系統 SHALL 在短時通知欄監控 session 期間持有受控 `PARTIAL_WAKE_LOCK`，以提高退後台與鎖屏期間刷新、通知更新和語音播報被執行的概率。

#### Scenario: 開始監控時取得 wake lock
- **WHEN** 用戶啟動通知欄監控
- **THEN** 系統 SHALL 取得一個與監控 session 綁定的 `PARTIAL_WAKE_LOCK`
- **AND** 該 wake lock SHALL 有硬性 timeout 保護

#### Scenario: 監控停止時釋放 wake lock
- **WHEN** 監控因手動停止、自動停止、連續失敗保護、session 過期或服務銷毀而結束
- **THEN** 系統 SHALL 釋放監控 wake lock
- **AND** 系統 SHALL NOT 在沒有活躍監控 session 時繼續持有 wake lock

#### Scenario: wake lock timeout 保護
- **WHEN** 監控 session 異常未走正常停止流程
- **THEN** 系統 SHALL 依 wake lock timeout 自動釋放 wake lock
- **AND** timeout SHALL NOT 晚於本次監控最大 session 時長或已知停止目標後的保護窗口

### Requirement: 提供電池最佳化豁免入口
系統 SHALL 在高優先級監控啟動流程中提供電池最佳化豁免入口，讓用戶可以提高短時候車監控的後台執行優先級。

#### Scenario: App 尚未豁免電池最佳化
- **WHEN** 用戶啟動通知欄監控
- **AND** 系統尚未對 App 豁免電池最佳化
- **THEN** 系統 SHALL 提供前往電池最佳化豁免設定的入口
- **AND** 系統 SHALL 說明該設定用於提高候車期間通知更新與語音播報可靠性

#### Scenario: 用戶拒絕電池最佳化豁免
- **WHEN** 用戶未授予電池最佳化豁免
- **THEN** 系統 SHALL 仍允許啟動監控
- **AND** 系統 SHALL 保留前台服務、ongoing notification 和可用的 alarm fallback

#### Scenario: App 已豁免電池最佳化
- **WHEN** 用戶啟動通知欄監控
- **AND** 系統已對 App 豁免電池最佳化
- **THEN** 系統 SHALL NOT 重複要求用戶處理電池最佳化豁免

### Requirement: 前台服務 timeout 安全清理
系統 SHALL 在 Android 15+ 前台服務 timeout 或系統要求停止時安全結束監控並釋放資源。

#### Scenario: dataSync 前台服務 timeout
- **WHEN** Android 15+ 系統對監控前台服務觸發 timeout
- **THEN** 系統 SHALL 停止本次監控 session
- **AND** 系統 SHALL 取消刷新 alarm、停止 alarm 和 handler tick
- **AND** 系統 SHALL 釋放 wake lock 與 TTS 資源
- **AND** 系統 SHALL 移除監控通知

#### Scenario: 用戶或系統強制停止 App
- **WHEN** 用戶透過系統能力強制停止 App 或停止前台服務
- **THEN** 系統 SHALL NOT 嘗試繞過該停止行為自動復活監控
- **AND** 下次 App 啟動或服務恢復時系統 SHALL 清理已中斷的監控 session

