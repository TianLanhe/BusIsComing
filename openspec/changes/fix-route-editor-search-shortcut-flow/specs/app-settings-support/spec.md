## MODIFIED Requirements

### Requirement: 設定頁管理乘車碼桌面快捷方式
系統 SHALL 在設定頁提供「乘車碼快捷方式」入口，透過 Android pinned shortcut 能力由用戶確認把乘車碼加入桌面，並在 Xiaomi／Redmi／POCO 裝置先提供可恢復的 HyperOS／MIUI 桌面快捷方式權限流程。

#### Scenario: 顯示快捷方式設定列
- **WHEN** 用戶查看設定頁
- **THEN** 系統 SHALL 顯示「乘車碼快捷方式」設定列
- **AND** 設定列 SHALL 以「從桌面一按開啟」說明其用途
- **AND** 設定列本身 SHALL NOT 直接啟動乘車碼

#### Scenario: 非 Xiaomi 裝置請求添加桌面快捷方式
- **WHEN** 目前裝置不是 Xiaomi、Redmi 或 POCO
- **AND** launcher 支援 pinned shortcut 且用戶點擊未固定的設定列
- **THEN** 系統 SHALL 直接使用 Android 標準 pinned shortcut 流程
- **AND** 用戶確認後建立的快捷方式 SHALL 直接進入正式乘車碼啟動鏈
- **AND** 系統 SHALL NOT 嘗試任何 OEM 私有權限頁

#### Scenario: Xiaomi 權限明確拒絕
- **WHEN** 目前裝置明確識別為 Xiaomi、Redmi 或 POCO
- **AND** 系統取得可信結果確認桌面快捷方式權限被拒絕
- **AND** 用戶點擊未固定的設定列
- **THEN** 系統 SHALL 直接開啟 BusIsComing 的 Xiaomi 應用權限設定
- **AND** 系統 SHALL NOT 在進入設定前發出 pinned shortcut 請求

#### Scenario: Xiaomi 權限未知且尚未通過閘門
- **WHEN** 目前裝置明確識別為 Xiaomi、Redmi 或 POCO
- **AND** OEM 權限狀態無法可靠判定
- **AND** 本機尚未有真正 pinned 成功所建立的權限閘門通過狀態
- **THEN** 系統 SHALL 在首次設定流程先開啟 BusIsComing 的 Xiaomi 應用權限設定
- **AND** 系統 SHALL NOT 使用隱藏 AppOps、反射或未驗證權限值假裝已授權

#### Scenario: Xiaomi 私有設定入口不可用
- **WHEN** Xiaomi 權限設定 Intent 無法由系統解析或啟動時拋出可捕獲例外
- **THEN** 系統 SHALL 降級開啟 BusIsComing 的 Android 通用應用詳情頁
- **AND** 系統 SHALL 顯示本地化路徑提示，讓用戶尋找桌面快捷方式權限
- **AND** 設定頁 SHALL NOT 崩潰

#### Scenario: 從 Xiaomi 權限頁返回後自動續辦
- **WHEN** 用戶由本次快捷方式操作進入 Xiaomi／通用設定頁後返回 BusIsComing
- **AND** 乘車碼 shortcut 尚未固定
- **THEN** 系統 SHALL 自動發出一次 Android pinned shortcut 請求
- **AND** 系統 SHALL 立即清除本次一次性 pending 狀態
- **AND** 後續 `onResume`、旋轉或 View 重建 SHALL NOT 重複自動請求

#### Scenario: Xiaomi 權限閘門只在真正成功後通過
- **WHEN** Xiaomi 家族裝置完成 pinned shortcut 成功 callback 或重新查詢確認 shortcut 已固定
- **THEN** 系統 SHALL 持久化本機權限閘門已通過狀態
- **AND** 後續未固定請求可直接進入 Android 標準流程
- **AND** 若標準流程返回後仍未固定，系統 SHALL 清除該閘門並重新提供權限入口

#### Scenario: 裝置不支援固定快捷方式
- **WHEN** 裝置 launcher 不支援 pinned shortcut
- **AND** 用戶點擊設定列
- **THEN** 系統 SHALL 顯示本地化替代操作指引
- **AND** 系統 SHALL 保持停留在設定頁且不得崩潰
- **AND** 系統 SHALL NOT 要求不存在的 Android 運行時權限

