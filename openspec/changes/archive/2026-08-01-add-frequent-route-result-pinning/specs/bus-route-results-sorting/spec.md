## MODIFIED Requirements

### Requirement: 排序时保留结果集

系統 SHALL 在排序時保留目前巴士路線結果集的全部唯一結果；當常用頁存在置頂路線時，排序 SHALL 只改變未置頂路線的展示順序，且 MUST NOT 增加、刪除、重複或重排置頂路線。

#### Scenario: 排序保留所有行
- **WHEN** 用戶對非空巴士路線結果集排序
- **THEN** 展示卡片仍 SHALL 包含排序前相同的唯一路線結果，只按目前置頂與排序規則改變位置

#### Scenario: 排序處理相同值
- **WHEN** 兩條或多條路線結果在排序字段上具有相同價格或等待時間
- **THEN** 系統 SHALL 保持所有匹配卡片可見，不丟失或重複資料

#### Scenario: 常用頁排序只作用於未置頂結果
- **WHEN** 常用頁結果同時包含置頂與未置頂路線
- **AND** 用戶切換排序字段或方向
- **THEN** 系統 SHALL 保持所有置頂路線的 token 降序
- **AND** 系統 SHALL 只按所選字段與方向重排未置頂路線

#### Scenario: 搜尋頁排序維持既有行為
- **WHEN** 用戶在搜尋 destination 對查詢結果排序
- **THEN** 系統 SHALL 按既有排序規則重排全部搜尋結果
- **AND** 系統 SHALL NOT 建立置頂區域或置頂排序例外

#### Scenario: 漸進 ETA 更新保持置頂區域
- **WHEN** 常用頁按候車時間排序且 ETA 漸進更新
- **THEN** 系統 SHALL 只按更新後 ETA 重排未置頂結果
- **AND** 置頂結果 SHALL 保持原 token 順序
