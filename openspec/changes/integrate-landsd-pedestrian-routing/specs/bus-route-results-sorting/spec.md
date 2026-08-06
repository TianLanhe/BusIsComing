## ADDED Requirements

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
