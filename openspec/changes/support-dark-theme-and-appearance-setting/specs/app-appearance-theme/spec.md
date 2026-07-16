## ADDED Requirements

### Requirement: App 提供三種可持久化外觀模式

系統 SHALL 提供 `跟隨系統`、`淺色模式` 與 `深色模式` 三種互斥外觀選擇，並 SHALL 保存用戶選擇供後續程序啟動使用。

#### Scenario: 無既有偏好時預設跟隨系統
- **WHEN** 新安裝用戶首次啟動 App，或升級用戶尚未保存外觀偏好
- **THEN** 系統 SHALL 使用 `跟隨系統`
- **AND** 系統 SHALL NOT 因缺少偏好阻止任何 Activity 啟動

#### Scenario: 重新啟動後保持已選模式
- **WHEN** 用戶已選擇任一外觀模式並完全重新啟動 App
- **THEN** 系統 SHALL 在顯示第一個 App 畫面前套用已保存模式
- **AND** 設定頁 SHALL 顯示相同的已保存模式

#### Scenario: 損壞或未知偏好安全回退
- **WHEN** 已保存外觀值為空白、未知或無法解析
- **THEN** 系統 SHALL 回退 `跟隨系統`
- **AND** 系統 SHALL NOT 崩潰或停留在無法操作的啟動畫面

### Requirement: 三種模式遵循明確的系統明暗語義

系統 SHALL 讓跟隨系統模式響應 Android 的日／夜配置，並 SHALL 讓固定淺色或深色模式覆蓋後續系統明暗變更。

#### Scenario: 跟隨系統響應日夜變更
- **WHEN** 已保存模式為 `跟隨系統`
- **AND** Android 系統由淺色切換至深色，或由深色切換至淺色
- **THEN** App 自有可見介面 SHALL 套用對應明暗資源

#### Scenario: 固定淺色忽略系統深色
- **WHEN** 已保存模式為 `淺色模式`
- **AND** Android 系統使用或切換至深色
- **THEN** App 自有介面 SHALL 保持淺色外觀

#### Scenario: 固定深色忽略系統淺色
- **WHEN** 已保存模式為 `深色模式`
- **AND** Android 系統使用或切換至淺色
- **THEN** App 自有介面 SHALL 保持深色外觀

### Requirement: 外觀變更使用標準生命週期立即套用

系統 SHALL 在外觀選擇變更後立即套用 AppCompat 日夜模式，並 SHALL 透過標準 Activity 重建及 Android 資源限定符讓所有 App 自有畫面取得一致資源。

#### Scenario: 選擇不同模式立即生效
- **WHEN** 用戶在設定中選擇與目前保存值不同的外觀模式
- **THEN** 系統 SHALL 先保存新模式再套用
- **AND** 當前設定頁及後續打開的 App 畫面 SHALL 使用新模式
- **AND** 系統 SHALL NOT 要求用戶重新啟動 App 才生效

#### Scenario: 選擇目前模式不做無效切換
- **WHEN** 用戶再次選擇目前已保存的外觀模式
- **THEN** 系統 SHALL 保持目前畫面與模式
- **AND** 系統 SHALL NOT 顯示成功 Toast 或執行可見的無效重載

#### Scenario: 不攔截 uiMode 重建
- **WHEN** 外觀模式變更需要 Activity 重建
- **THEN** 系統 SHALL 使用 Android 與 AppCompat 的標準生命週期
- **AND** 系統 SHALL NOT 以 Manifest `configChanges` 或逐 View 執行時換色繞過該重建

### Requirement: 主題重建不得破壞持久資料與背景能力

系統 SHALL 將外觀設定與路線資料、匯入狀態及通知監控狀態隔離，並 SHALL NOT 把具有時效的 ETA 或查詢結果寫入外觀偏好。

#### Scenario: 已保存路線與使用統計保持不變
- **WHEN** App 因外觀模式切換重建 Activity
- **THEN** SQLite 中的常用路線、使用次數及最近使用時間 SHALL 保持不變

#### Scenario: 路線匯入流程保持可恢復
- **WHEN** 路線傳輸頁在外觀模式切換期間被重建
- **THEN** 系統 SHALL 延續該頁既有的 stage、URI、檔名及 summary 恢復契約
- **AND** 系統 SHALL NOT 因主題切換新增、修改或刪除常用路線

#### Scenario: 通知監控狀態不受影響
- **WHEN** 前台通知監控正在運行且 App 外觀模式發生變更
- **THEN** 監控 session 與前台服務 SHALL 保持運行
- **AND** 外觀設定 SHALL NOT 修改通知 channel、刷新排程或語音設定

#### Scenario: 即時結果可重新取得
- **WHEN** 主頁在顯示即時 ETA 或查詢結果時因外觀模式切換而重建
- **THEN** 系統 SHALL 保留已保存路線並提供重新查詢能力
- **AND** 系統 SHALL NOT 將重建前的 ETA 當成持久外觀狀態恢復

