## ADDED Requirements

### Requirement: 頂層底部導航持續標示目前 destination
系統 SHALL 在「常用／搜尋／設定」三個頂層 destination 中持續標示目前選中項，並 SHALL 讓選中狀態在切換完成後、生命週期重建後及未進行觸控操作時仍可辨認。

#### Scenario: 選中項持續顯示
- **WHEN** 用戶打開任一頂層 destination
- **THEN** 對應導航項 SHALL 持續顯示膠囊形 active indicator
- **AND** 選中圖示 SHALL 以 `28dp` 視覺尺寸顯示
- **AND** 選中文字 SHALL 使用 `14sp` 粗體
- **AND** 點擊漣漪 SHALL 只作為短暫按下回饋，不得取代持續選中狀態

#### Scenario: 未選中項保持次要層級
- **WHEN** 一個頂層導航項不是目前 destination
- **THEN** 該項 SHALL 不顯示 active indicator
- **AND** 未選中圖示 SHALL 以 `24dp` 視覺尺寸顯示於固定 `28dp` 圖示槽
- **AND** 未選中文字 SHALL 使用 `12sp` 常規字重

#### Scenario: 切換 destination 不改變導航量度
- **WHEN** 用戶在三個頂層 destination 之間切換
- **THEN** 三個導航項 SHALL 保持等寬
- **AND** 圖示槽、文字區及導航欄在同一裝置與字體配置下 SHALL 保持穩定量度
- **AND** 頁面內容 SHALL NOT 因選中圖示或文字放大而上下跳動

#### Scenario: 重建後恢復選中項
- **WHEN** Activity 因旋轉、語言、主題或系統回收而重建
- **THEN** 系統 SHALL 讓目前恢復的 destination 對應導航項保持選中視覺
- **AND** 系統 SHALL NOT 短暫高亮另一個導航項作為最終狀態

#### Scenario: 大字體下導航文字完整
- **WHEN** 用戶在 `360dp` 寬度或 font scale `1.3／2.0` 下查看底部導航
- **THEN** 三個導航項 SHALL 保持可辨認和可點擊
- **AND** 導航欄 SHALL 可在該配置下使用足夠的統一高度
- **AND** 核心導航文字 SHALL NOT 互相重疊或被裁切為不可理解內容
