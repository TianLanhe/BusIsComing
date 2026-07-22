## MODIFIED Requirements

### Requirement: 保留页面内部标题和操作入口
系統 SHALL 使用頁面內部的任務語義、頂層底部導航和操作入口替代系統標題欄，並保留次級頁面的內部標題與返回操作。

#### Scenario: 頂層 destination 保留內部任務語義
- **WHEN** 用戶打開常用、搜尋或設定 destination
- **THEN** 頁面 SHALL 透過選中的底部導航、頁面內容標題或操作區表達目前任務
- **AND** 常用頁 SHALL 保留乘車碼與管理路線入口
- **AND** 設定頁 SHALL 不顯示返回上一頁操作

#### Scenario: 次級頁面保留內部標題和返回入口
- **WHEN** 用戶打開路線管理頁、路線編輯頁或關於頁
- **THEN** 頁面 SHALL 保留與該頁面對應的內部標題和返回入口
- **AND** 頁面內容與操作控件 SHALL 不被系統標題欄覆蓋
