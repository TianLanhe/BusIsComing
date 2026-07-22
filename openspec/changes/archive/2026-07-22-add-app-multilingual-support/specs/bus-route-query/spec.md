## ADDED Requirements

### Requirement: 語言切換後保留查詢上下文並自動重查
系統 SHALL 在語言切換後分別保留常用與搜尋 destination 的有效起終點語義，並以新語言重新取得曾查詢過的動態路線資料。

#### Scenario: 已選常用路線時切換語言
- **WHEN** 常用 destination 已選擇一條常用路線並曾發起有效查詢，且用戶切換語言
- **THEN** 系統 SHALL 保留該常用路線 id 及原始保存名稱
- **AND** 系統 SHALL 使用其起終點座標以新語言自動重查

#### Scenario: 搜尋查詢時切換語言
- **WHEN** 搜尋 destination 已使用未保存的起終點發起有效查詢，且用戶切換語言
- **THEN** 系統 SHALL 保留搜尋起終點名稱與座標
- **AND** 系統 SHALL 使用新語言自動重查

#### Scenario: 兩個 destination 都有有效查詢
- **WHEN** 常用與搜尋 destination 均有曾發起查詢的有效起終點上下文
- **AND** 用戶切換語言
- **THEN** 兩個 destination SHALL 各自使舊 generation 失效並以新語言重查
- **AND** 任一 destination 的結果 SHALL NOT 覆蓋另一 destination 的查詢狀態

#### Scenario: destination 切換不觸發語言重查
- **WHEN** 用戶只在常用、搜尋與設定 destination 之間切換而 App 實際語言沒有改變
- **THEN** 系統 SHALL 保留各自現有查詢狀態
- **AND** 系統 SHALL NOT 將普通 destination 切換當成語言變更而重查

#### Scenario: 自動重查不記錄使用
- **WHEN** 系統因語言切換自動重查常用路線
- **THEN** 系統 SHALL NOT 增加該路線使用次數
- **AND** 系統 SHALL NOT 改變未切換其他路線前的既有使用去重狀態

#### Scenario: 舊語言漸進更新晚到
- **WHEN** 舊語言路線的 ETA、站點預覽或詳情更新在新語言查詢後返回
- **THEN** 系統 SHALL 忽略該更新
- **AND** 系統 SHALL NOT 將舊語言資料插入常用或搜尋的目前結果列表

#### Scenario: 新語言自動重查失敗
- **WHEN** 新語言路線查詢失敗
- **THEN** 系統 SHALL 清除舊結果並以新語言展示失敗狀態
- **AND** 系統 SHALL 保留起終點供用戶手動重試
