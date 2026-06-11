## ADDED Requirements

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

### Requirement: 提供中文語音 fallback
系統 SHALL 優先使用繁體中文語音，並在首選語音不可用時嘗試其他中文語音 fallback。

#### Scenario: 首選繁體中文可用
- **WHEN** TextToSpeech 支援首選繁體中文語音
- **THEN** 系統 SHALL 使用該語音播放通知欄監控語音
- **AND** 系統 SHALL NOT 繼續切換到較低優先級語音

#### Scenario: 首選語音不可用但其他中文可用
- **WHEN** 首選繁體中文語音不可用
- **AND** 香港中文、台灣中文、粵語香港、簡體中文或通用中文任一 fallback 可用
- **THEN** 系統 SHALL 使用第一個可用 fallback 語音
- **AND** 系統 SHALL 記錄被選中的語言

#### Scenario: 中文語音缺少資料
- **WHEN** 所有候選中文語音的結果均為缺少語音資料或不可用
- **AND** 至少一個候選語音回傳缺少語音資料
- **THEN** 系統 SHALL 將診斷原因標記為缺少中文語音資料
- **AND** 系統 SHALL NOT 阻止用戶開始通知欄監控

#### Scenario: 中文語音不受支援
- **WHEN** 所有候選中文語音均不受支援
- **THEN** 系統 SHALL 將診斷原因標記為中文語音不受支援
- **AND** 系統 SHALL NOT 阻止用戶開始通知欄監控

### Requirement: 監控啟動面板保持語音配置簡潔
系統 SHALL 在通知欄監控啟動面板保留語音播報開關，但 SHALL NOT 提供試聽語音入口。

#### Scenario: 顯示語音播報開關
- **WHEN** 用戶打開通知欄監控啟動面板
- **THEN** 系統 SHALL 顯示 `語音播報` 開關
- **AND** 該開關 SHALL 默認開啟

#### Scenario: 不顯示試聽語音按鈕
- **WHEN** 用戶打開通知欄監控啟動面板
- **THEN** 系統 SHALL NOT 顯示 `試聽語音` 按鈕
- **AND** 系統 SHALL NOT 因為移除試聽入口而移除 `語音播報` 開關

#### Scenario: 開始監控時保存語音開關
- **WHEN** 用戶點擊 `開始監控`
- **THEN** 系統 SHALL 將 `語音播報` 開關狀態寫入監控 session
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
系統 SHALL 記錄足以定位語音不可用原因的非敏感診斷資訊。

#### Scenario: 初始化語音引擎
- **WHEN** 系統初始化 TextToSpeech
- **THEN** 系統 SHALL 記錄初始化狀態
- **AND** 系統 SHALL 記錄所使用或嘗試使用的 TTS 引擎標識

#### Scenario: 選擇語音語言
- **WHEN** 系統嘗試設定候選語言
- **THEN** 系統 SHALL 記錄候選語言和原始設定結果
- **AND** 系統 SHALL 記錄最終選中的語言或不可用原因

#### Scenario: 播放 utterance
- **WHEN** 系統提交語音播放請求
- **THEN** 系統 SHALL 記錄 `speak()` 返回結果
- **AND** 系統 SHALL 記錄 utterance 開始、完成、停止、超時或錯誤回調
