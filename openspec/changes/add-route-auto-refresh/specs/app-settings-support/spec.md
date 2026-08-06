## MODIFIED Requirements

### Requirement: 設定頁按 App 資訊、偏好、支援與關於分組
系統 SHALL 使用頂層設定 destination 承載 App 級低頻入口，並以清楚分組避免常用與搜尋頁平鋪功能按鈕。

#### Scenario: 顯示設定頁基本結構
- **WHEN** 用戶打開設定頁
- **THEN** 系統 SHALL 顯示頁面標題 `設定`
- **AND** 系統 SHALL 顯示 App 名稱 `BusIsComing`
- **AND** 系統 SHALL 顯示目前 App 版本
- **AND** 系統 SHALL 顯示 `偏好`、`路線資料`、`支援`、`關於` 分組

#### Scenario: 偏好分組
- **WHEN** 用戶查看設定頁 `偏好` 分組
- **THEN** 系統 SHALL 依序顯示 `外觀主題`、`語言` 與 `自動刷新`
- **AND** `自動刷新` SHALL 位於 `語言` 之後

#### Scenario: 路線資料分組
- **WHEN** 用戶查看設定頁 `路線資料` 分組
- **THEN** 系統 SHALL 顯示 `匯入與匯出常用路線` 入口

#### Scenario: 支援分組
- **WHEN** 用戶查看設定頁 `支援` 分組
- **THEN** 系統 SHALL 依序顯示 `分享應用`、`問題反饋`、`應用評分`、`檢查更新` 入口

#### Scenario: 關於分組
- **WHEN** 用戶查看設定頁 `關於` 分組
- **THEN** 系統 SHALL 依序顯示 `關於我們`、`隱私政策` 入口

#### Scenario: 設定是頂層 destination
- **WHEN** 用戶透過底部導航打開設定頁
- **THEN** 系統 SHALL 保持底部導航可見並標識設定為目前 destination
- **AND** 系統 SHALL NOT 顯示左上返回入口或把設定呈現為獨立次級頁

#### Scenario: 返回主頁
- **WHEN** 用戶在設定頁點擊左上返回入口或按系統返回
- **THEN** 系統 SHALL 關閉設定頁並回到主頁

## ADDED Requirements

### Requirement: 設定頁以行內分段選擇器管理自動刷新
系統 SHALL 在偏好分組以可存取的行內 segmented selector 讓用戶一次點擊選擇關閉、1、2、5 或 10 分鐘，並 SHALL 清楚顯示目前選中值。

#### Scenario: 顯示自動刷新選項
- **WHEN** 用戶查看設定頁偏好分組
- **THEN** 系統 SHALL 在 `自動刷新` 內同時顯示 `關閉`、`1 分鐘`、`2 分鐘`、`5 分鐘`、`10 分鐘` 五個互斥選項
- **AND** 目前持久化選項 SHALL 以清楚的 container、文字與選中狀態突出
- **AND** 系統 SHALL NOT 要求先打開單選對話框

#### Scenario: 一次點擊切換間隔
- **WHEN** 用戶點擊任一不同選項
- **THEN** 系統 SHALL 立即保存並套用該值
- **AND** selector SHALL 立即更新選中狀態
- **AND** 系統 SHALL NOT 顯示成功 Toast 或要求額外確認

#### Scenario: 重新選擇目前值
- **WHEN** 用戶點擊目前已選中的選項
- **THEN** 系統 SHALL 保持目前刷新值且不執行可見重載
- **AND** 系統 SHALL 把該操作視為使用者已明確理解並選擇自動刷新設定

#### Scenario: 360dp 正常字體顯示 selector
- **WHEN** 設定頁可用寬度為 360dp 級別且字體比例為 1.0
- **THEN** 五個精簡選項 SHALL 在同一行完整顯示
- **AND** 文字 SHALL NOT 被縮小、裁切或重疊

#### Scenario: 寬度或大型字體不足
- **WHEN** 三語文案、可用寬度或字體比例 1.3／2.0 令五個選項無法在一行自然容納
- **THEN** selector SHALL 以 wrap、reflow 或獨立 trailing row 完整展示全部選項
- **AND** 系統 SHALL NOT 縮小字體、裁切文字、重疊控件或要求橫向捲動才能理解目前值

#### Scenario: 輔助技術讀取 selector
- **WHEN** TalkBack 或其他輔助技術聚焦自動刷新選項
- **THEN** 每個選項 SHALL 讀出設定名稱、間隔與選中狀態
- **AND** 每個可操作選項 SHALL 提供至少 48dp 的有效觸控區
- **AND** 所有文案 SHALL 使用目前 App 的香港繁體、獨立簡體或自然英文資源
