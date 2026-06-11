## ADDED Requirements

### Requirement: 結果卡片提供通知欄監控入口

系統 SHALL 在可監控的路線結果卡片上提供低權重小鈴鐺入口，讓用戶可以手動啟動通知欄監控。

#### Scenario: 展示小鈴鐺入口
- **WHEN** 路線結果卡片包含可用首程 ETA 或可嘗試啟動監控的首程資訊
- **THEN** 卡片 SHALL 在候車狀態附近展示小鈴鐺圖示入口
- **AND** 小鈴鐺 SHALL 不新增獨立文字行

#### Scenario: 小鈴鐺低權重展示
- **WHEN** 系統展示結果卡片
- **THEN** 小鈴鐺圖示 SHALL 使用低權重樣式
- **AND** 小鈴鐺 SHALL NOT 比路線號或候車狀態更醒目

#### Scenario: 小鈴鐺觸控區
- **WHEN** 用戶點擊小鈴鐺入口
- **THEN** 系統 SHALL 將該點擊解釋為啟動通知欄監控
- **AND** 小鈴鐺入口 SHALL 提供不小於 48dp 的可觸控區域

#### Scenario: 保持卡片詳情入口
- **WHEN** 用戶點擊小鈴鐺以外的卡片區域
- **THEN** 系統 SHALL 保持既有打開路線詳情行為
- **AND** 小鈴鐺點擊 SHALL NOT 同時觸發路線詳情

#### Scenario: 窄屏不重疊
- **WHEN** 用戶在較窄手機豎屏或較大系統字體下查看結果卡片
- **THEN** 小鈴鐺 SHALL NOT 與路線號、候車狀態、站點預覽、價格、耗時或步行距離文字重疊
