## ADDED Requirements

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

## REMOVED Requirements

### Requirement: 不直接請求電池最佳化豁免

**Reason**: 用戶實測鎖屏刷新與語音及時性下降，並明確決定恢復 `f9350cb` 修改前的直接電池最佳化豁免流程。

**Migration**: 重新宣告 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`，恢復豁免狀態檢查與 `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`；拒絕或不可用時仍沿用現有降級監控，不回退 exact alarm、WakeLock、TTS 或其他後續能力。
