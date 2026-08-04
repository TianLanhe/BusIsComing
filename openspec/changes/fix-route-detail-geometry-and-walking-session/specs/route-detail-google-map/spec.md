## MODIFIED Requirements

### Requirement: 地圖展示完整路線角色與示意步行
系統 SHALL 在地圖上展示所有可靠巴士站、分段巴士道路、查詢起終點、轉乘與示意步行，並以形狀、線型、文字、時間線及無障礙描述共同表達角色；頁面 SHALL NOT 顯示常駐路線圖例。

#### Scenario: 展示單段巴士路線
- **WHEN** 單段路線的站點與道路幾何可用
- **THEN** 系統 SHALL 使用與時間線一致的分段色繪製帶對比描邊實線
- **AND** 系統 SHALL 展示上車站、所有途經站與下車站
- **AND** 普通途經站 SHALL 使用低強度小圓點且不預設顯示全部站名

#### Scenario: 展示多段轉乘路線
- **WHEN** 路線包含兩個或更多乘車段
- **THEN** 系統 SHALL 依乘車段次序使用可辨識的不同分段色
- **AND** marker SHALL 顯示其所屬路線與上車、下車或轉乘角色
- **AND** 顏色 SHALL NOT 是唯一角色資訊

#### Scenario: 展示同站轉乘
- **WHEN** Citybus 詳情把兩段路線標記為同站轉乘
- **THEN** 系統 SHALL 使用單一複合轉乘 marker
- **AND** 系統 SHALL NOT 繪製步行虛線或步行距離

#### Scenario: 展示步行轉乘
- **WHEN** Citybus 詳情把兩段路線標記為步行前往轉車站
- **THEN** 系統 SHALL 保留前段下車站與後段上車站兩個角色
- **AND** 系統 SHALL 以灰色虛線表示示意步行連接
- **AND** 系統 SHALL NOT 以坐標相同為由改寫 Citybus 轉乘類型

#### Scenario: 展示首尾步行
- **WHEN** 查詢起點、首段上車站、末段下車站與查詢終點坐標可用
- **THEN** 系統 SHALL 使用灰色示意虛線展示首尾步行連接
- **AND** 系統 SHALL NOT 把該虛線描述為真實沿街導航

#### Scenario: 地圖不顯示路線圖例
- **WHEN** 地圖區域在摘要、半屏或全屏任一詳情窗檔位可見
- **THEN** 頁面 SHALL NOT 顯示「巴士路線」、「步行連接（示意）」或等效浮動圖例
- **AND** 圖例 SHALL NOT 保留空白容器、觸控目標或無障礙節點
- **AND** 巴士實線、示意步行虛線、marker、時間線及無障礙描述 SHALL 保持可用

#### Scenario: 移除圖例後地圖安全區
- **WHEN** 圖例被移除且 WindowInsets 或 bottom sheet 高度改變
- **THEN** Google Logo、必要法律文字、返回、目前位置及全覽控件 SHALL 保持可見且不互相遮擋
- **AND** 頁面 SHALL NOT 新增另一個常駐說明卡取代圖例
