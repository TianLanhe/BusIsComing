## ADDED Requirements

### Requirement: 首次引導示例卡沿用真實結果卡片
系統 SHALL 在首次引導頁的 `示例預覽` 中使用與真實查詢結果卡片一致的卡片布局、格式化規則和可讀性約束。

#### Scenario: 示例卡使用真實結果卡片布局
- **WHEN** 系統在首次引導頁展示 `示例預覽`
- **THEN** 示例卡 SHALL 使用與查詢結果列表相同的結果卡片 layout 或等效共用視圖結構
- **AND** 示例卡 SHALL 使用與真實結果卡片相同的路線號、站點預覽、候車狀態、下一班摘要、通知入口位置、分隔線和價格／耗時／步行資訊布局
- **AND** 系統 SHALL NOT 為首次引導頁手寫一套與真實結果卡片分離的靜態卡片 UI

#### Scenario: 示例卡使用固定示例內容
- **WHEN** 系統渲染首次引導頁示例卡
- **THEN** 示例卡 SHALL 顯示路線 `118`
- **AND** 示例卡 SHALL 顯示路徑 `柴灣 → 中環`
- **AND** 示例卡 SHALL 顯示主候車狀態 `等候 4 分鐘`
- **AND** 示例卡 SHALL 顯示下一班摘要 `下一班 11 分鐘 ›`
- **AND** 示例卡 SHALL 顯示 `HK$ 11.8 · 耗時 38 分鐘 · 步行 160 米`
- **AND** 示例內容 SHALL NOT 來自真實網路查詢
- **AND** 示例內容 SHALL NOT 寫入常用路線、臨時查詢上下文或查詢結果列表

#### Scenario: 示例卡不觸發真實結果卡交互
- **WHEN** 用戶查看或點擊首次引導頁的示例卡
- **THEN** 系統 SHALL NOT 打開路線詳情
- **AND** 系統 SHALL NOT 打開首程 ETA 班次
- **AND** 系統 SHALL NOT 啟動通知欄監控
- **AND** 系統 SHALL NOT 發起巴士路線查詢
- **AND** 系統 SHALL NOT 顯示 `不可點擊` 文案

#### Scenario: 真實結果卡片變更時示例卡同步
- **WHEN** 後續版本調整真實結果卡片的字號、間距、欄位格式、右側候車區或底部資訊格式
- **THEN** 首次引導頁示例卡 SHALL 透過共用 layout、formatter、binder 或等效封裝同步使用該變更
- **AND** 開發者 SHALL NOT 需要在另一套靜態示例卡 UI 中重複修改同一視覺結構
