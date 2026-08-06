## ADDED Requirements

### Requirement: 地圖方向紋理嚴格沿路徑前進方向
系統 SHALL 以綁定同一有序路徑的開放折角顯示巴士及示意步行的前進方向，且每個折角 SHALL 按所在位置的局部切線定向。

#### Scenario: 彎曲巴士 geometry 顯示方向
- **WHEN** 一段巴士 geometry 包含直線、彎道或 S 彎
- **THEN** 地圖 SHALL 在帶白色描邊的分段色實線上稀疏重複稍粗的白色開放折角
- **AND** 每個折角 SHALL 隨其所在位置的局部曲線轉向
- **AND** 系統 SHALL NOT 使用整段起終點 bearing、手工角度、字體 glyph 或獨立 Marker 定向折角

#### Scenario: 有序 geometry 反轉
- **WHEN** 測試或上游資料把同一條 geometry 的點序反轉
- **THEN** 所有方向折角 SHALL 同步反轉並繼續貼合路徑
- **AND** 折角 SHALL NOT 保留原方向或漂離拐角

#### Scenario: 地圖步行軌跡顯示方向
- **WHEN** 起點步行、異站換乘步行或終點步行取得包含一個或更多有序 `geometry.paths` 的 CSDI 成功結果
- **THEN** 地圖 SHALL 只以較粗的灰色開放折角沿每個 CSDI 有序子路徑顯示前進方向
- **AND** 每個折角 SHALL 按所在位置的局部切線定向，子路徑空隙及端點之間 SHALL NOT 補畫直線
- **AND** 地圖 SHALL NOT 顯示灰色實線、點線或虛線底圖
- **AND** 系統 SHALL 把該軌跡描述為規劃預覽而非逐步導航或即時引導

#### Scenario: 步行軌跡查詢中或回退
- **WHEN** 步行段仍在查詢、SameStop、端點不可靠、CSDI 最終失敗或使用 Citybus 距離回退
- **THEN** 地圖 SHALL 保留可靠端點 marker 及其他已成功內容
- **AND** 地圖 SHALL NOT 為該段建立開放折角、直線、虛假軌跡或失敗佔位線

#### Scenario: 相機及增量更新保持紋理貼合
- **WHEN** 相機縮放、平移、bottom sheet padding 或漸進資料更新改變
- **THEN** 已顯示折角 SHALL 保持由同一 polyline 路徑排布及定向
- **AND** 增量 renderer SHALL NOT 造成折角相對路徑漂移、跳角或反向

#### Scenario: 方向紋理無法可靠建立
- **WHEN** 某段 polyline stamp 或等效內建路徑樣式無法建立或渲染結果不能保證局部切線方向
- **THEN** 系統 SHALL 保留可靠巴士實線或省略該步行紋理並記錄安全診斷
- **AND** 系統 SHALL NOT 回退為可能方向錯誤的手工 Marker 或固定角度圖標

### Requirement: 地圖站名按角色優先級動態避讓
系統 SHALL 以目前相機 projection、可見地圖範圍及固定角色優先級放置站名，讓關鍵站名可辨識而普通途經站不形成文字牆。

#### Scenario: 關鍵站名選擇可讀位置
- **WHEN** 地圖顯示查詢起點、查詢終點、上車、下車或同站換乘 marker
- **THEN** 系統 SHALL 依右、左、上、下候選位置評估視口、bottom sheet、系統 inset、目前可見 CSDI 署名、路徑、marker 及既有標籤碰撞
- **AND** 系統 SHALL 優先顯示關鍵站名並選擇無碰撞或衝突最少的位置
- **AND** 標籤 SHALL 與 marker 保持可辨識間距及可讀 halo

#### Scenario: 普通途經站按空間顯示
- **WHEN** 普通途經站位於目前地圖可見區域
- **THEN** 系統 SHALL 只在縮放及碰撞條件允許時顯示其站名
- **AND** 普通站名 SHALL 為起終點、上下車、換乘及已選普通站名讓位
- **AND** 放大或選中普通站後使用者 SHALL 可取得完整站名

#### Scenario: 相機停止後更新標籤
- **WHEN** 相機完成一次平移、縮放或程式化定位
- **THEN** 系統 SHALL 在 camera idle 後重新評估標籤
- **AND** 舊候選位置仍有效時 SHALL 保持原側，避免標籤反覆跳動
- **AND** 系統 SHALL NOT 在每個 camera move frame 重算文字碰撞

#### Scenario: 長站名及語言切換
- **WHEN** Citybus 站名在目前 `LanguageSnapshot` 為長英文、繁體或簡體文本
- **THEN** 地圖標籤 SHALL 使用該原文、單行限寬及省略呈現
- **AND** 完整名稱 SHALL 保留於 marker 互動、時間線及無障礙描述
- **AND** App SHALL NOT 自行翻譯或因語言切換改寫第三方站名

## MODIFIED Requirements

### Requirement: 地圖展示完整路線角色與示意步行
系統 SHALL 在地圖上展示所有可靠巴士站、分段巴士道路、查詢起終點、轉乘與成功 CSDI 步行軌跡，並以固定角色圖形、線型、文字、時間線及無障礙描述共同表達角色；頁面 SHALL NOT 顯示常駐路線圖例，亦 SHALL NOT 以端點直線冒充步行路線。

#### Scenario: 展示單段巴士路線
- **WHEN** 單段路線的站點與道路幾何可用
- **THEN** 系統 SHALL 使用與時間線一致的分段色繪製帶對比白色描邊實線
- **AND** 系統 SHALL 展示上車站、所有途經站與下車站
- **AND** 上車點 SHALL 為目前乘車段色實心圓內白色巴士正面圖形
- **AND** 下車點 SHALL 為目前乘車段色空心圓環內等比 `log-out` 圖形
- **AND** 普通途經站 SHALL 使用帶白色隔離邊緣的低強度中性小圓點且不預設顯示全部站名

