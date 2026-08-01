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

### Requirement: 輸入法不推移頂層底部導航
系統 SHALL 讓主 Activity 的頂層底部導航固定在屏幕物理底部；軟鍵盤顯示時 SHALL 覆蓋底部導航，使其暫時不可見，而不得把導航抬升到鍵盤上方。

#### Scenario: 搜尋輸入框拉起鍵盤
- **WHEN** 用戶在搜尋 destination 聚焦起點或終點輸入框並拉起軟鍵盤
- **THEN** 底部導航 SHALL 保持在屏幕物理底部
- **AND** 軟鍵盤 SHALL 覆蓋底部導航，使其暫時不可見
- **AND** 底部導航 SHALL NOT 顯示或重新定位到鍵盤上方

#### Scenario: 被鍵盤覆蓋的導航不可操作
- **WHEN** 軟鍵盤覆蓋底部導航
- **THEN** 用戶 SHALL NOT 能透過鍵盤上方的殘留區域觸發底部導航
- **AND** 被覆蓋的導航項 SHALL NOT 成為目前可見內容的無障礙焦點

#### Scenario: 收起鍵盤後恢復原位
- **WHEN** 用戶收起軟鍵盤
- **THEN** 底部導航 SHALL 在原屏幕底部位置重新可見
- **AND** 導航高度、三個項目的量度及目前選中 destination SHALL 保持不變
- **AND** 頁面 SHALL NOT 因導航恢復而產生額外跳動或錯誤切換

#### Scenario: 候選列表仍避開鍵盤
- **WHEN** 搜尋地點候選列表與軟鍵盤同時顯示
- **THEN** 候選列表 SHALL 依輸入法 Insets 限制自身可用高度並保持在鍵盤上方
- **AND** 底部導航的覆蓋策略 SHALL NOT 令候選項被鍵盤遮住

#### Scenario: 次級編輯頁行為不被改變
- **WHEN** 用戶在新增、編輯或複製行程等次級 Activity 拉起軟鍵盤
- **THEN** 該頁 SHALL 沿用既有內容避讓與操作可見性行為
- **AND** 主 Activity 的底部導航覆蓋策略 SHALL NOT 全域改寫次級 Activity 的窗口行為

#### Scenario: 系統版本與導航模式一致
- **WHEN** App 在受支援的舊版與新版 Android，以及手勢導航或三按鍵導航模式下顯示軟鍵盤
- **THEN** 頂層底部導航 SHALL 保持同一個「固定於物理底部並被鍵盤覆蓋」語義
- **AND** 系統導航 Insets SHALL NOT 令底部導航浮到鍵盤上方

### Requirement: 頂層查詢區只由結果列表帶動捲動
系統 SHALL 禁止用戶直接拖動常用行程或搜尋頁的頂部查詢區；只有有效結果列表發起的 nested scroll SHALL 可帶動頂部查詢區捲出或恢復。

#### Scenario: 直接拖動頂部不改變位置
- **WHEN** 用戶在常用行程快捷區、查詢按鈕、搜尋編輯器或折疊「本次行程」欄內上下滑動
- **THEN** 對應 AppBar offset SHALL 保持不變
- **AND** 此規則 SHALL 同時適用於有結果與無結果狀態

#### Scenario: 無結果時頂部固定
- **WHEN** 常用行程或搜尋頁沒有有效結果列表
- **THEN** 初始、查詢中、空結果及無保留結果的失敗狀態 SHALL NOT 產生頁面級 AppBar 位移
- **AND** 空狀態內容若需要內部捲動 SHALL NOT 把 nested scroll 傳給 AppBar

#### Scenario: 結果列表保留既有收折能力
- **WHEN** 頁面存在有效結果
- **AND** 用戶從結果列表開始上下滑動
- **THEN** 結果列表 SHALL 可帶動頂部查詢區捲出或恢復
- **AND** 結果控制器 SHALL 繼續按既有規則吸頂
