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

### Requirement: 設定頁以標準設定行及單選對話框管理自動刷新
系統 SHALL 在偏好分組以標準設定行顯示自動刷新目前值，並 SHALL 讓用戶透過 Material 單選對話框選擇關閉、1、2、5 或 10 分鐘。

#### Scenario: 顯示自動刷新設定行
- **WHEN** 用戶查看設定頁偏好分組
- **THEN** 系統 SHALL 在語言項之後顯示與外觀主題及語言一致的 `自動刷新` 標準設定行
- **AND** 設定行左側 SHALL 顯示標題，右側 SHALL 顯示目前持久化值
- **AND** 設定頁 SHALL NOT 在頁面內直接平鋪五個間隔按鈕

#### Scenario: 打開單選對話框
- **WHEN** 用戶啟用自動刷新設定行
- **THEN** 系統 SHALL 打開 Material 單選對話框
- **AND** 對話框 SHALL 完整顯示 `關閉`、`1 分鐘`、`2 分鐘`、`5 分鐘`、`10 分鐘` 五個互斥選項
- **AND** 目前持久化選項 SHALL 顯示為已選中

#### Scenario: 選擇不同間隔
- **WHEN** 用戶在對話框選擇任一不同選項
- **THEN** 系統 SHALL 立即保存並套用該值
- **AND** 系統 SHALL 關閉對話框並立即更新設定行右側目前值
- **AND** 系統 SHALL NOT 顯示成功 Toast 或要求額外確認

#### Scenario: 重新選擇目前值
- **WHEN** 用戶在對話框重新選擇目前已選中的選項
- **THEN** 系統 SHALL 保持目前刷新值、關閉對話框且不執行可見重載
- **AND** 系統 SHALL 把該操作視為使用者已明確理解並選擇自動刷新設定

#### Scenario: 寬度或大型字體適配
- **WHEN** 三語文案、360dp 級別可用寬度或字體比例 1.3／2.0 令內容需要更多空間
- **THEN** 標準設定行及單選對話框 SHALL 自然換行或擴高以完整展示標題、目前值與全部選項
- **AND** 系統 SHALL NOT 縮小字體、裁切文字、重疊控件或要求橫向捲動才能理解目前值與全部選項

#### Scenario: 輔助技術讀取設定行及選項
- **WHEN** TalkBack 或其他輔助技術聚焦自動刷新設定行或對話框選項
- **THEN** 設定行 SHALL 讀出設定名稱及目前間隔，對話框選項 SHALL 讀出間隔及選中狀態
- **AND** 設定行與每個可操作選項 SHALL 提供至少 48dp 的有效觸控區
- **AND** 所有文案 SHALL 使用目前 App 的香港繁體、獨立簡體或自然英文資源

#### Scenario: 首次提示跳轉至設定
- **WHEN** 用戶從自動刷新首次提示啟用 `設定`
- **THEN** 系統 SHALL 打開設定 destination、捲動至自動刷新設定行並把焦點放在整個設定行
- **AND** 系統 SHALL NOT 嘗試聚焦已移除的行內選項或自動打開單選對話框
