## ADDED Requirements

### Requirement: 候車監控通知提供乘車碼情境操作
系統 SHALL 在候車監控通知中以「重新整理／乘車碼／停止」順序提供三個操作，並讓乘車碼操作復用正式支付 provider 回退鏈而不改變監控狀態。

#### Scenario: 監控通知顯示第三個操作
- **WHEN** 候車監控前台服務正在運行
- **THEN** 通知 SHALL 同時顯示重新整理、乘車碼及停止 action
- **AND** 乘車碼 action SHALL 使用目前 App 語言的短標籤

#### Scenario: 從通知開啟乘車碼
- **WHEN** 用戶點擊通知中的乘車碼 action
- **THEN** 系統 SHALL 進入與其他正式入口相同的乘車碼候選鏈
- **AND** 目前監控 session SHALL 繼續運行
- **AND** 系統 SHALL NOT 把該點擊當作重新整理或停止操作

#### Scenario: 通知乘車碼啟動失敗
- **WHEN** 所有乘車碼候選都無法由 Android 啟動
- **THEN** 系統 SHALL 顯示既有本地化失敗提示
- **AND** 候車監控 SHALL 保持運行並可繼續重新整理或停止
