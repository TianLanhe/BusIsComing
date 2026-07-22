## ADDED Requirements

### Requirement: 設定頁準確回饋乘車碼桌面快捷方式狀態
系統 SHALL 在設定頁的乘車碼快捷方式入口展示目前 pinned shortcut 狀態，並對請求接受、固定成功、已存在、不支援及失敗提供可理解且不誤導的三語回饋。

#### Scenario: 快捷方式已固定
- **WHEN** 用戶打開或返回設定頁且乘車碼 pinned shortcut 已存在
- **THEN** 設定列 SHALL 顯示已新增狀態
- **AND** 用戶再次點擊時系統 SHALL 提示 `已新增至主畫面`
- **AND** 系統 SHALL NOT 重複發出 pinned shortcut 請求

#### Scenario: 系統接受固定請求
- **WHEN** 用戶點擊尚未固定且 launcher 支援的乘車碼快捷方式設定列
- **AND** 系統接受 pinned shortcut 請求
- **THEN** 系統 SHALL 提示用戶在系統視窗確認新增
- **AND** 系統 SHALL NOT 在收到成功 callback 前宣告已新增

#### Scenario: 固定快捷方式成功
- **WHEN** launcher 完成固定並回傳成功 callback
- **THEN** 設定頁 SHALL 顯示新增成功回饋
- **AND** 設定列 SHALL 刷新為已新增狀態

#### Scenario: 用戶取消系統確認
- **WHEN** 系統已接受請求但用戶取消 launcher 確認
- **THEN** 系統 SHALL 保持設定列為未新增狀態
- **AND** 系統 SHALL NOT 誤報成功或失敗
- **AND** 用戶 SHALL 能夠再次點擊重試

#### Scenario: launcher 不支援固定快捷方式
- **WHEN** 目前 launcher 不支援 App 內 pinned shortcut
- **THEN** 系統 SHALL 指引用戶長按 BusIsComing 圖示並把靜態 `乘車碼` 快捷項拖到主畫面
- **AND** 系統 SHALL NOT 要求不存在的 Android 運行時權限

#### Scenario: 固定請求失敗
- **WHEN** pinned shortcut API 返回 false 或發生可捕獲例外
- **THEN** 系統 SHALL 顯示可重試的失敗回饋
- **AND** 設定列 SHALL 保持未新增狀態及可點擊

#### Scenario: 返回設定頁重新檢查
- **WHEN** 設定頁進入 `onResume`，包括從 launcher 確認畫面返回或用戶從桌面移除 shortcut 後返回
- **THEN** 系統 SHALL 重新查詢 pinned shortcut 狀態
- **AND** 設定列 SHALL 反映目前實際可檢測狀態

#### Scenario: 多語與深淺色顯示
- **WHEN** App 使用繁體中文、簡體中文或英文，以及淺色或深色模式
- **THEN** 請求確認、成功、已存在、不支援及失敗回饋 SHALL 使用對應語言資源及語意色
- **AND** 狀態文字 SHALL NOT 與設定列圖示或其他內容重疊