#### Scenario: 重複請求快捷方式
- **WHEN** 用戶已添加乘車碼快捷方式並再次點擊設定列
- **THEN** 系統 SHALL 顯示已添加狀態
- **AND** 系統 SHALL NOT 重複發出 pinned shortcut 請求
- **AND** 系統 SHALL NOT 在常用頁增加持續宣傳入口

### Requirement: 設定頁準確回饋乘車碼桌面快捷方式狀態
系統 SHALL 在設定頁的乘車碼快捷方式入口展示目前 pinned 及 Xiaomi 權限恢復狀態，並對需要權限、等待系統處理、固定成功、已存在、不支援及失敗提供可理解且不誤導的三語回饋。

#### Scenario: 快捷方式已固定
- **WHEN** 用戶打開或返回設定頁且乘車碼 pinned shortcut 已存在
- **THEN** 設定列 SHALL 顯示已新增狀態
- **AND** 用戶再次點擊時系統 SHALL 提示 `已新增至主畫面`
- **AND** 系統 SHALL NOT 重複發出 pinned shortcut 請求

#### Scenario: Xiaomi 設備需要權限
- **WHEN** Xiaomi 家族裝置需要先完成桌面快捷方式權限閘門
- **THEN** 設定列 SHALL 顯示可操作的需要權限或檢查權限狀態
- **AND** 回饋 SHALL 指向系統設定而非不存在的 Android 運行時授權框
- **AND** 系統 SHALL NOT 宣告快捷方式已新增

#### Scenario: 系統接受固定請求
- **WHEN** 用戶點擊尚未固定且 launcher 支援的乘車碼快捷方式設定列
- **AND** `requestPinShortcut()` 返回 true
- **THEN** 系統 SHALL 把狀態視為請求已發出或等待系統處理
- **AND** 系統 SHALL NOT 僅依該返回值顯示「請在系統視窗確認新增」或宣告已新增
- **AND** 系統 SHALL 等待成功 callback 或後續 pinned 狀態查詢

#### Scenario: 固定快捷方式成功
- **WHEN** launcher 完成固定並回傳成功 callback，或設定頁重新查詢確認 shortcut 已 pinned
- **THEN** 設定頁 SHALL 顯示新增成功回饋
- **AND** 設定列 SHALL 刷新為已新增狀態

#### Scenario: 用戶取消或系統沒有完成固定
- **WHEN** 系統已接受請求但沒有成功 callback且返回後 shortcut 仍未 pinned
- **THEN** 系統 SHALL 保持設定列為未新增狀態
- **AND** 系統 SHALL NOT 誤報成功
- **AND** 非 Xiaomi 裝置 SHALL 保持可重試
- **AND** Xiaomi 家族裝置 SHALL 提供重試或檢查桌面快捷方式權限入口

#### Scenario: launcher 不支援固定快捷方式
- **WHEN** 目前 launcher 不支援 App 內 pinned shortcut
- **THEN** 系統 SHALL 指引用戶長按 BusIsComing 圖示並把靜態 `乘車碼` 快捷項拖到主畫面
- **AND** 系統 SHALL NOT 要求不存在的 Android 運行時權限

#### Scenario: 固定請求失敗
- **WHEN** pinned shortcut API 返回 false 或發生可捕獲例外
- **THEN** 系統 SHALL 顯示可重試的失敗回饋
- **AND** 設定列 SHALL 保持未新增狀態及可點擊
- **AND** Xiaomi 家族裝置 SHALL 同時提供檢查權限入口

#### Scenario: 返回設定頁重新檢查
- **WHEN** 設定頁進入 `onResume`，包括從 launcher 確認畫面、Xiaomi 權限頁或桌面返回
- **THEN** 系統 SHALL 先重新查詢 pinned shortcut 狀態
- **AND** 只有本次權限導航留下的一次性 pending 狀態 SHALL 觸發一次自動續辦
- **AND** 設定列 SHALL 反映目前實際可檢測狀態

#### Scenario: 多語與深淺色顯示
- **WHEN** App 使用繁體中文、簡體中文或英文，以及淺色或深色模式
- **THEN** 需要權限、等待、成功、已存在、不支援及失敗回饋 SHALL 使用對應語言資源及語意色
- **AND** 狀態文字 SHALL NOT 與設定列圖示或其他內容重疊
