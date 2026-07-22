# app-ui-style-system Specification

## Purpose
定义 BusIsComing 路线相关页面的统一视觉、状态反馈和动效基线，确保后续 UI 变更默认遵循项目风格指南。
## Requirements

### Requirement: 路线相关页面采用统一现代通勤风格
系統 SHALL 讓常用、搜尋、設定、路線管理頁和路線編輯頁採用 `docs/ui-style-guide.md` 中定義的「安靜實用的現代通勤工具」風格。

#### Scenario: 常用與搜尋頁使用統一風格
- **WHEN** 用戶打開常用頁或搜尋頁
- **THEN** 頁面 SHALL 使用清晰的任務標題或操作區、主要查詢控制、狀態區域和結果卡片組成可掃讀的畫面
- **AND** 頁面 SHALL 使用項目主色、輔助色、淺色表面、克制圓角和清楚字號層級

#### Scenario: 設定與路線管理頁使用統一風格
- **WHEN** 用戶打開設定頁或路線管理頁
- **THEN** 頁面 SHALL 與常用頁保持一致的背景、標題層級、按鈕層級、卡片樣式和間距節奏

#### Scenario: 路線編輯頁使用統一風格
- **WHEN** 用戶打開新增、編輯或複製路線頁
- **THEN** 頁面 SHALL 以輕量表單方式展示路線名稱、起點、終點、保存和返回操作
- **AND** 頁面 SHALL 避免呈現為後台配置表單或純文本堆疊界面

#### Scenario: 主查询页使用统一风格
- **WHEN** 用户打开主查询页
- **THEN** 页面 SHALL 使用清晰标题、路线选择、主查询按钮、状态区域和结果卡片组成第一屏
- **AND** 页面 SHALL 使用项目主色、辅助色、浅色表面、克制圆角和清楚字号层级

#### Scenario: 路线管理页使用统一风格
- **WHEN** 用户打开路线管理页
- **THEN** 页面 SHALL 与主查询页保持一致的背景、标题层级、按钮层级、卡片样式和间距节奏

#### Scenario: 路线编辑页使用统一风格
- **WHEN** 用户打开新增、编辑或克隆路线页面
- **THEN** 页面 SHALL 以轻量表单方式展示路线名称、起点、终点、保存和返回操作
- **AND** 页面 SHALL 避免呈现为后台配置表单或纯文本堆叠界面

### Requirement: 页面状态反馈使用轻量现代样式
系统 SHALL 使用状态卡、状态区域或控件内进度反馈表达加载、空状态、失败和操作反馈。

#### Scenario: 查询状态可感知
- **WHEN** 系统正在查询路线、无路线结果或查询失败
- **THEN** 用户 SHALL 看到与当前状态对应的现代化状态区域，而不是只看到普通文本行

#### Scenario: 空状态提供下一步入口
- **WHEN** 主查询页或路线管理页没有可用路线配置
- **THEN** 系统 SHALL 展示空状态说明和新增路线入口

#### Scenario: 动画不阻塞操作
- **WHEN** 页面展示查询、排序、交换起终点或列表进入反馈
- **THEN** 动画 SHALL 保持在 150ms 到 300ms 的轻量范围内
- **AND** 动画 SHALL NOT 阻塞输入、滚动、返回或再次点击

### Requirement: UI 变更引用项目风格指南
涉及 BusIsComing 页面展示或交互的 OpenSpec 变更 SHALL 以 `docs/ui-style-guide.md` 作为默认视觉和交互基线。

#### Scenario: 新增或改造页面的设计说明
- **WHEN** 后续 OpenSpec change 涉及页面展示、列表、表单、状态或动效
- **THEN** 对应 `design.md` SHALL 说明是否遵循 `docs/ui-style-guide.md`
- **AND** 若偏离该指南，`design.md` SHALL 说明偏离原因

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

#### Scenario: 常用頁使用統一根背景
- **WHEN** 用戶打開常用頁
- **THEN** 常用頁根容器 SHALL 使用淡綠到近白的克制漸變背景
- **AND** 首次引導頁、常用路線區塊、查詢結果區和結果列表背後 SHALL 透出同一根背景
- **AND** 查詢結果卡片、狀態卡和排序 chips SHALL 保持白色或淺色實體表面

#### Scenario: 搜尋頁使用統一根背景
- **WHEN** 用戶打開搜尋頁
- **THEN** 搜尋頁根容器 SHALL 使用與常用頁一致的淡綠到近白根背景
- **AND** 起終點表單、候選列表、搜尋摘要、狀態卡和結果卡 SHALL 保持清楚可辨識的實體表面或主色按鈕樣式

