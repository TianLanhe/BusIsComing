## MODIFIED Requirements

### Requirement: 暫不支援入口提供明確 Toast
系統 SHALL 保留應用評分與檢查更新入口為可點擊狀態；應用評分 SHALL 交由 `google-play-app-rating` 提供 Google Play 商品導向及恢復行為，檢查更新 SHALL 交由 `app-update-check` 提供實際版本檢查及更新行為。

#### Scenario: 點擊應用評分入口
- **WHEN** 用戶在設定頁點擊 `應用評分`
- **THEN** 系統 SHALL 依 `google-play-app-rating` 檢查官方 Google Play 狀態並打開商品頁或提供對應恢復操作
- **AND** 系統 SHALL NOT 顯示應用評分暫不支援提示或啟動 Play In-App Review

#### Scenario: 點擊檢查更新入口
- **WHEN** 用戶在設定頁點擊 `檢查更新`
- **THEN** 系統 SHALL 發起或附著到 `app-update-check` 定義的手動更新檢查
- **AND** 系統 SHALL NOT 顯示檢查更新暫不支援提示
