## MODIFIED Requirements

### Requirement: 結果排序控件保持緊湊展示
系統 SHALL 在常用與搜尋的查詢結果區直接展示排序控件，不在排序項上方展示獨立標題文字，並 SHALL 讓兩個頁面使用相同的按鈕量度與選中狀態。

#### Scenario: 展示排序控件
- **WHEN** 用戶查詢出一條或多條巴士路線結果且排序控件可見
- **THEN** 系統 SHALL 直接展示可點擊排序項
- **AND** 系統 SHALL NOT 在排序項上方展示獨立的 `排序` 標題文字
- **AND** 排序項 SHALL 保留字段文案和當前排序方向

#### Scenario: 常用與搜尋排序樣式一致
- **WHEN** 常用頁或搜尋頁展示排序控件
- **THEN** 每個排序項 SHALL 使用 `48dp` 最小高度、`14dp` 水平內距、`13sp` 文字及 `8dp` 相鄰間距
- **AND** 未選中項 SHALL 使用普通表面、主要文字和分隔色描邊
- **AND** 選中項 SHALL 使用強調填充背景和高對比文字
- **AND** 常用與搜尋頁 SHALL 使用同一套 checkable button style 及狀態色

#### Scenario: 顯示目前排序方向
- **WHEN** 一個排序字段是目前選中項
- **THEN** 系統 SHALL 在字段文字後顯示升序或降序箭頭
- **AND** 箭頭變更 SHALL NOT 改變按鈕高度或令相鄰排序項跳動
- **AND** 「路線」排序 SHALL 繼續表示查詢結果路線，而非已保存行程

#### Scenario: 切換排序
- **WHEN** 用戶點擊任一排序項
- **THEN** 系統 SHALL 按既有排序規則切換排序字段或排序方向
- **AND** 系統 SHALL NOT 因視覺樣式統一而改變排序結果、刷新後排序恢復或 query owner

#### Scenario: 排序文案超出可用寬度
- **WHEN** 三語排序項總寬度超出結果區可用寬度
- **THEN** 排序列 SHALL 可水平滾動
- **AND** 系統 SHALL NOT 縮小、裁切或互相重疊字段文案

### Requirement: 首次引導示例卡沿用真實結果卡片
系統 SHALL 在首次引導頁的「路線結果預覽」中使用與真實查詢結果卡片一致的卡片布局、格式化規則和可讀性約束。

#### Scenario: 預覽卡使用真實結果卡片布局
- **WHEN** 系統在首次引導頁展示「路線結果預覽」
- **THEN** 預覽卡 SHALL 使用與查詢結果列表相同的結果卡片 layout 或等效共用視圖結構
- **AND** 預覽卡 SHALL 使用與真實結果卡片相同的路線號、站點預覽、候車狀態、下一班摘要、通知入口位置、分隔線和價格／耗時／步行資訊布局
- **AND** 系統 SHALL NOT 為首次引導頁手寫一套與真實結果卡片分離的靜態卡片 UI

#### Scenario: 預覽卡使用固定示例內容
- **WHEN** 系統渲染首次引導頁預覽卡
- **THEN** 預覽卡 SHALL 顯示路線 `118`
- **AND** 預覽卡 SHALL 顯示路徑 `柴灣 → 中環`
- **AND** 預覽卡 SHALL 顯示主候車狀態 `等候 4 分鐘`
- **AND** 預覽卡 SHALL 顯示下一班摘要 `下一班 11 分鐘 ›`
- **AND** 預覽卡 SHALL 顯示 `HK$ 11.8 · 耗時 38 分鐘 · 步行 160 米`
- **AND** 預覽內容 SHALL NOT 來自真實網絡查詢
- **AND** 預覽內容 SHALL NOT 寫入常用行程、搜尋查詢上下文或查詢結果列表

#### Scenario: 預覽卡不觸發真實結果卡交互
- **WHEN** 用戶查看或點擊首次引導頁的預覽卡
- **THEN** 系統 SHALL NOT 打開路線詳情
- **AND** 系統 SHALL NOT 打開首程 ETA 班次
- **AND** 系統 SHALL NOT 啟動通知欄監控
- **AND** 系統 SHALL NOT 發起巴士路線查詢
- **AND** 系統 SHALL NOT 向無障礙服務暴露無效 click action

