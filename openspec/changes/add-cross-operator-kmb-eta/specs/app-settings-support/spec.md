## ADDED Requirements

### Requirement: 設定頁可檢查全局路線資料庫更新
系統 SHALL 在設定頁 `路線資料` 分組提供「路線資料庫更新檢查」，展示五項全局來源最近一次完整成功檢查狀態，並允許用戶手動加入或觸發 single-flight 檢查。

#### Scenario: 顯示最近完整成功時間
- **WHEN** 五項全局來源曾全部成功驗證
- **THEN** 設定列摘要 SHALL 以目前 App 語言顯示最近一次完整成功的香港日期與時間
- **AND** 摘要 SHALL NOT 使用單一 provider timestamp 或 CTB 路線懶載入時間代替

#### Scenario: 尚未有完整成功快照
- **WHEN** 五項全局來源從未完成一次完整成功驗證
- **THEN** 設定列摘要 SHALL 以目前 App 語言顯示尚未完成或等價狀態

#### Scenario: 用戶手動開始檢查
- **WHEN** 沒有全局檢查正在進行
- **AND** 用戶點擊「路線資料庫更新檢查」
- **THEN** 系統 SHALL 繞過每日資料日門禁並開始一項五來源檢查
- **AND** 設定列 SHALL 顯示檢查中並阻止重複點擊
- **AND** 系統 SHALL NOT 因手動檢查批量懶載入 CTB 站點或預計算 DP

#### Scenario: 用戶在自動檢查期間點擊
- **WHEN** 自動全局檢查正在進行
- **AND** 用戶點擊「路線資料庫更新檢查」
- **THEN** 設定頁 SHALL 觀察同一項檢查並顯示檢查中
- **AND** 系統 SHALL NOT 建立第二項全局檢查

#### Scenario: 手動檢查成功且內容未變
- **WHEN** 五項來源均成功驗證且語義內容未改變
- **THEN** 設定列 SHALL 顯示目前時間為最近完整成功時間
- **AND** 系統 SHALL 以目前 App 語言提示資料已是最新

#### Scenario: 手動檢查成功且發布新快照
- **WHEN** 五項來源均成功驗證且原子發布了新快照
- **THEN** 設定列 SHALL 刷新最近完整成功時間
- **AND** 系統 SHALL 以目前 App 語言提示路線資料已更新

#### Scenario: 手動檢查失敗
- **WHEN** 任一全局來源令本次手動檢查失敗
- **THEN** 設定列 SHALL 恢復可操作
- **AND** 系統 SHALL 保留及顯示上次完整成功時間
- **AND** 系統 SHALL 以目前 App 語言提示仍使用上次資料

#### Scenario: 檢查期間設定頁被銷毀
- **WHEN** 設定頁在全局檢查完成前離開或因配置變更被銷毀
- **THEN** 設定頁 SHALL 解除舊 view 觀察而不得取消仍由 App runtime 使用的檢查
- **AND** 過期 callback SHALL NOT 更新已銷毀或重建後不相符的畫面

#### Scenario: 設定列的語言與無障礙
- **WHEN** 用戶以香港繁體、簡體或英文及輔助技術查看設定列
- **THEN** 標題、狀態、成功及失敗提示 SHALL 使用目前 App 語言
- **AND** 輔助技術 SHALL 能讀取設定名稱、目前狀態或最近成功時間及是否可操作
