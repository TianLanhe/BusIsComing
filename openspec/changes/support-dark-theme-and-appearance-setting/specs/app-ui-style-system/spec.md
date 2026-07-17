## MODIFIED Requirements

### Requirement: 路線相關頁面採用統一現代通勤風格
系統 SHALL 讓常用、搜尋、設定、路線管理頁和路線編輯頁在淺色及深色模式下均採用 `docs/ui-style-guide.md` 中定義的「安靜實用的現代通勤工具」風格。

#### Scenario: 常用與搜尋頁使用統一風格
- **WHEN** 用戶打開常用或搜尋 destination
- **THEN** 頁面 SHALL 使用清晰任務語義、路線選擇或起終點表單、主查詢按鈕、狀態區域和結果卡片組成可掃讀畫面
- **AND** 頁面 SHALL 使用模式對應的專案主色、輔助色、語意表面、克制圓角和清楚字號層級

#### Scenario: 設定與路線管理頁使用統一風格
- **WHEN** 用戶打開設定 destination 或路線管理頁
- **THEN** 頁面 SHALL 與常用頁保持一致的模式對應背景、標題層級、按鈕層級、卡片樣式和間距節奏

#### Scenario: 路線編輯頁使用統一風格
- **WHEN** 用戶打開新增、編輯或複製路線頁面
- **THEN** 頁面 SHALL 以輕量表單方式展示路線名稱、起點、終點、保存和返回操作
- **AND** 頁面 SHALL 避免呈現為後台配置表單或純文本堆疊介面

### Requirement: 完整頁面使用統一淡綠根背景
系統 SHALL 在淺色模式保留統一淡綠到近白根背景，並 SHALL 在深色模式使用同一資訊層級對應的深青綠根背景與實體表面。

#### Scenario: 淺色模式保留既有根背景
- **WHEN** 用戶在淺色模式打開常用、搜尋、設定、路線管理頁或路線編輯頁
- **THEN** 頁面 SHALL 保持現有淡綠到近白的克制漸變背景
- **AND** 底部導航、卡片、設定列、狀態區、排序 chips、表單和候選列表 SHALL 保持現有白色或淺色實體表面層級
- **AND** 系統 SHALL NOT 因語意色整理而改變既有資訊架構、間距、字體、圓角或排序

#### Scenario: 深色模式使用深青綠根背景
- **WHEN** 用戶在深色模式打開常用、搜尋、設定、路線管理頁或路線編輯頁
- **THEN** 頁面 SHALL 使用「深青綠夜行」方向的深青綠到近黑根背景
- **AND** 底部導航、卡片、設定列、狀態區、排序 chips、表單和候選列表 SHALL 使用比根背景清楚可辨識的深色實體表面
- **AND** 文字、圖示、描邊及選中態 SHALL NOT 與其背景融在一起

#### Scenario: 路線編輯表單在兩種模式保持可用
- **WHEN** 用戶在任一外觀模式打開新增、編輯或複製路線頁
- **THEN** 路線編輯頁的主要表單功能區 SHALL 使用模式對應的克制漸變或實體承載面
- **AND** 路線名稱、起點、終點輸入框和候選列表 SHALL 保持可讀、可點選和可滾動
- **AND** 輸入框文字、hint、錯誤提示和候選內容 SHALL NOT 因模式變更而降低對比或重疊

#### Scenario: 底部彈層和對話框使用模式對應表面
- **WHEN** 系統顯示路線詳情、ETA、監控設定 Bottom Sheet，或保存、刪除及匯入確認 Dialog
- **THEN** 彈層和對話框 SHALL 使用目前外觀模式對應的實體表面及 on-surface 文字
- **AND** 深色模式 SHALL NOT 保留會與周圍深色內容衝突的固定白色根背景
- **AND** 系統 SHALL NOT 對彈層或對話框使用全局半透明玻璃化背景

## ADDED Requirements

### Requirement: 全部 App 自有介面使用模式感知語意色

