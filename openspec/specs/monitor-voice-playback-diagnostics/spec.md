# monitor-voice-playback-diagnostics Specification

## Purpose
TBD - created by archiving change fix-monitor-voice-playback-diagnostics. Update Purpose after archive.
## Requirements

### Requirement: 診斷語音播放能力
系統 SHALL 在使用通知欄監控語音播報前檢查 Android TextToSpeech 能力，並保留可區分的診斷結果。

#### Scenario: 系統沒有可用 TTS 引擎
- **WHEN** 設備無法解析或初始化任何 TextToSpeech 引擎
- **THEN** 系統 SHALL 將語音能力標記為不可用
- **AND** 診斷原因 SHALL 表示系統沒有可用語音引擎

#### Scenario: TTS 初始化失敗
- **WHEN** TextToSpeech 初始化回調狀態不是成功
- **THEN** 系統 SHALL 將語音能力標記為不可用
- **AND** 診斷原因 SHALL 表示語音引擎初始化失敗

#### Scenario: TTS 控制器已釋放
- **WHEN** 語音控制器已釋放後仍收到播放請求
- **THEN** 系統 SHALL 拒絕該播放請求
- **AND** 診斷原因 SHALL 表示語音控制器已不可用

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

### Requirement: 記錄狀態切換播報決策
系統 SHALL 在每次可用 ETA 刷新後記錄足以判斷是否應播報的非敏感診斷資訊。

#### Scenario: 狀態未變化
- **WHEN** ETA 刷新後計算出的監控狀態與上一已播報狀態相同
- **THEN** 系統 SHALL 記錄當前 ETA、步行時間、上一狀態、新狀態和 `shouldSpeak=false`
- **AND** 系統 SHALL NOT 調用 TTS 播放

#### Scenario: 狀態變化且語音開啟
- **WHEN** ETA 刷新後監控狀態發生變化
- **AND** 語音播報開關已開啟
- **THEN** 系統 SHALL 記錄當前 ETA、步行時間、上一狀態、新狀態、上一已播報狀態和 `shouldSpeak=true`
- **AND** 系統 SHALL 嘗試進入後台語音播報流程

#### Scenario: 語音開關關閉
- **WHEN** ETA 刷新後監控狀態發生變化
- **AND** 語音播報開關已關閉
- **THEN** 系統 SHALL 記錄 `voiceEnabled=false`
- **AND** 系統 SHALL NOT 調用 TTS 播放

### Requirement: 後台播報使用音頻焦點
系統 SHALL 在通知欄監控狀態切換播報前請求短暫 audio focus，並在播報結束或失敗後釋放。

#### Scenario: 音頻焦點獲取成功
- **WHEN** 監控狀態切換且系統準備語音播報
- **AND** audio focus 請求成功
- **THEN** 系統 SHALL 提交 TextToSpeech 播放請求
- **AND** 系統 SHALL 記錄 audio focus 成功

#### Scenario: 音頻焦點獲取失敗
- **WHEN** 監控狀態切換且系統準備語音播報
- **AND** audio focus 請求失敗
- **THEN** 系統 SHALL 記錄 audio focus 失敗
- **AND** 系統 SHALL NOT 將該狀態記錄為已成功播報
- **AND** 系統 SHALL 繼續更新通知和安排後續 ETA 刷新

#### Scenario: 播報結束釋放音頻焦點
- **WHEN** 本次語音播報完成、停止、錯誤或超時
- **THEN** 系統 SHALL 釋放本次播報持有的 audio focus

### Requirement: 後台播報使用語音提醒音頻用途
系統 SHALL 為通知欄監控狀態切換播報使用適合語音提醒的 audio usage，避免完全依賴通知事件音頻用途。

#### Scenario: 提交後台語音播報
- **WHEN** 系統提交通知欄監控狀態切換語音播報
- **THEN** 系統 SHALL 使用適合語音提醒、導航提示或輔助提示的 audio usage
- **AND** 系統 SHALL 記錄本次使用的 audio usage

#### Scenario: 系統策略限制播報
- **WHEN** 系統因勿擾、靜音、音量或音頻策略限制播報
- **THEN** 系統 SHALL 保持通知欄監控運作
- **AND** 系統 SHALL NOT 將限制情況誤判為通知欄監控失敗

### Requirement: 語音失敗不影響通知欄監控
系統 SHALL 在語音播放不可用或播放失敗時保持通知欄監控核心功能正常運作。

#### Scenario: 開始監控時語音不可用
- **WHEN** 用戶開始通知欄監控
- **AND** 語音播報開關已開啟但語音能力不可用
- **THEN** 系統 SHALL 繼續啟動通知欄監控
- **AND** 系統 SHALL 更新通知並繼續嘗試刷新 ETA

#### Scenario: 狀態切換播報失敗
- **WHEN** 監控狀態切換且系統嘗試語音播報
- **AND** 本次播報失敗
- **THEN** 系統 SHALL 繼續更新通知內容
- **AND** 系統 SHALL 繼續安排後續 ETA 刷新
- **AND** 系統 SHALL NOT 將該狀態記錄為已成功播報

#### Scenario: 狀態切換播報開始
- **WHEN** 監控狀態切換且本次語音播報已開始
- **THEN** 系統 MAY 將該狀態記錄為已播報
- **AND** 後續相同狀態 SHALL NOT 重複播報

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

#### Scenario: 選擇語音語言
- **WHEN** 系統嘗試設定候選語言
- **THEN** 系統 SHALL 記錄候選語言和原始設定結果
- **AND** 系統 SHALL 記錄最終選中的語言或不可用原因

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
