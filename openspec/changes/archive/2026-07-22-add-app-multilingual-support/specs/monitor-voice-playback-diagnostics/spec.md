## REMOVED Requirements

### Requirement: 提供中文語音 fallback
**Reason**: 既有 requirement 允許繁體中文 fallback 到簡體中文或模糊通用中文，可能以錯誤語系播報，亦無法支援英文 App 語言。

**Migration**: 由新增的「按目前 App 語言選擇相容 Voice」requirement 取代；繁體、簡體及英文只在各自允許的語言體系內 fallback。

## ADDED Requirements

### Requirement: 按目前 App 語言選擇相容 Voice
系統 SHALL 只從與目前 App 語言體系相容的 TextToSpeech Voice 中選擇播報語音。

#### Scenario: 繁體中文選擇 Voice
- **WHEN** 目前 App 語言為繁體中文
- **THEN** 系統 SHALL 只接受粵語、香港中文或明確標記繁體中文的 Voice
- **AND** 系統 SHALL NOT fallback 到簡體中文、普通話簡體或模糊通用 `zh` Voice

#### Scenario: 簡體中文選擇 Voice
- **WHEN** 目前 App 語言為簡體中文
- **THEN** 系統 SHALL 只接受普通話或明確標記簡體中文的 Voice
- **AND** 系統 SHALL NOT fallback 到繁體中文、粵語或模糊通用 `zh` Voice

#### Scenario: 英文選擇 Voice
- **WHEN** 目前 App 語言為英文
- **THEN** 系統 SHALL 只接受 English Voice
- **AND** 系統 SHALL 優先香港或英國英文，再嘗試其他 English 地區

#### Scenario: 沒有相容 Voice
- **WHEN** TTS engine 已初始化但所有目前語言候選 Voice 均不相容或不可用
- **THEN** 系統 SHALL 將語音能力標記為沒有相容 Voice
- **AND** 系統 SHALL NOT 以其他語言播報
- **AND** 系統 SHALL 繼續通知監控

### Requirement: 語音失敗提供原因明確的 Toast
系統 SHALL 將 TTS 不可用或播放失敗映射為具體原因，並在不打斷通知監控的前提下向用戶提供目前語言的明確 Toast。

#### Scenario: 啟動時語音能力不可用
- **WHEN** 用戶開始監控且語音已開啟
- **AND** 系統檢測到沒有 engine、初始化失敗／timeout、語音資料缺失、不支援目前語言或沒有相容 Voice
- **THEN** 系統 SHALL 立即顯示一次說明具體原因及監控仍會繼續的 Toast
- **AND** 系統 SHALL NOT 只顯示通用「目前語言的語音不可用」

#### Scenario: 運行期間播放失敗
- **WHEN** audio focus 被拒絕、`speak()` 被拒絕、播放 callback error、utterance timeout 或控制器已釋放
- **THEN** 系統 SHALL 顯示目前語言且對應具體原因的 Toast
- **AND** 系統 SHALL 繼續 ETA 刷新與通知更新

#### Scenario: 同一 session 重複相同原因
- **WHEN** 同一 monitor session 已提示某一語音失敗原因
- **AND** 相同原因再次發生
- **THEN** 系統 SHALL NOT 再次顯示該原因 Toast

#### Scenario: 新 session 再次發生
- **WHEN** 用戶開始新的 monitor session
- **AND** 某一語音失敗原因再次發生
- **THEN** 系統 SHALL 為該新 session 再提示一次

## MODIFIED Requirements

### Requirement: 監控啟動面板保持語音配置簡潔
系統 SHALL 在通知欄監控啟動面板保留目前語言的語音播報開關，但 SHALL NOT 提供試聽語音或系統語音設定入口。

#### Scenario: 顯示語音播報開關
- **WHEN** 用戶打開通知欄監控啟動面板
- **THEN** 系統 SHALL 以目前 App 語言顯示語音播報開關
- **AND** 該開關 SHALL 默認開啟

#### Scenario: 不顯示試聽語音按鈕
- **WHEN** 用戶打開通知欄監控啟動面板
- **THEN** 系統 SHALL NOT 顯示試聽語音按鈕
- **AND** 系統 SHALL NOT 因移除試聽入口而移除語音播報開關

#### Scenario: 不提供系統語音設定入口
- **WHEN** 用戶查看監控啟動面板或 TTS 失敗 Toast
- **THEN** 系統 SHALL NOT 提供打開系統語音設定的 action 或 item

#### Scenario: 開始監控時保存語音開關
- **WHEN** 用戶點擊開始監控
- **THEN** 系統 SHALL 將語音播報開關狀態寫入監控 session
- **AND** 後台監控服務 SHALL 根據該狀態決定是否嘗試語音播報

### Requirement: 記錄語音診斷日誌
系統 SHALL 記錄足以定位語音不可用原因的非敏感診斷資訊，並區分能力、語言選擇、audio focus、提交及播放階段。

#### Scenario: 初始化語音引擎
- **WHEN** 系統初始化 TextToSpeech
- **THEN** 系統 SHALL 記錄初始化狀態、timeout 與所使用或嘗試使用的 engine package

#### Scenario: 選擇語音語言及 Voice
- **WHEN** 系統嘗試設定候選語言或 Voice
- **THEN** 系統 SHALL 記錄請求語言、候選 locale／Voice、`isLanguageAvailable` 與 `setLanguage` 原始結果
- **AND** 系統 SHALL 記錄最終選中 Voice 或沒有相容 Voice 的原因

#### Scenario: 請求 audio focus
- **WHEN** 系統準備提交語音播放
- **THEN** 系統 SHALL 記錄 audio focus 請求與原始結果

#### Scenario: 播放 utterance
- **WHEN** 系統提交語音播放請求
- **THEN** 系統 SHALL 記錄 `speak()` 返回結果
- **AND** 系統 SHALL 記錄 utterance 開始、完成、停止、timeout 或 error callback 與可用原始錯誤碼
- **AND** 系統 SHALL 記錄 monitor session id 及失敗階段

#### Scenario: 診斷資訊保持非敏感
- **WHEN** 系統記錄任何 TTS 診斷
- **THEN** 日誌 SHALL NOT 包含 API key、完整自訂路線名稱或完整 utterance 文字
