## ADDED Requirements

### Requirement: 鎖屏可見監控通知
系統 SHALL 在用戶開始通知欄監控後，讓監控通知可在通知欄和鎖屏頁面查看，並展示完整路線與候車狀態內容。

#### Scenario: 開始監控後鎖屏可見
- **WHEN** 用戶啟動通知欄監控且通知權限已授權
- **THEN** 系統 SHALL 發出常駐監控通知
- **AND** 該通知 SHALL 在通知欄可見
- **AND** 在系統鎖屏通知設定允許時，該通知 SHALL 在鎖屏頁面可見

#### Scenario: 鎖屏展示完整內容
- **WHEN** 監控通知在鎖屏頁面可見
- **THEN** 通知 SHALL 展示路線號、監控狀態、剩餘候車分鐘、步行到站分鐘和最後更新時間
- **AND** 系統 SHALL NOT 只展示空白通知或僅展示 App 名稱

#### Scenario: 使用可遷移通知渠道
- **WHEN** 系統建立或更新監控通知渠道
- **THEN** 系統 SHALL 使用能承載鎖屏可見監控通知的新渠道或已符合要求的渠道
- **AND** 系統 SHALL NOT 假設已建立的舊低重要性渠道可被原地升級

#### Scenario: 尊重系統隱私設定
- **WHEN** 用戶或系統關閉鎖屏通知內容展示
- **THEN** 系統 SHALL 尊重該系統設定
- **AND** App SHALL 保持通知欄監控通知在通知欄可查看

### Requirement: 退前台與鎖屏後嘗試刷新 ETA
系統 SHALL 在 App 退前台或設備鎖屏後繼續保持短時監控 session，並在系統允許時每分鐘嘗試刷新首程 ETA。

#### Scenario: App 退前台後繼續監控
- **WHEN** 用戶啟動通知欄監控後按 Home、切換到其他 App 或關閉 Activity
- **THEN** 系統 SHALL 保持監控服務或等效監控調度處於有效狀態
- **AND** 系統 SHALL 繼續嘗試刷新首程 ETA 並更新通知

#### Scenario: 鎖屏後繼續嘗試刷新
- **WHEN** 用戶啟動通知欄監控後鎖屏
- **THEN** 系統 SHALL 在系統允許的調度窗口中繼續嘗試刷新首程 ETA
- **AND** 通知 SHALL 在刷新成功後更新候車狀態和最後更新時間

#### Scenario: 不依賴 Activity 生命週期
- **WHEN** 監控已啟動且 MainActivity 不在前台
- **THEN** 系統 SHALL NOT 依賴 Activity 實例、Activity Handler 或前台 UI 狀態完成下一次刷新

#### Scenario: 不只依賴內存 Handler tick
- **WHEN** 設備進入鎖屏、待機或 App 退前台狀態
- **THEN** 系統 SHALL 使用可由系統調度喚醒的機制安排後續刷新嘗試
- **AND** 系統 SHALL NOT 僅依賴服務內存中的 `Handler.postDelayed` 作為唯一刷新來源

#### Scenario: 系統限制導致延遲
- **WHEN** Android 省電、Doze、網絡不可用或系統調度限制導致刷新延遲
- **THEN** 通知 SHALL 保留最後一次成功資料
- **AND** 通知 SHALL 展示最後更新時間或資料延遲狀態
- **AND** 系統 SHALL 在下一個允許窗口繼續嘗試刷新，除非停止條件已達成

### Requirement: 持久化並恢復監控 session
系統 SHALL 持久化一次通知欄監控 session 的必要資料，使服務重建、進程回收或調度喚醒後仍可恢復監控。

#### Scenario: 開始監控時保存 session
- **WHEN** 用戶開始通知欄監控
- **THEN** 系統 SHALL 保存路線標題、首程 ETA query、步行到站分鐘、語音開關和 session 起始時間
- **AND** 系統 SHALL 保存用於自動停止和狀態切換去重的必要資料

