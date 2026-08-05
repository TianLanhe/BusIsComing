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

### Requirement: 直接請求電池最佳化豁免
系統 SHALL 在用戶主動啟動短時通知欄監控且 App 尚未獲得電池最佳化豁免時，說明用途與耗電影響並直接開啟系統豁免確認頁，同時保留拒絕或不可用時的監控降級。

#### Scenario: manifest 宣告直接豁免權限
- **WHEN** App 生成 Android manifest
- **THEN** manifest SHALL 宣告 `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- **AND** manifest SHALL 保留通知、前台服務、wake lock 與 exact alarm 相關宣告

#### Scenario: 已取得豁免時不重複請求
- **WHEN** 用戶啟動通知欄監控
- **AND** 系統確認 BusIsComing 已忽略電池最佳化
- **THEN** 系統 SHALL NOT 再開啟電池豁免確認頁
- **AND** 系統 SHALL 繼續既有監控啟動流程

#### Scenario: 未取得豁免時直接請求
- **WHEN** 用戶啟動通知欄監控
- **AND** 通知與渠道的 blocking 問題已處理
- **AND** 系統確認 BusIsComing 尚未忽略電池最佳化
- **THEN** 系統 SHALL 以目前 App 語言說明提高鎖屏刷新與語音及時性的用途及可能增加耗電
- **AND** 系統 SHALL 以 `package:<applicationId>` 開啟 `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 系統確認頁

#### Scenario: 多個系統能力依序處理
- **WHEN** 同一次監控啟動同時缺少通知設定、exact alarm special access 或電池最佳化豁免
- **THEN** 系統 SHALL 依通知與渠道、exact alarm、電池豁免順序一次處理一個系統頁
- **AND** 每個能力在單次啟動嘗試中 SHALL 最多提示一次
- **AND** 從系統頁返回後系統 SHALL 重新查詢對應能力而非依賴 result code

#### Scenario: 用戶拒絕電池豁免
- **WHEN** 用戶從系統確認頁返回且 BusIsComing 仍未忽略電池最佳化
- **THEN** 系統 SHALL 不在同一次啟動嘗試中重複打開確認頁
- **AND** 系統 SHALL 允許本次監控使用 exact alarm／best-effort alarm、前台服務與受控 wake lock 繼續啟動
- **AND** 系統 SHALL 保留資料延遲語義

#### Scenario: 電池豁免設定頁不可用
- **WHEN** 系統無法解析、啟動或完成直接電池豁免確認 Intent
- **THEN** 系統 SHALL 顯示目前 App 語言的降級提示
- **AND** 系統 SHALL 不因該失敗中止基本監控
- **AND** App SHALL NOT 因 settings Activity 例外而崩潰