#### Scenario: 路線管理與編輯頁使用統一根背景
- **WHEN** 用戶打開路線管理頁或新增、編輯、複製路線頁
- **THEN** 對應根容器 SHALL 使用與常用頁一致的淡綠到近白根背景
- **AND** 表單、候選列表、空狀態卡和主要操作 SHALL 保持可讀、可點選和可捲動

#### Scenario: 彈層和對話框保持穩定表面
- **WHEN** 系統顯示路線詳情底部彈層、ETA 底部彈層、保存對話框或刪除確認對話框
- **THEN** 彈層和對話框內容表面 SHALL 保持既有白色或淺色實體表面
- **AND** 系統 SHALL NOT 對彈層或對話框使用全局半透明玻璃化背景

#### Scenario: 主查詢頁使用統一根背景
- **WHEN** 用戶打開主查詢頁
- **THEN** 主查詢頁根容器 SHALL 使用淡綠到近白的克制漸變背景
- **AND** 無路線首次引導頁、常用路線區塊、查詢結果區和結果列表背後 SHALL 透出同一根背景
- **AND** 查詢結果卡片、狀態卡、排序 chips 和臨時查詢上下文條 SHALL 保持白色或淺色實體表面
- **AND** 系統 SHALL NOT 讓正文文本直接浮在高對比、複雜或裝飾性漸變上

#### Scenario: 路線管理頁使用統一根背景
- **WHEN** 用戶打開路線管理頁
- **THEN** 路線管理頁根容器 SHALL 使用與主查詢頁一致的淡綠到近白根背景
- **AND** 路線卡片、空狀態卡和主要操作按鈕 SHALL 保持清楚可辨識的實體表面或主色按鈕樣式

#### Scenario: 路線編輯頁使用統一根背景
- **WHEN** 用戶打開新增、編輯或複製路線頁
- **THEN** 路線編輯頁根容器 SHALL 使用與主查詢頁一致的淡綠到近白根背景
- **AND** 路線編輯頁的主要表單功能區 SHALL 使用克制的淡色漸變承載面，位置 SHALL 接近原有垂直居中的表單體驗
- **AND** 路線名稱、起點、終點輸入框和候選列表 SHALL 保持可讀、可點選和可滾動
- **AND** 輸入框文字、hint、錯誤提示和候選內容 SHALL NOT 因背景變更而降低對比或重疊

#### Scenario: 底部彈層和對話框保持穩定表面
- **WHEN** 系統顯示臨時查詢底部彈層、路線詳情底部彈層、ETA 底部彈層、保存對話框或刪除確認對話框
- **THEN** 彈層和對話框內容表面 SHALL 保持既有白色或淺色實體表面
- **AND** 系統 SHALL NOT 對彈層或對話框使用全局半透明玻璃化背景

### Requirement: 首次引導頁使用克制進入動效
系統 SHALL 在首次引導頁顯示時使用輕量、非阻塞的進入動效，並在系統動畫關閉時直接展示最終狀態。

#### Scenario: 首次引導內容依次進入
- **WHEN** 系統顯示首次引導頁
- **THEN** 主標題、路線結果預覽卡片和「新增常用行程」按鈕 SHALL 以淡入或輕微上移的方式依次出現
- **AND** 單段動畫時長 SHALL 維持在 150ms 到 250ms 範圍
- **AND** 動效 SHALL NOT 循環播放
- **AND** 動效 SHALL NOT 改變最終布局尺寸

#### Scenario: 動效不阻塞操作
- **WHEN** 首次引導頁進入動效正在執行
- **THEN** 用戶 SHALL 能在內容可見後點擊「新增常用行程」或「乘車碼」
- **AND** 頁面 SHALL NOT 顯示或等待頁內一次性查詢次按鈕
- **AND** 動效 SHALL NOT 阻塞底部搜尋導航、返回、旋轉、Activity 暫停或恢復

#### Scenario: 系統動畫關閉
- **WHEN** Android 系統動畫 scale 為 0 或等效設定表示動畫關閉
- **THEN** 首次引導頁 SHALL 直接顯示最終狀態
- **AND** 系統 SHALL NOT 依賴動畫完成回調才能讓按鈕可點擊

