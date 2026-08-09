## ADDED Requirements

### Requirement: 地圖控件使用一致圓形操作語言
系統 SHALL 讓路線詳情的返回、目前位置及全覽路線使用一致的圓形控件、居中圖標與可辨識操作語義，而 SHALL NOT 以逐控件視覺補償造成位置漂移。

#### Scenario: 顯示三個地圖控件
- **WHEN** 路線詳情地圖顯示成功
- **THEN** 返回、目前位置及全覽路線 SHALL 使用 `48dp` 圓形控件
- **AND** 三個控件 SHALL 使用位於外框幾何中心的 `24dp` 圖標
- **AND** 三個控件 SHALL 保持各自目前的 content description、位置、觸控及點擊行為

#### Scenario: 顯示全覽路線圖標
- **WHEN** 全覽路線控件可見
- **THEN** 圖標 SHALL 以端點及相連路徑表達目前路線全覽
- **AND** 圖標 SHALL NOT 使用掃描框、二維碼、泛用地圖或單純全屏展開語義

## MODIFIED Requirements

### Requirement: 地圖展示完整路線角色與示意步行
系統 SHALL 在地圖上展示所有可靠巴士站、分段巴士道路、查詢起終點、轉乘與成功 CSDI 步行軌跡，並以固定角色圖形、線型、文字、時間線及無障礙描述共同表達角色；頁面 SHALL NOT 顯示常駐路線圖例，亦 SHALL NOT 以端點直線冒充步行路線。

#### Scenario: 展示單段巴士路線
- **WHEN** 單段路線的站點與道路幾何可用
- **THEN** 系統 SHALL 使用與時間線一致的分段色繪製帶對比白色描邊實線
- **AND** 系統 SHALL 展示上車站、所有途經站與下車站
- **AND** 上車點 SHALL 為目前乘車段色實心圓內白色巴士正面圖形
- **AND** 下車點 SHALL 為目前乘車段色不透明實心圓、對比白色外框及等比白色 `log-out` 圖形
- **AND** 普通途經站 SHALL 使用帶白色隔離邊緣的低強度中性小圓點且不預設顯示全部站名

#### Scenario: 展示查詢起終點
- **WHEN** 查詢起點或查詢終點坐標可用
- **THEN** 起點 SHALL 使用綠色、中心白色圓孔的地圖針
- **AND** 終點 SHALL 使用珊瑚紅、中心白色圓孔的地圖針
- **AND** 起終點角色 SHALL 以形狀及無障礙描述區分，不能只依賴顏色

#### Scenario: 展示多段轉乘路線
- **WHEN** 路線包含兩個或更多乘車段
- **THEN** 系統 SHALL 依乘車段次序使用可辨識的不同分段色
- **AND** marker SHALL 顯示其所屬路線與上車、下車或轉乘角色
- **AND** 顏色 SHALL NOT 是唯一角色資訊

#### Scenario: 展示同站轉乘
- **WHEN** Citybus 詳情把兩段路線標記為同站轉乘
- **THEN** 系統 SHALL 使用單一複合轉乘 marker
- **AND** marker SHALL 使用分別代表前後乘車段色的雙色圓環及中性環形換向箭頭
- **AND** 系統 SHALL NOT 疊放前段下車與後段上車 marker
- **AND** 系統 SHALL NOT 繪製步行紋理或步行距離

#### Scenario: 展示步行轉乘
- **WHEN** Citybus 詳情把兩段路線標記為步行前往轉車站且該 CSDI 分段成功
- **THEN** 系統 SHALL 保留前段下車站與後段上車站兩個角色
- **AND** 系統 SHALL 只沿 CSDI 回應的每個有序子路徑以較粗灰色開放折角表示步行軌跡
- **AND** 系統 SHALL NOT 顯示灰色實線、點線或虛線底圖
- **AND** 系統 SHALL NOT 以坐標相同為由改寫 Citybus 轉乘類型

#### Scenario: 展示首尾步行
- **WHEN** 起點或終點必要步行段取得 CSDI 成功結果
- **THEN** 系統 SHALL 只沿 CSDI 回應的每個有序子路徑使用較粗灰色開放折角展示首尾步行軌跡
- **AND** 系統 SHALL NOT 顯示灰色實線、點線或虛線底圖
- **AND** 系統 SHALL 把該紋理描述為規劃預覽而非逐步導航或即時引導

#### Scenario: 步行段查詢中或失敗
- **WHEN** 某步行段仍在查詢、SameStop、最終失敗、回退 Citybus 距離或端點不可可靠確定
- **THEN** 地圖 SHALL 保留該段端點 marker 及其他已成功巴士或步行內容
- **AND** 地圖 SHALL NOT 為該段繪製折角、直線、虛假軌跡或失敗佔位線

#### Scenario: 多個子路徑不補畫連接線
- **WHEN** 一個 CSDI 成功分段包含兩個或更多 `geometry.paths`
- **THEN** 每個子路徑 SHALL 以 `步行分段 + path 次序` 形成穩定渲染身份並獨立繪製
- **AND** 系統 SHALL NOT 在子路徑空隙、首尾 marker 或其他幾何之間補畫直線

#### Scenario: 漸進結果只更新對應軌跡
- **WHEN** CSDI 分段以任意次序成功或有效狀態被替換
- **THEN** renderer SHALL 依穩定子路徑身份只新增、更新或移除對應 path presentation
- **AND** 其他 marker、巴士幾何及成功步行軌跡 SHALL 保持不變

#### Scenario: 地圖不顯示路線圖例
- **WHEN** 地圖區域在摘要、半屏或全屏任一詳情窗檔位可見
- **THEN** 頁面 SHALL NOT 顯示「巴士路線」、「步行連接（示意）」或等效浮動圖例
- **AND** 圖例 SHALL NOT 保留空白容器、觸控目標或無障礙節點
- **AND** 巴士實線、方向折角、marker、時間線及無障礙描述 SHALL 保持可用

#### Scenario: 移除圖例後地圖安全區
- **WHEN** 圖例被移除且 WindowInsets 或 bottom sheet 高度改變
- **THEN** Google Logo、必要法律文字、CSDI 署名、返回、目前位置及全覽控件 SHALL 保持可見且不互相遮擋
- **AND** 頁面 SHALL NOT 新增另一個常駐說明卡取代圖例
