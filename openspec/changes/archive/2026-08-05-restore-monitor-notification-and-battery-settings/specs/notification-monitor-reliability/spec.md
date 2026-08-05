## ADDED Requirements

### Requirement: 監控通知渠道健康檢查與修復入口
系統 SHALL 在啟動通知欄監控前檢查 App 通知總開關、普通監控渠道與緊急提醒渠道的可觀察設定，並為可修復問題提供最具體的系統設定入口及安全回退。

#### Scenario: 檢查前確保監控渠道存在
- **WHEN** 系統準備展示或執行監控通知渠道健康檢查
- **THEN** 系統 SHALL 先以目前 App 語言確保普通監控與緊急提醒渠道已建立
- **AND** 系統 SHALL 再讀取系統保存的渠道重要性與鎖屏可見性

#### Scenario: App 通知總開關關閉
- **WHEN** 系統確認 App 通知總開關已關閉
- **THEN** 系統 SHALL 將監控通知狀態標記為 blocking
- **AND** 系統 SHALL 優先提供 App 通知設定入口
- **AND** 系統 SHALL NOT 啟動無可見常駐通知的監控服務

#### Scenario: 普通監控渠道停用
- **WHEN** 普通監控渠道建立後仍不存在或重要性為 `IMPORTANCE_NONE`
- **THEN** 系統 SHALL 將監控通知狀態標記為 blocking
- **AND** 系統 SHALL 優先提供該普通監控渠道的設定入口
- **AND** 系統 SHALL NOT 啟動本次監控，直到返回後重新檢查通過

#### Scenario: 緊急提醒渠道異常
- **WHEN** 緊急提醒渠道不存在、停用或重要性低於 App 定義的緊急提醒預期
- **THEN** 系統 SHALL 將監控通知狀態標記為 warning
- **AND** 系統 SHALL 提供該緊急提醒渠道的設定入口
- **AND** 系統 SHALL 允許用戶在理解警告後啟動基本監控

#### Scenario: 渠道明確禁止鎖屏展示
- **WHEN** 任一監控渠道的系統可觀察鎖屏可見性為 `VISIBILITY_SECRET`
- **THEN** 系統 SHALL 顯示該渠道不會在鎖屏展示的 warning
- **AND** 系統 SHALL 提供對應渠道設定入口
- **AND** 系統 SHALL 尊重用戶保留該隱私設定的選擇

#### Scenario: 平台無法確認最終鎖屏呈現
- **WHEN** 公開 Android API 未報告明確異常但全局鎖屏策略或廠商行為無法由 App 確認
- **THEN** 系統 SHALL NOT 宣稱鎖屏展示已獲保證
- **AND** 系統 SHALL 允許啟動監控並保留通知設定入口

#### Scenario: 直接開啟具體渠道設定
- **WHEN** Android 8+ 設備存在可定位到具體監控渠道的異常
- **AND** 系統可解析並啟動對應渠道設定 Activity
- **THEN** 系統 SHALL 開啟包含 App package 與 channel id 的渠道設定頁

#### Scenario: 通知設定逐級回退
- **WHEN** 具體渠道設定頁不可解析、無法啟動或拋出平台例外
- **THEN** 系統 SHALL 依序嘗試 App 通知設定與 App 詳情頁
- **AND** 若所有系統頁均不可用，系統 SHALL 顯示目前 App 語言的手動操作路徑
- **AND** 導航失敗 SHALL NOT 導致 App 崩潰

#### Scenario: 從系統設定返回後重新檢查
- **WHEN** 用戶從通知渠道、App 通知或 App 詳情設定返回 BusIsComing
- **THEN** 系統 SHALL 重新讀取 App 與渠道狀態
- **AND** 系統 SHALL NOT 以 settings Activity result code 代替真實狀態檢查
- **AND** 若此前保存的監控啟動仍有效且 blocking 問題已修復，系統 SHALL 繼續該啟動流程