### Requirement: App 自有短文案保持自然字距
系統 SHALL 讓 App 自有 UI 中的短標題、主/次按鈕、text button、chips、短標籤、底部彈層標題、對話框標題和短操作項在常見 Android 版本下保持自然字距，避免被字符間兩端對齊或等效排版策略拉伸到整個容器寬度。

#### Scenario: 短文案保持自然字距
- **WHEN** 系統在 App 自有頁面、底部彈層或對話框中展示靜態短標題、主/次按鈕、text button、chips、短標籤或短操作項
- **THEN** 這些短文案 SHALL 使用自然字距展示
- **AND** 這些短文案 SHALL NOT 被字符間兩端對齊或等效排版策略拉伸到整個容器寬度
- **AND** 這些短文案 SHALL 在控件內保持語義完整可讀，不與圖標、邊框或相鄰控件重疊

#### Scenario: API 36.1 首頁首次引導短文案穩定
- **WHEN** 用戶在 API 36.1 模擬器、預設系統語言和 `font_scale=1.0` 下打開無常用路線的首頁首次引導
- **THEN** `新增常用路線` 和 `直接查詢一次` 等短按鈕文案 SHALL 保持自然字距
- **AND** 系統 SHALL NOT 將中文字逐字均勻分散到按鈕整行寬度

#### Scenario: API 36.1 臨時查詢底部彈層短文案穩定
- **WHEN** 用戶在 API 36.1 模擬器、預設系統語言和 `font_scale=1.0` 下打開臨時查詢底部彈層
- **THEN** `臨時查詢`、`使用此路線查詢` 和 `保存為常用` 等短文案 SHALL 保持自然字距
- **AND** 系統 SHALL NOT 將中文字逐字均勻分散到標題或按鈕整行寬度

#### Scenario: API 37 短文案保持一致可讀
- **WHEN** 用戶在 API 37 模擬器、預設系統語言和 `font_scale=1.0` 下查看首頁首次引導和臨時查詢底部彈層
- **THEN** 系統 SHALL 展示與 API 36.1 同等自然可讀的短文案
- **AND** 系統 MAY 因 Android 版本、狀態欄、導航欄、圓角、字體 fallback 或底部彈層高度產生非語義性的視覺差異
- **AND** 驗收 SHALL NOT 要求 API 36.1 與 API 37 截圖像素完全一致

#### Scenario: 長文案與動態內容不套用短文案規則
- **WHEN** 系統展示路線站名、用戶輸入、候選地點、Citybus 或 DATA.GOV.HK 動態返回內容、路線詳情長段落、隱私或說明正文，或由 Android 系統通知模板渲染的文本
- **THEN** 系統 SHALL 保留這些內容既有的換行、省略、對齊和可讀性策略
- **AND** 系統 SHALL NOT 為了修復短文案字距而強行套用短文案排版規則

#### Scenario: 大字體不因修復產生明顯回退
- **WHEN** Android 系統字體縮放大於預設值且 App 展示已套用短文案穩定策略的控件
- **THEN** 系統 SHALL NOT 因本修復新增全局強制單行或固定高度策略而造成短文案明顯重疊、不可辨識或與控件邊框衝突
- **AND** 既有單行按鈕、chips 或短標籤 MAY 保留原有省略或高度策略

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

### Requirement: 導航與搜尋優化在三語及深淺色保持相同層級
系統 SHALL 讓本 change 的底部導航、搜尋輸入器、欄位狀態、結果操作及排序控件在繁體、簡體、英文與淺色、深色模式下保持相同幾何、互動和資訊層級。

#### Scenario: 深淺色保持相同布局
- **WHEN** 用戶在淺色或深色模式查看底部導航、搜尋頁或首次空狀態
- **THEN** 控件尺寸、間距、圓角、展開位置、觸控區和焦點順序 SHALL 保持一致
- **AND** 系統 SHALL 只透過語意色切換表面、描邊、active indicator、選中內容及次要文字對比

#### Scenario: 三語文案使用資源並完整展示
- **WHEN** App 使用繁體、簡體或英文顯示新增或修改的導航、預覽、helper、錯誤及保存操作
- **THEN** 系統 SHALL 從對應 locale resource 取得自然文案
- **AND** 系統 SHALL NOT 在 XML 或 Kotlin 硬編碼 App 可見文案
- **AND** `Save as regular journey` 等長英文 SHALL NOT 與摘要、按鈕邊界或其他控件重疊

