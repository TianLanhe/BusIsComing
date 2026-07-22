## MODIFIED Requirements

### Requirement: 通知欄持續展示監控狀態
系統 SHALL 在用戶開始監控後啟動短時前台服務，並透過目前 App 語言的常駐通知展示候車監控狀態。

#### Scenario: 開始監控
- **WHEN** 用戶在啟動面板點擊目前語言的開始監控操作
- **THEN** 系統 SHALL 啟動前台服務
- **AND** 系統 SHALL 顯示 BusIsComing 常駐通知
- **AND** notification channel 名稱與說明 SHALL 使用目前 App 語言

#### Scenario: 通知展示內容
- **WHEN** 監控服務已有最近一次可用 ETA
- **THEN** 通知 SHALL 以目前 App 語言展示監控狀態、路線號、剩餘候車分鐘、步行到站分鐘和最後更新時間
- **AND** 路線號及用戶保存名稱 SHALL 保持原文

#### Scenario: 通知操作
- **WHEN** 監控通知展示時
- **THEN** 通知 SHALL 以目前 App 語言提供刷新、停止和打開 App 操作

#### Scenario: 每分鐘嘗試更新
- **WHEN** 監控服務處於運行狀態
- **THEN** 系統 SHALL 每分鐘嘗試刷新首程 ETA
- **AND** 系統 SHALL 使用目前語言的「嘗試更新」語義處理系統省電或網絡限制導致的延遲

#### Scenario: 手動刷新
- **WHEN** 用戶點擊通知中的刷新操作
- **THEN** 系統 SHALL 立即嘗試刷新首程 ETA
- **AND** 通知 SHALL 在刷新完成後以目前語言更新候車狀態或展示資料延遲

### Requirement: 狀態切換語音播報
系統 SHALL 在語音提醒開啟時，於監控狀態切換時使用目前 App 語言的 TextToSpeech 文案播報相同狀態語義。

#### Scenario: 默認開啟語音提醒
- **WHEN** 系統打開通知欄監控啟動面板
- **THEN** 目前語言的語音提醒開關 SHALL 默認為開啟

#### Scenario: 關閉語音提醒
- **WHEN** 用戶關閉語音提醒
- **THEN** 系統 SHALL 在本次監控 session 中不播放狀態切換語音
- **AND** 通知欄狀態更新 SHALL 不受影響

#### Scenario: 播報準備出門
- **WHEN** 監控狀態切換為準備出門語義
- **AND** 語音提醒已開啟
- **THEN** 系統 SHALL 以目前 App 語言播報剩餘到站分鐘及做好出門準備的語義

#### Scenario: 播報立即出門
- **WHEN** 監控狀態切換為立即出門語義
- **AND** 語音提醒已開啟
- **THEN** 系統 SHALL 以目前 App 語言播報剩餘到站分鐘及立即出門的語義

#### Scenario: 播報快遲到了
- **WHEN** 監控狀態切換為快遲到了語義
- **AND** 語音提醒已開啟
- **THEN** 系統 SHALL 以目前 App 語言播報剩餘到站分鐘及可能遲到的語義

#### Scenario: 同一狀態不重複播報
- **WHEN** ETA 刷新後監控狀態與上一次已播報狀態相同
- **THEN** 系統 SHALL NOT 重複播報同一狀態文案

## ADDED Requirements

### Requirement: 活動監控立即跟隨語言切換
系統 SHALL 在不停止 monitor session 的前提下令活動通知及後續 TTS 跟隨新的 App 語言。

#### Scenario: 監控期間切換語言
- **WHEN** 前台監控正在運作且用戶切換 App 語言
- **THEN** 系統 SHALL 保留 monitor session、路線、步行設定與刷新週期
- **AND** 系統 SHALL 使用相同 channel id 重新建立本地化 channel metadata
- **AND** 系統 SHALL 立即以新語言更新活動通知

#### Scenario: 切換時正在播放舊語言
- **WHEN** 語言切換時舊語言 utterance 尚未完成
- **THEN** 系統 SHALL 停止該 utterance
- **AND** 後續狀態播報 SHALL 使用新語言及相容 Voice

#### Scenario: 語言切換後 ETA 失敗
- **WHEN** 監控已切換新語言但下一次 ETA 刷新失敗
- **THEN** 系統 SHALL 以新語言保留最後成功資料或展示資料延遲
- **AND** 系統 SHALL NOT 恢復舊語言通知文案
