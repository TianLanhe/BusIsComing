## ADDED Requirements

### Requirement: 三語畫面使用可伸縮版面
系統 SHALL 讓 App 自有畫面根據繁體、簡體及英文文字長度伸縮，不以不可讀縮字或固定容器裁切核心內容。

#### Scenario: 三個頂層 destination 與底部導航
- **WHEN** 常用、搜尋或設定 destination 及底部導航以任一支援語言顯示
- **THEN** destination 標題、導航 label、選中狀態及主要操作 SHALL 完整可理解
- **AND** 系統 SHALL NOT 以固定繁體中文字串或固定寬度造成英文及簡體文字裁切

#### Scenario: 核心文字超出單行
- **WHEN** action、狀態、錯誤或說明文字在目前語言無法於單行完整展示
- **THEN** 系統 SHALL 允許文字換行、容器增高或頁面滾動
- **AND** 系統 SHALL NOT 以小於既有可讀層級的自動縮字容納內容

#### Scenario: 窄屏無法容納並排核心操作
- **WHEN** 約 360dp 闊度無法同時容納多個重要操作
- **THEN** 系統 SHALL 以縱向排列或等效可達布局展示操作
- **AND** 每個主要操作 SHALL 保持至少 48dp 觸控高度

#### Scenario: 輔助控件超出可用闊度
- **WHEN** 排序 chip 或其他非核心橫向控件因語言長度超出可用闊度
- **THEN** 系統 SHALL 使用橫向滾動或等效可達布局
- **AND** 系統 SHALL NOT 令控件文字互相重疊或貼出畫面邊界

### Requirement: 三語大字體驗證覆蓋高風險畫面
系統 SHALL 對三種語言在淺色及深色模式下的高風險 XML 畫面執行可重現的大字體與無障礙驗證。

#### Scenario: font scale 1.3 高風險畫面
- **WHEN** 底部導航、常用／搜尋結果、地點候選、設定、ETA、詳情或監測畫面以 font scale 1.3 顯示
- **THEN** 系統 SHALL 保持文字可讀、action 可點擊、內容可滾動且無控件重疊

#### Scenario: locale 與 night 資源共同生效
- **WHEN** 任一支援語言分別在淺色與深色模式顯示同一畫面
- **THEN** 文案 SHALL 使用目前語言資源
- **AND** 表面、文字、圖示及互動狀態 SHALL 使用目前外觀模式的語意色
- **AND** locale 資源目錄 SHALL NOT 以固定色覆蓋 `values-night` 對應資源

#### Scenario: font scale 2.0 關鍵流程
- **WHEN** 三語關鍵流程以 font scale 2.0 顯示
- **THEN** 核心 action SHALL 保持可達
- **AND** Dialog action SHALL NOT 超出邊界
- **AND** Bottom Sheet 內容 SHALL 可滾動到末端

#### Scenario: TalkBack 讀取受控省略內容
- **WHEN** compact card 受控省略長站名或方向
- **THEN** TalkBack SHALL 可讀取完整內容及控件用途
- **AND** 系統 SHALL NOT 只靠顏色或被省略文字表達關鍵狀態