系統 SHALL 讓所有 App 自有 Activity、Fragment、RecyclerView item、Drawable、動態 View、Material 元件、底部導航及系統欄使用目前外觀模式對應的語意色。

#### Scenario: 三個頂層 destination 與底部導航支援深色
- **WHEN** 用戶在深色模式切換常用、搜尋與設定 destination
- **THEN** 三個 Fragment 的根背景、卡片、表單、狀態與底部導航 SHALL 使用一致的深色語意表面
- **AND** 底部導航的圖示、label、選中 indicator、ripple 及未選狀態 SHALL 保持可辨識
- **AND** destination 切換 SHALL NOT 出現固定淺色閃爍或遺留上一頁顏色

#### Scenario: 設定與次級頁支援深色
- **WHEN** 用戶在深色模式打開設定、關於或路線匯入與匯出頁
- **THEN** 頁面根背景、資訊卡、設定列、表格、按鈕、Dialog 和狀態文字 SHALL 使用一致的深色語意表面
- **AND** 頁面 SHALL NOT 顯示未經語意化的白色表格列或白色卡片根背景

#### Scenario: 動態建立的元件支援目前模式
- **WHEN** 系統以 Kotlin 動態建立常用路線選擇列、搜尋結果、ETA、詳情或監控元件
- **THEN** 一般文字、卡片、chip、按鈕及 Drawable SHALL 從模式感知資源或主題屬性取色
- **AND** 一般 UI SHALL NOT 以固定日間白色取代 surface 或 on-surface 語意

#### Scenario: Material 互動狀態保持一致
- **WHEN** 用戶在任一外觀模式查看或操作按鈕、TextInputLayout、RadioButton、ripple、進度元件或選中／停用狀態
- **THEN** 元件 SHALL 使用目前模式對應的 primary、secondary、surface、error 及 on-color 語意
- **AND** 正常、按下、選中、停用及錯誤狀態 SHALL 保持可辨識

#### Scenario: 系統欄圖示保持可見
- **WHEN** 任一 App Activity 在支援的 Android 版本顯示 status bar 或 navigation bar
- **THEN** 系統欄背景與圖示明暗 SHALL 配合目前外觀模式
- **AND** Android 7.1 與近期 Android 版本 SHALL NOT 出現圖示與欄背景無法辨識的組合

#### Scenario: 三語與明暗資源共同生效
- **WHEN** 任一支援語言在淺色或深色模式顯示 App 自有畫面
- **THEN** 文案 SHALL 使用目前 locale 資源
- **AND** 表面與前景 SHALL 使用目前外觀模式的語意色
- **AND** locale 資源 SHALL NOT 覆蓋或固定日／夜色票

#### Scenario: 固定品牌與路線識別色保持受控
- **WHEN** App 顯示 App 圖示、巴士路線識別色或由系統／第三方控制的模板
- **THEN** 系統 SHALL 保留其必要固定色
- **AND** 固定色上的 App 自有前景文字或圖示 SHALL 仍滿足適用的對比要求

### Requirement: 明暗配色滿足可讀性與無障礙門檻

系統 SHALL 在淺色及深色模式提供可測量的前景／背景對比，並 SHALL 在大字體和輔助技術下保持操作可辨識。

#### Scenario: 一般文字對比
- **WHEN** App 顯示一般尺寸正文、站名、設定摘要或狀態文字
- **THEN** 文字與背景對比 SHALL 至少達到 4.5:1

#### Scenario: 大型文字與必要控制邊界對比
- **WHEN** App 顯示符合大型文字條件的標題、必要圖示或必要控制邊界
- **THEN** 前景或邊界與相鄰背景對比 SHALL 至少達到 3:1
- **AND** 非必要裝飾 divider SHALL NOT 被用作輸入框或必要控制的唯一邊界

#### Scenario: 大字體下保持內容可操作
- **WHEN** 用戶啟用大字體並打開任一受影響畫面或主題單選對話框
- **THEN** 短文案 SHALL 保持穩定對齊
- **AND** 文字、目前值、RadioButton 和主要操作 SHALL NOT 重疊、被裁切或失去可點擊區