#### Scenario: 窄屏及大字體保持可操作
- **WHEN** 用戶在 `360dp` 寬度及 font scale `1.0／1.3／2.0` 查看受影響畫面
- **THEN** 核心文案 SHALL 透過換行、縱向重排或穩定增高保持完整可理解
- **AND** 系統 SHALL NOT 以縮小字體或裁切核心文字處理翻譯長度
- **AND** 圖示按鈕及主要操作 SHALL 保持不小於 `48dp` 的觸控區

### Requirement: 新互動元件跨語言與主題保持穩定
系統 SHALL 讓本 change 的站名預覽、搜尋輸入、排序摘要、導航選中態與乘車碼快捷入口在繁體中文、簡體中文、英文、淺色及深色模式使用同一資訊結構與語義色層級。

#### Scenario: 三語與深淺色使用相同結構
- **WHEN** 用戶切換 App 語言或外觀模式
- **THEN** 系統 SHALL 保持相同元件順序、觸控目標與功能可用性
- **AND** 所有 App 自有文案 SHALL 使用目前語言資源
- **AND** 系統 SHALL NOT 翻譯、縮寫或改寫第三方站名

#### Scenario: 窄屏與大字體保持核心操作可用
- **WHEN** App 在 360dp 寬度或 font scale 1.3 至 2.0 顯示相關頁面
- **THEN** 主要操作和至少 48dp 觸控目標 SHALL 保持可用
- **AND** 導航、輸入文字、按鈕與結果摘要 SHALL NOT 互相重疊
- **AND** 路線卡片站名僅可按規格尾部省略，完整名稱 SHALL 由詳情與無障礙描述提供

#### Scenario: 焦點和選中狀態具有足夠對比
- **WHEN** 搜尋輸入獲得焦點或導航、排序控制被選中
- **THEN** 淺色與深色主題 SHALL 使用對應的強調前景與容器語義色
- **AND** 狀態辨識 SHALL NOT 只依靠瞬時動畫

### Requirement: 底部導覽選中膠囊與標籤保持清楚分離
系統 SHALL 在三個頂層 destination 的底部導覽中保留穩定的選中膠囊、圖示和文字層級，且 SHALL NOT 讓選中背景與標籤重疊。

#### Scenario: 一般字體顯示選中項
- **WHEN** 系統以字體縮放 1.0 顯示底部導覽選中項
- **THEN** 選中膠囊 SHALL 保持約 `64×32dp`
- **AND** 圖示 SHALL 保持約 `24dp`
- **AND** 膠囊底緣與標籤頂緣 SHALL 保留約 `5dp` 的可見空隙
- **AND** 標籤字形底緣與所屬 item 底緣 SHALL 保留至少 `2dp` 的可見安全空間
- **AND** 選中標籤 SHALL 使用 `13sp` 粗體，未選中標籤 SHALL 使用 `12sp` 正常字重
- **AND** 選中狀態 SHALL NOT 改變三個 Tab 的寬度或導覽總高度

#### Scenario: 大字體顯示底部導覽
- **WHEN** 系統以字體縮放 1.3 或 2.0 顯示底部導覽
- **THEN** 導覽列 SHALL 允許增加必要高度以完整容納圖示、膠囊與標籤
- **AND** 膠囊、圖示及標籤 SHALL NOT 互相重疊或被裁切
- **AND** 每個 Tab SHALL 保持至少 `48dp` 可操作觸控範圍

#### Scenario: 切換深淺色與選中項
- **WHEN** 用戶切換底部導覽 destination 或 App 深淺色模式
- **THEN** 系統 SHALL 保持相同膠囊與標籤幾何
- **AND** 系統 SHALL 只使用對應主題的語意色更新選中與未選中狀態
- **AND** destination 切換完成後目前選中項 SHALL 持續可辨識

### Requirement: 吸頂結果控制器沿用頁面背景
系統 SHALL 讓常用與搜尋頁的吸頂排序／摘要控制器使用透明背景並沿用目前頁面背景色，使結果區在淺色及深色模式保持連續。

#### Scenario: 淺色模式顯示結果控制器
- **WHEN** 常用或搜尋頁在淺色模式顯示吸頂結果控制器
- **THEN** 控制器 SHALL 直接顯示頁面 `app_page_background`
- **AND** 控制器 SHALL NOT 顯示白色或其他固定 surface 色矩形背景

#### Scenario: 深色模式顯示結果控制器
- **WHEN** 常用或搜尋頁在深色模式顯示吸頂結果控制器
- **THEN** 控制器 SHALL 使用同一透明結構
- **AND** 排序、摘要、選中及停用狀態 SHALL 使用既有 `bus_*` 語意色保持可讀對比
