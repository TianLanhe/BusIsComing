## ADDED Requirements

### Requirement: 常用頁以緊湊標題列與單一捲動體系優先展示結果
系統 SHALL 讓常用行程標題、快捷卡、查詢控制、排序、結果摘要和路線列表形成單一垂直捲動體系，並只固定與目前結果直接相關的排序及摘要。

#### Scenario: 常用行程標題與操作同列
- **WHEN** 常用頁存在已保存行程
- **THEN** 「常用行程」、「全部」與「管理」SHALL 位於同一水平列並沿同一基線排列
- **AND** 系統 SHALL NOT 新增獨立的「常用」頁面大標題
- **AND** 「全部」與「管理」SHALL 各自保留至少 48dp 觸控範圍

#### Scenario: 非結果控制隨內容捲走
- **WHEN** 用戶在已有路線結果的常用頁向下捲動
- **THEN** 常用行程標題、快捷卡和查詢按鈕 SHALL 隨頁面移出畫面
- **AND** 只有排序項及路線數量／更新時間摘要 SHALL 吸頂
- **AND** 吸頂內容 SHALL NOT 遮擋 Insets 或第一張結果卡

#### Scenario: 常用頁收緊非結果間距
- **WHEN** 常用頁顯示一般查詢狀態或結果
- **THEN** 主要內容水平邊距 SHALL 為 16dp
- **AND** 相鄰功能區 SHALL 使用約 8 至 12dp 的垂直節奏
- **AND** 視覺收緊 SHALL NOT 把操作觸控高度降至 48dp 以下

#### Scenario: 首次空狀態突出保存常用行程
- **WHEN** App 沒有任何已保存常用行程且常用頁沒有查詢上下文
- **THEN** 首次狀態 SHALL 以「新增常用行程」作為主要行動
- **AND** 系統 SHALL NOT 在首次狀態顯示「搜尋路線」次要行動
- **AND** 系統 SHALL NOT 在常用頁顯示乘車碼入口

## REMOVED Requirements

### Requirement: 主頁頂部提供乘車碼與管理路線入口
**Reason**: 乘車碼改由系統快捷方式和候車通知提供，設定已成為獨立頂層 destination；常用頁只保留同列的「全部／管理」行程操作。

**Migration**: 移除常用頁乘車碼與設定圖示；管理行程入口移至「常用行程」同一標題列，乘車碼使用靜態 shortcut、pinned shortcut 或監控通知 action。