#### Scenario: 真實結果卡片變更時預覽卡同步
- **WHEN** 後續版本調整真實結果卡片的字號、間距、欄位格式、右側候車區或底部資訊格式
- **THEN** 首次引導頁預覽卡 SHALL 透過共用 layout、formatter、binder 或等效封裝同步使用該變更
- **AND** 開發者 SHALL NOT 需要在另一套靜態預覽卡 UI 中重複修改同一視覺結構

### Requirement: 臨時查詢上下文條提供保存與編輯入口
系統 SHALL 在搜尋結果摘要中同時提供編輯與「儲存為常用行程」入口，讓用戶可基於目前起終點繼續編輯或建立可重複使用的行程；摘要與操作 SHALL 依可用寬度及字體比例採用確定的響應式排列。

#### Scenario: 搜尋結果顯示摘要與操作
- **WHEN** 用戶在搜尋頁使用有效起點和終點取得查詢結果
- **THEN** 系統 SHALL 在結果上方顯示本次搜尋的起點到終點摘要
- **AND** 系統 SHALL 顯示 `編輯` 入口
- **AND** 系統 SHALL 顯示「儲存為常用行程」入口
- **AND** 兩個入口 SHALL 具備獨立且不小於 `48dp` 高的觸控區

#### Scenario: 保存操作突出行程價值
- **WHEN** 搜尋結果摘要顯示編輯與保存操作
- **THEN** `編輯` SHALL 使用次要文字操作樣式
- **AND** 「儲存為常用行程」 SHALL 使用較明顯的 tonal 按鈕樣式
- **AND** 保存文案 SHALL 表達保存目前起點和終點行程，而非保存任一查詢結果路線

#### Scenario: 緊湊寬度使用縱向排列
- **WHEN** 畫面可用寬度小於 `600dp`
- **THEN** 起終點摘要 SHALL 顯示在操作列上方
- **AND** 編輯與保存操作 SHALL 顯示在摘要下方
- **AND** 長摘要或三語按鈕 SHALL NOT 互相重疊或裁切

#### Scenario: 大字體使用縱向排列
- **WHEN** font scale 大於或等於 `1.3`
- **THEN** 搜尋結果摘要與操作 SHALL 採用縱向排列
- **AND** 按鈕 SHALL 使用 `wrap_content` 高度及 `48dp` 最小高度
- **AND** 系統 SHALL NOT 以固定 `48dp` 高度裁切兩行文案

#### Scenario: 寬屏一般字體使用同列排列
- **WHEN** 畫面可用寬度大於或等於 `600dp`
- **AND** font scale 小於 `1.3`
- **THEN** 摘要與操作 SHALL 可顯示於同一列
- **AND** 起終點摘要 SHALL 使用剩餘可用寬度
- **AND** 操作 SHALL NOT 超出畫面邊界

#### Scenario: 點擊搜尋結果編輯入口
- **WHEN** 用戶點擊搜尋結果摘要中的 `編輯`
- **THEN** 系統 SHALL 將搜尋表單帶回可編輯位置
- **AND** 起點和終點 SHALL 保留目前搜尋上下文作為初始值
- **AND** 系統 SHALL NOT 自動保存行程
- **AND** 系統 SHALL NOT 因進入編輯而立即發起新查詢

#### Scenario: 點擊搜尋結果保存入口
- **WHEN** 用戶點擊「儲存為常用行程」
- **THEN** 系統 SHALL 沿用既有行程名稱輸入、重複校驗及保存流程
- **AND** 系統 SHALL 使用本次搜尋的起點和終點快照建立行程
- **AND** 系統 SHALL NOT 保存使用者點擊前正在查看的某一條路線結果

#### Scenario: 常用行程查詢不顯示搜尋摘要操作
- **WHEN** 用戶查看由已保存常用行程發起的查詢結果
- **THEN** 系統 SHALL NOT 顯示搜尋結果專用的編輯或保存入口
- **AND** 常用頁既有結果摘要、排序和刷新 SHALL 保持不變