#### Scenario: 刷新成功後保存最新狀態
- **WHEN** 監控服務成功刷新 ETA 並更新通知
- **THEN** 系統 SHALL 保存最後成功通知文案、最後更新時間、當前監控狀態和可用的第 1 班與第 2 班 ETA 時間

#### Scenario: 服務重建後恢復
- **WHEN** 監控服務因系統或調度喚醒重新建立
- **AND** 存在未過期且未達停止條件的監控 session
- **THEN** 系統 SHALL 恢復該 session 並繼續嘗試刷新 ETA

#### Scenario: session 已過停止條件
- **WHEN** 系統恢復監控 session 時發現第 2 班 ETA 已過站、fallback 停止條件已達成或 session 已超過最大監控時長
- **THEN** 系統 SHALL 清理該 session
- **AND** 系統 SHALL 移除或停止監控通知

### Requirement: 語音播報可靠性
系統 SHALL 在語音提醒開啟時檢查 TextToSpeech 可用性，並於監控狀態切換時可靠嘗試播報對應文案。

#### Scenario: 啟動面板提供試聽
- **WHEN** 系統展示通知欄監控啟動面板
- **THEN** 面板 SHALL 提供 `試聽語音` 或等效入口
- **AND** 用戶 SHALL 可在開始監控前確認設備能播放語音提醒

#### Scenario: TTS 語言 fallback
- **WHEN** 系統初始化 TextToSpeech
- **THEN** 系統 SHALL 檢查初始化結果和語言設定結果
- **AND** 若首選繁體中文不可用，系統 SHALL 嘗試香港中文、台灣中文、通用中文或實作定義的中文 fallback

#### Scenario: 語音不可用時降級
- **WHEN** 設備沒有可用 TTS 引擎、語言包不可用或 TTS 初始化失敗
- **THEN** 系統 SHALL 不阻塞通知欄監控啟動和通知更新
- **AND** 系統 SHALL 在啟動面板或監控狀態中向用戶提示語音提醒不可用

#### Scenario: 狀態切換播報
- **WHEN** 監控狀態從上一狀態切換為 `準備出門`、`立即出門` 或 `快遲到了`
- **AND** 語音提醒已開啟
- **AND** TTS 可用
- **THEN** 系統 SHALL 嘗試播報包含當前第 1 班 ETA 分鐘數的對應文案

#### Scenario: 同一狀態不重複播報
- **WHEN** ETA 刷新後監控狀態與上一次已播報狀態相同
- **THEN** 系統 SHALL NOT 重複播放同一狀態語音

#### Scenario: 後台或鎖屏狀態切換
- **WHEN** App 不在前台或設備鎖屏時監控狀態發生切換
- **AND** 語音提醒已開啟
- **AND** TTS 可用且系統音頻策略允許播放
- **THEN** 系統 SHALL 嘗試播放狀態切換語音
- **AND** 播放失敗 SHALL NOT 中斷通知刷新

### Requirement: 系統限制邊界
系統 SHALL 對 Android 系統層面的省電、通知和前台服務限制提供可理解的降級行為，而不是承諾不可達成的精確後台執行。

#### Scenario: 每分鐘嘗試而非絕對準時
- **WHEN** 監控 session 正在運行
- **THEN** 系統 SHALL 以每分鐘為目標安排刷新嘗試
- **AND** 系統 SHALL 允許 Android 省電、Doze 或調度限制造成實際刷新延遲

#### Scenario: 用戶強制停止 App
- **WHEN** 用戶透過系統能力強制停止 App 或停止前台服務
- **THEN** 系統 MAY 無法繼續監控或自動恢復
- **AND** 下一次 App 啟動時系統 SHALL 清理或標記此前 session 已中斷

#### Scenario: 連續刷新失敗
- **WHEN** ETA 刷新因網絡或 API 問題連續失敗
- **THEN** 通知 SHALL 保留最後一次成功 ETA 或展示暫無資料
- **AND** 系統 SHALL 按既有保護上限停止監控或提示資料延遲
