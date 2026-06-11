## Context

通知欄監控目前已有 `語音播報` 開關和 `試聽語音` 按鈕。最新實測確認 `試聽語音` 已能正常播放，因此設備 TTS 引擎、中文語音包和前台播放能力不是當前主要問題。

新的問題是：開啟通知欄監控後，通知欄與鎖屏內容可以每分鐘更新，但監控狀態變化時沒有按預期語音播報。這條鏈路與試聽不同：

```text
前台試聽
  用戶點擊按鈕
  → PREVIEW audio mode
  → TTS 播放
  → UI 顯示結果

後台監控播報
  ETA 每分鐘刷新
  → 計算狀態
  → 判斷 lastSpokenStatus 是否需要播
  → MONITOR audio mode
  → 鎖屏/後台音頻策略
  → 決定是否記為已播報
```

因此，試聽正常只能證明基礎 TTS 能力可用，不能證明後台監控播報觸發條件、音頻焦點、音量策略或已播報狀態管理正確。

## Goals / Non-Goals

**Goals:**
- 移除監控啟動面板中的 `試聽語音` 按鈕，讓頁面只保留必要配置。
- 保留 `語音播報` 開關且默認開啟。
- 讓開發者能從服務端日誌判斷：狀態是否變化、是否應播報、是否調用 TTS、是否拿到 audio focus、TTS 是否開始/完成/失敗。
- 讓後台狀態切換播報使用更適合語音提醒的音頻策略，而不是完全依賴通知事件音頻用途。
- 播報失敗時不影響通知欄監控、ETA 刷新、鎖屏通知和停止策略。

**Non-Goals:**
- 不替換 Android 系統 TTS 為第三方在線語音服務。
- 不要求 App 下載或內置語音包。
- 不承諾在勿擾、靜音、系統禁用 TTS、強制停止 App 或廠商後台限制下必定出聲。
- 不新增 `試聽語音` 的替代按鈕或更重的語音設定流程。
- 不改變監控狀態閾值、ETA 查詢、步行時間估算或通知欄刷新節奏。

## Decisions

### 1. 啟動面板移除 `試聽語音`

監控設定面板保留核心配置：

```text
步行到站時間
步行速度 / 場景加成
語音播報  [開]
開始監控
```

不再展示：

- `試聽語音` 按鈕
- 試聽診斷提示文案
- 由試聽失敗觸發的 `設定系統語音` 次級按鈕

原因是語音能力已通過實測確認，繼續保留試聽會佔用啟動面板空間，且容易讓用戶誤以為“試聽成功 = 後台一定播報成功”。後台播報問題應在服務端可靠性中解決。

### 2. 保留可診斷的語音結果模型

`BusMonitorSpeechResult` 仍需要能區分：

- `Queued`：TTS 尚未初始化，已排隊等待。
- `Accepted`：`TextToSpeech.speak()` 已接受請求。
- `Started`：TTS 回調表示 utterance 已開始。
- `Completed`：utterance 完成。
- `Stopped`：utterance 被停止。
- `NoEngine`：系統沒有可解析的 TTS 引擎。
- `InitializationFailed`：`TextToSpeech` 初始化狀態不是 `SUCCESS`。
- `LanguageMissingData`：候選中文語言需要安裝資料。
- `LanguageNotSupported`：候選中文語言均不支援。
- `SpeakRejected`：`TextToSpeech.speak()` 回傳錯誤。
- `PlaybackError`：utterance 回調報錯。
- `PlaybackTimeout`：請求被接受但未收到開始回調。
- `Released`：controller 已釋放或不再可用。

這些結果不一定直接展示給用戶，但必須能支撐服務端日誌、測試和後續定位。

### 3. 服務端記錄播報決策，而不只記錄 TTS 結果

每次可用 ETA 刷新後，`BusMonitorService` 應記錄非敏感診斷信息：

```text
route=82X
voiceEnabled=true
firstEtaMinutes=4
walkingMinutes=3
lastStatus=PREPARE
nextStatus=LEAVE_NOW
lastSpokenStatus=PREPARE
shouldSpeak=true
speechText=當前汽車到站剩餘 4 分鐘，請立即出門
```

播放請求階段再記錄：

```text
audioFocus=GRANTED / DENIED
audioUsage=...
speakResult=Accepted / Failure(...)
utterance=Started / Completed / Error / Timeout / Stopped
markSpoken=true / false
```

這能把“沒有聲音”拆成三類：

- 沒有狀態變化或 `shouldSpeak=false`。
- 狀態變化且應播報，但沒有成功提交 TTS。
- TTS 接受或開始處理，但音頻策略或系統環境導致不可聽。

### 4. 後台播報使用短暫 audio focus

監控狀態切換播報不是普通通知提示音，而是一段語音提醒。播放前應向 `AudioManager` 請求短暫 audio focus：

- Android O 及以上使用 `AudioFocusRequest`。
- 低版本使用兼容 API。
- 建議使用 `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` 或等效短暫焦點，避免長時間佔用。
- 如果 audio focus 被拒絕，應記錄失敗並保持通知刷新，不標記該狀態為已播報。
- 播放完成、停止、錯誤或超時後釋放 audio focus。

### 5. 後台播報使用更合適的 audio usage

`USAGE_NOTIFICATION_EVENT` 容易受通知音量、通知渠道、勿擾和 ROM 通知策略影響。後台語音提醒應評估改用更接近“語音提醒/導航提示”的 usage，例如：

- `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`
- 或 `USAGE_ASSISTANT`

最終選型應以實測為準，但實作上不應再把前台試聽成功作為後台通知事件音頻可用的證據。

### 6. 已播報狀態只能代表“成功嘗試播報”

`lastSpokenStatus` 的語義應保持嚴格：

- audio focus 失敗：不更新。
- `speak()` 被拒絕：不更新。
- 播放超時或回調錯誤：不更新。
- 收到 `Started` 或 `Completed`：可以更新。

如果某狀態播報失敗，下一次刷新仍可重試。但為避免故障環境下每分鐘重複嘗試造成干擾，實作可以增加輕量節流，例如同一狀態失敗後至少等待一個刷新週期或記錄失敗原因後再重試。節流不得阻止狀態真正變化後的播報。

### 7. 語音失敗只降級語音，不降級監控

語音播報失敗時，通知欄監控仍需：

- 更新折疊態和展開態通知內容。
- 保持鎖屏可見。
- 安排下一次刷新。
- 保持手動刷新和停止 action 可用。
- 按既有停止策略停止監控。

## Risks / Trade-offs

- [Risk] 不同 Android 版本與 ROM 對 audio focus 和 TTS usage 的處理不一致。→ Mitigation：把 audio focus 結果、usage、speak 結果和回調完整記錄，並用真機/模擬器驗證。
- [Risk] 改用更強的語音 usage 可能比通知事件更打擾。→ Mitigation：只在狀態切換時播報，且保留 `語音播報` 開關。
- [Risk] 移除 `試聽語音` 後，用戶無法在啟動前自查 TTS。→ Mitigation：目前語音能力已實測正常，本變更重點是後台播報；後續若需要可在獨立設定頁提供語音測試，而不是佔用監控啟動面板。
- [Risk] `onStart()` 不等於用戶一定聽到聲音。→ Mitigation：配合 audio focus、音量/勿擾診斷日誌判斷；不把 `Accepted` 當成已播報。
