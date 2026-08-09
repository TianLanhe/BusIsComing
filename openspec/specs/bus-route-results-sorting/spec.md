# bus-route-results-sorting Specification

## Purpose
TBD - created by archiving change build-bus-query-mvp. Update Purpose after archive.
## Requirements
### Requirement: 按价格排序巴士结果

系统 SHALL 允许用户通过价格表头按港币价格排序当前展示的巴士路线结果。

#### Scenario: 价格升序排序
- **WHEN** 用户在当前结果集未按价格排序时点击价格表头
- **THEN** 系统按价格升序展示当前结果集

#### Scenario: 价格降序排序
- **WHEN** 用户在当前结果集已按价格升序排序时再次点击价格表头
- **THEN** 系统按价格降序展示当前结果集

### Requirement: 按预计等候时间排序巴士结果

系统 SHALL 允许用户通过预计等候时间表头按车辆等待分钟数排序当前展示的巴士路线结果。

#### Scenario: 等待时间升序排序
- **WHEN** 用户在当前结果集未按预计等候时间排序时点击预计等候时间表头
- **THEN** 系统按等待分钟数升序展示当前结果集

#### Scenario: 等待时间降序排序
- **WHEN** 用户在当前结果集已按等待时间升序排序时再次点击预计等候时间表头
- **THEN** 系统按等待分钟数降序展示当前结果集

### Requirement: 展示当前排序方向

系统 SHALL 在当前激活的可排序表头上展示排序方向。

#### Scenario: 展示升序方向
- **WHEN** 当前结果按价格或预计等候时间升序排序
- **THEN** 激活表头展示升序方向标记

#### Scenario: 展示降序方向
- **WHEN** 当前结果按价格或预计等候时间降序排序
- **THEN** 激活表头展示降序方向标记

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

### Requirement: 漸進步行距離排序保持結果身份與置頂邊界
系統 SHALL 在步行距離漸進更新時保留目前路線結果的穩定身份與完整集合；只有目前選擇步行距離排序時，系統才 SHALL 依新狀態重排適用區域。

#### Scenario: 數值結果排在查詢中結果之前
- **WHEN** 目前按步行距離升序或降序排序，且結果同時包含可用數值與查詢中狀態
- **THEN** CSDI 成功及 Citybus 回退數值 SHALL 先按目前方向排序
- **AND** 查詢中結果 SHALL 固定置於全部數值結果之後，不因降序而反轉至前方

#### Scenario: 相同值與查詢中次序穩定
- **WHEN** 兩張或更多卡片具有相同步行距離，或同時仍在查詢中
- **THEN** 系統 SHALL 以本次查詢的初始索引維持其相對次序
- **AND** callback 完成次序 SHALL NOT 成為 tie-break

#### Scenario: 常用頁只重排未置頂結果
- **WHEN** 常用頁目前按步行距離排序且一個步行狀態漸進更新
- **THEN** 系統 SHALL 保持所有置頂路線的 token 降序及身份不變
- **AND** 系統 SHALL 只按步行數值與查詢中規則重排未置頂路線

#### Scenario: 搜尋頁重排全部結果
- **WHEN** 搜尋 destination 目前按步行距離排序且一個步行狀態漸進更新
- **THEN** 系統 SHALL 按步行數值與查詢中規則重排全部搜尋結果
- **AND** 系統 SHALL NOT 建立置頂區域或置頂排序例外

#### Scenario: 其他排序只局部刷新卡片
- **WHEN** 目前使用路線、價格、耗時或候車時間排序且步行狀態漸進更新
- **THEN** 系統 SHALL 保留目前結果次序並只刷新受影響卡片內容

#### Scenario: 步行更新不改變結果身份
- **WHEN** 卡片由查詢中變為 CSDI 成功或 Citybus 回退
- **THEN** 該卡片 SHALL 保持相同 result identity
- **AND** 系統 SHALL NOT 增加、刪除或重複任何路線結果

#### Scenario: 步行重排保持目前閱讀位置
- **WHEN** CSDI 漸進狀態令目前步行排序結果發生位置變化
- **THEN** 系統 SHALL 在提交前保存第一可見路線 stable id 與相對列表頂部 pixel offset，並在提交後恢復
- **AND** 若該路線已消失，系統 SHALL 選擇新排序中最接近的下一張路線而非跳至列表頂部
