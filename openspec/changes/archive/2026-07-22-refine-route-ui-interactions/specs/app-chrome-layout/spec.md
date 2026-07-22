## ADDED Requirements

### Requirement: 頂層導航以穩定膠囊持續標示目前頁面
系統 SHALL 讓常用、搜尋與設定三個底部導航項保持固定等寬量度，並以不改變佈局尺寸的選中膠囊、圖示色和標籤字重持續標示目前 destination。

#### Scenario: 選中項使用固定量度
- **WHEN** 用戶選中任一底部導航項
- **THEN** 選中與未選中圖示 SHALL 均保持 24dp
- **AND** 選中圖示背後 SHALL 顯示 64×32dp 膠囊背景
- **AND** 膠囊 SHALL 只包圍圖示且不得與標籤重疊

#### Scenario: 選中標籤持續可辨識
- **WHEN** 導航切換動畫結束
- **THEN** 目前項目 SHALL 持續保留膠囊背景、選中前景色及 13sp Bold 標籤
- **AND** 未選中標籤 SHALL 使用 12sp Regular
- **AND** 相鄰項目 SHALL NOT 因選中狀態改變位置

#### Scenario: 導航切換動畫不阻塞操作
- **WHEN** 用戶切換底部導航項
- **THEN** 系統 SHALL 以約 150ms 的顏色或透明度過渡更新狀態
- **AND** 動畫 SHALL NOT 阻止再次點擊、返回或頁面內容互動

#### Scenario: 深色與大字體保持相同結構
- **WHEN** App 使用深色模式或 font scale 2.0
- **THEN** 導航 SHALL 使用對應語義色維持相同膠囊和圖示尺寸
- **AND** 導航列 SHALL 可增加高度以完整展示標籤
- **AND** 標籤 SHALL NOT 被裁切或與圖示重疊
