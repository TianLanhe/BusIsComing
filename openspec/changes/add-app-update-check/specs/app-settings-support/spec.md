## MODIFIED Requirements

### Requirement: 暫不支援入口提供明確 Toast
系統 SHALL 保留應用評分入口為可點擊狀態並提供目前語言的暫不支援提示；檢查更新入口 SHALL 改由 `app-update-check` 能力提供實際版本檢查及更新行為。

#### Scenario: 點擊應用評分入口
- **WHEN** 用戶在設定頁點擊 `應用評分`
- **THEN** 系統 SHALL 以目前 App 語言顯示暫不支援提示
- **AND** 系統 SHALL NOT 打開商店頁或 Play In-App Review

#### Scenario: 點擊檢查更新入口
- **WHEN** 用戶在設定頁點擊 `檢查更新`
- **THEN** 系統 SHALL 發起或附著到 `app-update-check` 定義的手動更新檢查
- **AND** 系統 SHALL NOT 顯示檢查更新暫不支援提示

