## MODIFIED Requirements

### Requirement: 設定頁按 App 資訊、偏好、支援與關於分組
系統 SHALL 使用獨立設定頁承載 App 級低頻入口，並以清楚分組避免首頁平鋪功能按鈕。

#### Scenario: 顯示設定頁基本結構
- **WHEN** 用戶打開設定頁
- **THEN** 系統 SHALL 顯示頁面標題 `設定`
- **AND** 系統 SHALL 顯示 App 名稱 `BusIsComing`
- **AND** 系統 SHALL 顯示目前 App 版本
- **AND** 系統 SHALL 顯示 `偏好`、`支援`、`關於` 分組

#### Scenario: 偏好分組
- **WHEN** 用戶查看設定頁 `偏好` 分組
- **THEN** 系統 SHALL 依序顯示 `外觀主題` 與 `語言` 入口
- **AND** `外觀主題` SHALL 位於目前尚未支援的 `語言` 之前

#### Scenario: 支援分組
- **WHEN** 用戶查看設定頁 `支援` 分組
- **THEN** 系統 SHALL 依序顯示 `分享應用`、`問題反饋`、`應用評分`、`檢查更新` 入口

#### Scenario: 關於分組
- **WHEN** 用戶查看設定頁 `關於` 分組
- **THEN** 系統 SHALL 依序顯示 `關於我們`、`隱私政策` 入口

#### Scenario: 返回主頁
- **WHEN** 用戶在設定頁點擊左上返回入口或按系統返回
- **THEN** 系統 SHALL 關閉設定頁並回到主頁

## ADDED Requirements

### Requirement: 設定頁提供外觀主題單選入口

系統 SHALL 在 `外觀主題` 設定列顯示已保存模式，並 SHALL 使用可存取的 Material 單選對話框讓用戶切換三種外觀模式。

#### Scenario: 設定列顯示目前值
- **WHEN** 用戶打開設定頁
- **THEN** `外觀主題` 列 SHALL 顯示目前保存值 `跟隨系統`、`淺色模式` 或 `深色模式`
- **AND** 整列 SHALL 提供至少 48dp 的觸控高度
- **AND** 輔助技術 SHALL 能讀取設定名稱及目前值

#### Scenario: 打開外觀主題單選對話框
- **WHEN** 用戶點擊 `外觀主題`
- **THEN** 系統 SHALL 顯示標題為 `外觀主題` 的 Material 單選對話框
- **AND** 對話框 SHALL 依序顯示 `跟隨系統`、`淺色模式`、`深色模式`
- **AND** 對話框 SHALL 選中目前已保存模式
- **AND** 輔助技術 SHALL 能讀取每個選項及其選中狀態

#### Scenario: 選擇新的外觀模式
- **WHEN** 用戶在對話框點擊與目前不同的模式
- **THEN** 系統 SHALL 保存並立即套用該模式
- **AND** 對話框 SHALL 關閉
- **AND** 設定列摘要 SHALL 在 Activity 重建後顯示新模式
- **AND** 系統 SHALL NOT 顯示成功 Toast 或要求額外確認

#### Scenario: 重新選擇目前模式
- **WHEN** 用戶在對話框點擊目前已保存模式
- **THEN** 系統 SHALL 關閉對話框並保持目前模式
- **AND** 系統 SHALL NOT 顯示成功 Toast 或執行可見的無效重載

