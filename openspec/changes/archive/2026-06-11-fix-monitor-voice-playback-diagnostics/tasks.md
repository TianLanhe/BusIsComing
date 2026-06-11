## 1. 語音結果模型與語言策略

- [x] 1.1 將 `BusMonitorSpeechResult` 從三態 enum 改為可攜帶診斷原因的結果模型，覆蓋初始化中、已接受、已開始、已完成、停止、無引擎、初始化失敗、缺語音資料、不支援語言、播放請求失敗、播放回調錯誤、播放超時和已釋放。
- [x] 1.2 擴充 `BusMonitorTtsLanguagePolicy` 的中文候選順序，保留繁體中文優先，加入香港中文、台灣中文、粵語香港、簡體中文和通用中文 fallback。
- [x] 1.3 讓語言策略保留每個候選語言的原始 `setLanguage()` 結果，並能區分缺少語音資料與語言不支援。
- [x] 1.4 為語音結果模型和語言策略新增單元測試，覆蓋成功、缺資料、不支援、fallback 命中和全部失敗。

## 2. TTS 控制器與後台音頻策略

- [x] 2.1 調整 `BusMonitorSpeechController` 初始化流程，記錄 TTS 初始化狀態、引擎標識和初始化失敗原因。
- [x] 2.2 將 `speak()` 改為能接收播放事件 callback 或等效 listener，回報排隊、接受、開始播放、完成、停止和錯誤。
- [x] 2.3 使用 `UtteranceProgressListener` 追蹤每次 utterance 的 `onStart`、`onDone`、`onStop` 和 `onError`。
- [x] 2.4 讓 `speak()` 的即時返回值只表示請求是否接受；不得把 `Accepted` 當成已成功播報。
- [x] 2.5 為播放請求已接受但未收到 `onStart` 的情況加入保守超時結果。
- [x] 2.6 為後台監控播報新增短暫 audio focus 請求和釋放流程，覆蓋 Android O 及以上與低版本兼容路徑。
- [x] 2.7 將後台監控播報的 audio usage 從純通知事件用途調整為更適合語音提醒、導航提示或輔助提示的用途，並記錄實際 usage。
- [x] 2.8 在 audio focus 失敗、播放請求失敗、播放超時或播放錯誤時返回可診斷失敗結果，且不得標記為已播報。
- [x] 2.9 補充 controller 或契約測試，覆蓋 audio focus 成功、失敗、釋放和後台 monitor audio usage。

## 3. 監控啟動面板語音配置簡化

- [x] 3.1 從 `MonitorSettingsBottomSheet` 移除 `試聽語音` 按鈕。
- [x] 3.2 移除由試聽入口驅動的前台語音診斷提示、`設定系統語音` 次級按鈕和相關點擊流程。
- [x] 3.3 保留 `語音播報` 開關，默認開啟，並保持開始監控時將開關值傳入 `BusMonitorService.startIntent`。
- [x] 3.4 移除 `MainActivity` 中不再使用的 `BusMonitorSpeechPreviewer` wiring；若類本身不再被引用，刪除或收斂對應 class。
- [x] 3.5 更新 UI/契約測試，確認啟動面板不包含 `試聽語音`，但仍包含 `語音播報` 開關。
- [x] 3.6 檢查底部面板移除試聽入口後的間距和小屏幕布局，避免留下空白區或過窄操作區。

## 4. 後台監控播報決策與降級

- [x] 4.1 調整 `BusMonitorService` 播報狀態切換的邏輯，只有播放開始或完成後才更新 `lastSpokenStatus`。
- [x] 4.2 在後台播報失敗時記錄診斷原因，並保持通知更新、ETA 刷新和後續調度不受影響。
- [x] 4.3 保持語音不可用時仍可開始通知欄監控。
- [x] 4.4 確認監控 session 持久化資料不需要新增敏感字段；若需要記錄語音能力狀態，只保存非敏感診斷分類。
- [x] 4.5 在每次可用 ETA 刷新後記錄播報決策日誌：`voiceEnabled`、首班 ETA、步行時間、上一狀態、新狀態、上一已播報狀態和 `shouldSpeak`。
- [x] 4.6 在提交 TTS 前後記錄 audio focus 結果、播放文案、`speak()` 返回值、utterance 回調和是否更新 `lastSpokenStatus`。
- [x] 4.7 確認 audio focus 失敗、播放請求失敗、超時或錯誤時不更新 `lastSpokenStatus`，後續狀態變化仍可再次嘗試播報。
- [x] 4.8 若同一狀態連續播報失敗，增加輕量節流或診斷記錄，避免故障環境下每分鐘無意義重試，同時不阻止真正狀態變化後播報。

## 5. 驗證

- [x] 5.1 運行 `./gradlew build`，確認編譯、單元測試、lint 和 assemble 通過。
- [x] 5.2 重新運行 `./gradlew build`，確認移除試聽入口和後台音頻策略調整後仍通過。
- [x] 5.3 在有 Google TTS 的模擬器或真機上啟動通知欄監控，觸發狀態切換，確認 logcat 出現播報決策、audio focus、播放請求和 utterance 回調日誌。
- [x] 5.4 在 App 退到後台或鎖屏後等待至少一次刷新，確認通知欄/鎖屏仍更新，且狀態變化時可聽到語音播報或看到明確失敗日誌。
- [x] 5.5 使用 contract 測試覆蓋 audio focus 被拒絕時不標記為已播報；使用模擬器驗證後台通知刷新不受正常播報流程影響。
- [x] 5.6 截圖或手動檢查通知欄監控啟動面板，確認 `試聽語音` 已移除，`語音播報` 開關與開始監控操作保持簡潔。
