## ADDED Requirements

### Requirement: 設定頁管理乘車碼桌面快捷方式
系統 SHALL 在設定頁提供「乘車碼快捷方式」入口，並透過 Android pinned shortcut 能力由用戶確認把乘車碼添加至桌面。

#### Scenario: 顯示快捷方式設定列
- **WHEN** 用戶查看設定頁
- **THEN** 系統 SHALL 顯示「乘車碼快捷方式」設定列
- **AND** 設定列 SHALL 以「從桌面一按開啟」說明其用途
- **AND** 設定列 SHALL NOT 直接啟動乘車碼

#### Scenario: 請求添加桌面快捷方式
- **WHEN** 裝置支援 pinned shortcut 且用戶點擊設定列
- **THEN** 系統 SHALL 顯示由系統 launcher 管理的添加確認
- **AND** 用戶確認後建立的快捷方式 SHALL 直接進入正式乘車碼啟動鏈

#### Scenario: 裝置不支援固定快捷方式
- **WHEN** 裝置 launcher 不支援 pinned shortcut
- **AND** 用戶點擊設定列
- **THEN** 系統 SHALL 顯示本地化的不可用提示
- **AND** 系統 SHALL 保持停留在設定頁且不得崩潰

#### Scenario: 重複請求快捷方式
- **WHEN** 用戶已添加乘車碼快捷方式並再次點擊設定列
- **THEN** 系統 SHALL 可再次交由 launcher 處理請求或顯示已添加狀態
- **AND** 系統 SHALL NOT 在常用頁增加持續宣傳入口
