## ADDED Requirements

### Requirement: 乘車碼 pinned shortcut 使用可確認的建立流程
系統 SHALL 透過 Android pinned shortcut 能力建立乘車碼桌面入口，並提供成功 callback 及結構化平台結果，使設定頁不需要推測請求是否完成。

#### Scenario: 建立 pinned shortcut 請求
- **WHEN** launcher 支援 pinned shortcut 且乘車碼 shortcut 尚未固定
- **THEN** 系統 SHALL 使用穩定 shortcut id、App 圖示、當前語言標籤及正式乘車碼 explicit intent 建立請求
- **AND** 請求 SHALL 包含只在 launcher 真正完成固定後觸發的成功 callback

#### Scenario: 查詢已固定狀態
- **WHEN** 系統需要顯示或刷新乘車碼 shortcut 狀態
- **THEN** shortcut manager SHALL 回傳已固定、未固定或不可可靠判定的結構化狀態
- **AND** package／launcher 查詢例外 SHALL NOT 讓設定頁崩潰

#### Scenario: 平台接受但尚未成功
- **WHEN** `requestPinShortcut` 返回 true 且成功 callback 尚未觸發
- **THEN** shortcut manager SHALL 回傳請求已發出狀態
- **AND** 該狀態 SHALL NOT 等同固定成功

#### Scenario: 平台拒絕或拋出例外
- **WHEN** `requestPinShortcut` 返回 false 或拋出可捕獲例外
- **THEN** shortcut manager SHALL 回傳失敗狀態
- **AND** 系統 SHALL 保留再次請求能力

### Requirement: 所有乘車碼快捷入口沿用正式啟動契約
系統 SHALL 讓 pinned shortcut、長按 App 圖示的靜態 shortcut及候車監控通知 action 使用同一正式 `OPEN_TRANSIT_CODE` explicit intent 與既有支付工具候選鏈。

#### Scenario: 從桌面 pinned shortcut 開啟乘車碼
- **WHEN** 用戶點擊已固定至桌面的乘車碼 shortcut
- **THEN** 系統 SHALL 進入正式乘車碼啟動流程
- **AND** 系統 SHALL NOT 打開設定頁、搜尋頁或實驗性乘車碼面板

#### Scenario: 從靜態 shortcut 開啟乘車碼
- **WHEN** 用戶長按 App 圖示並點擊靜態 `乘車碼` shortcut
- **THEN** 系統 SHALL 使用與 pinned shortcut 相同的正式乘車碼啟動流程

#### Scenario: 快捷入口不改變巴士狀態
- **WHEN** 用戶透過 pinned、靜態或通知入口開啟乘車碼後返回 App
- **THEN** 系統 SHALL 保留既有常用行程選擇、搜尋上下文、排序及查詢結果
- **AND** 系統 SHALL NOT 寫入支付工具偏好或修改已保存行程