#### Scenario: 展示查詢起終點
- **WHEN** 查詢起點或查詢終點坐標可用
- **THEN** 起點 SHALL 使用綠色、中心白色圓孔的地圖針
- **AND** 終點 SHALL 使用珊瑚紅、中心白色圓孔的地圖針
- **AND** 起終點角色 SHALL 以形狀及無障礙描述區分，不能只依賴顏色

#### Scenario: 展示多段轉乘路線
- **WHEN** 路線包含兩個或更多乘車段
- **THEN** 系統 SHALL 依乘車段次序使用可辨識的不同分段色
- **AND** marker SHALL 顯示其所屬路線與上車、下車或轉乘角色
- **AND** 顏色 SHALL NOT 是唯一角色資訊

#### Scenario: 展示同站轉乘
- **WHEN** Citybus 詳情把兩段路線標記為同站轉乘
- **THEN** 系統 SHALL 使用單一複合轉乘 marker
- **AND** marker SHALL 使用分別代表前後乘車段色的雙色圓環及中性環形換向箭頭
- **AND** 系統 SHALL NOT 疊放前段下車與後段上車 marker
- **AND** 系統 SHALL NOT 繪製步行紋理或步行距離

#### Scenario: 展示步行轉乘
- **WHEN** Citybus 詳情把兩段路線標記為步行前往轉車站且該 CSDI 分段成功
- **THEN** 系統 SHALL 保留前段下車站與後段上車站兩個角色
- **AND** 系統 SHALL 只沿 CSDI 回應的每個有序子路徑以較粗灰色開放折角表示步行軌跡
- **AND** 系統 SHALL NOT 顯示灰色實線、點線或虛線底圖
- **AND** 系統 SHALL NOT 以坐標相同為由改寫 Citybus 轉乘類型

#### Scenario: 展示首尾步行
- **WHEN** 起點或終點必要步行段取得 CSDI 成功結果
- **THEN** 系統 SHALL 只沿 CSDI 回應的每個有序子路徑使用較粗灰色開放折角展示首尾步行軌跡
- **AND** 系統 SHALL NOT 顯示灰色實線、點線或虛線底圖
- **AND** 系統 SHALL 把該紋理描述為規劃預覽而非逐步導航或即時引導

#### Scenario: 步行段查詢中或失敗
- **WHEN** 某步行段仍在查詢、SameStop、最終失敗、回退 Citybus 距離或端點不可可靠確定
- **THEN** 地圖 SHALL 保留該段端點 marker 及其他已成功巴士或步行內容
- **AND** 地圖 SHALL NOT 為該段繪製折角、直線、虛假軌跡或失敗佔位線

#### Scenario: 多個子路徑不補畫連接線
- **WHEN** 一個 CSDI 成功分段包含兩個或更多 `geometry.paths`
- **THEN** 每個子路徑 SHALL 以 `步行分段 + path 次序` 形成穩定渲染身份並獨立繪製
- **AND** 系統 SHALL NOT 在子路徑空隙、首尾 marker 或其他幾何之間補畫直線

#### Scenario: 漸進結果只更新對應步行軌跡
- **WHEN** CSDI 分段以任意次序成功或有效狀態被替換
- **THEN** renderer SHALL 依穩定子路徑身份只新增、更新或移除對應 path presentation
- **AND** 其他 marker、巴士幾何及成功步行軌跡 SHALL 保持不變

#### Scenario: 地圖不顯示路線圖例
- **WHEN** 地圖區域在摘要、半屏或全屏任一詳情窗檔位可見
- **THEN** 頁面 SHALL NOT 顯示「巴士路線」、「步行連接（示意）」或等效浮動圖例
- **AND** 圖例 SHALL NOT 保留空白容器、觸控目標或無障礙節點
- **AND** 巴士實線、方向折角、marker、時間線及無障礙描述 SHALL 保持可用

#### Scenario: 移除圖例後地圖安全區
- **WHEN** 圖例被移除且 WindowInsets 或 bottom sheet 高度改變
- **THEN** Google Logo、必要法律文字、CSDI 署名、返回、目前位置及全覽控件 SHALL 保持可見且不互相遮擋
- **AND** 頁面 SHALL NOT 新增另一個常駐說明卡取代圖例

### Requirement: 返回入口按詳情窗狀態遷移
系統 SHALL 在地圖可見狀態提供單一屏內返回入口，並 SHALL 在全屏詳情狀態把內容空間留給時間線而不新增屏內返回或標題列。

#### Scenario: 摘要或半屏返回入口
- **WHEN** bottom sheet 位於摘要態或半屏態
- **THEN** 返回按鈕 SHALL 以圓形浮動控件顯示在地圖左上安全區域
- **AND** 畫面 SHALL NOT 同時顯示第二個頁面返回入口

#### Scenario: 全屏不顯示屏內返回與標題
- **WHEN** bottom sheet 進入全屏態
- **THEN** 地圖浮動返回按鈕 SHALL 隨地圖隱藏
- **AND** 詳情窗 SHALL NOT 顯示「路線詳情」標題、Toolbar、App Bar 或任何屏內返回按鈕
- **AND** 內容 SHALL 在狀態列安全區及拖動把手之後直接鋪滿可用高度

#### Scenario: 任一檔位使用系統返回
- **WHEN** 用戶在摘要、半屏或全屏態使用 Android 系統返回手勢或按鍵
- **THEN** 系統 SHALL 直接關閉路線詳情並返回原結果上下文
- **AND** 系統 SHALL NOT 先逐段收合 bottom sheet
