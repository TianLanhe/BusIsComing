# app-chrome-layout Specification

## Purpose
记录 App 顶部系统标题栏与页面内部标题、操作入口之间的布局约束，确保页面不被系统 ActionBar 遮挡。

## Requirements

### Requirement: 移除系统标题栏

系统 SHALL 不显示由应用主题自动生成的顶部 ActionBar 标题栏。

#### Scenario: 主界面不显示系统标题栏
- **WHEN** 用户打开主界面
- **THEN** 页面顶部不显示系统 ActionBar 中的 `BusIsComing` 标题

#### Scenario: 页面内容不被标题栏遮挡
- **WHEN** 用户打开主界面、路线管理页或路线编辑页
- **THEN** 页面内部标题和主要操作控件完整可见，不被系统标题栏覆盖

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

#### Scenario: 主界面保留自定义标题
- **WHEN** 用户打开主界面
- **THEN** 页面内部仍显示“巴士查询”标题和“管理路线”入口

#### Scenario: 路线管理页保留自定义标题
- **WHEN** 用户打开路线管理页
- **THEN** 页面内部仍显示“路线管理”标题和新增路线入口

### Requirement: 日夜主题表现一致

系统 SHALL 在日间主题和夜间主题下都移除系统标题栏。

#### Scenario: 日间主题无系统标题栏
- **WHEN** App 使用日间主题启动
- **THEN** 系统不显示 ActionBar 标题栏

#### Scenario: 夜间主题无系统标题栏
- **WHEN** App 使用夜间主题启动
- **THEN** 系统不显示 ActionBar 标题栏

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
