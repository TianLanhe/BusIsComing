## Context

目前 `MainActivity` 與 `SearchFragment` 點擊 `BusRouteOption` 後，分別透過 `RouteDetailBottomSheet` 按需呼叫 `CitybusRouteDetailRepository`。Repository 使用 `P2pRouteDetailQuery` 組裝 `getp2pstopinroute.php`，`CitybusRouteDetailParser` 只把回應轉為每段 `RouteDetailLeg` 的上車、途經與下車站；UI 再以動態 `LinearLayout`、`LegRailView` 與 `NestedScrollView` 組成底部彈層。

現有 fixture 已證明同一詳情回應還包含：起點及終點步行距離、步行前往轉車站／同站換乘、換乘步行距離、分段票價、分段預計上下車時間與最終到達時間。例如 N8P → N969 樣本的卡片步行距離為 378 米（243 + 135），詳情另有 25 米換乘步行，因此詳情完整步行總量為 403 米。舊模型只提供單一 `walkingDistanceMeters` 及可選 `originWalkingDistanceMeters`，無法表達這些來源與完整性。

路線卡片目前由 `RouteResultCardFormatter` 把價格、耗時及步行距離格式化為單一文字摘要。本變更只改造耗時／步行的視覺標記，不改變 `ppsearch_p3.php` 卡片距離、排序或查詢流程。

本設計遵循 `docs/ui-style-guide.md` 的「安靜實用的現代通勤工具」方向，以及 `docs/localization-guidelines.md` 的三語、動態資料與回退規則。後續將加入 Google 地圖，但本次不新增地圖依賴或空白佔位。

## Goals / Non-Goals

**Goals:**

- 以獨立全屏頁取代路線詳情 Bottom Sheet，並保持返回後的查詢結果、排序及捲動上下文。
- 把 Citybus 詳情解析為起點步行、乘車、換乘、後續乘車、終點步行的完整結構化行動鏈。
- 在詳情摘要顯示總票價、總耗時、預計到達、總途經站數及可判定時的完整步行距離。
- 以分段色粗實線、步行細虛線、節點形狀及文字共同表達路線結構。
- 清楚區分 Citybus 預計時刻與 DATA.GOV.HK 首程即時 ETA。
- 讓長路線、逐段途經站展開、三語、深淺色、大字體與 TalkBack 保持可用。
- 保留真實 HTTP 行為、語言隔離、快取、取消及測試注入點。

**Non-Goals:**

- 不接入 Google Maps 或繪製地圖折線；只確保頁面結構可在摘要與時間線之間插入可選地圖 item。
- 不修改卡片步行距離、步行排序或 `ppsearch_p3.php` 解析語義。
- 不推算步行時間、不提供逐步步行導航、不以座標直線距離補造缺失距離。
- 不查詢或偽造後續乘車段即時 ETA。
- 不改變通知欄監控、收藏、分享或下車提醒。
- 不引入 Compose、Navigation Component 或新的架構框架。

## Decisions

### 1. 使用獨立 `RouteDetailActivity` 與單一 RecyclerView

新增 `RouteDetailActivity`，由 `MainActivity` 與 `SearchFragment` 的卡片點擊入口共同啟動。固定 App Bar 提供返回；摘要、未來可選地圖、步行段、乘車段、途經站及終點全部轉為同一 RecyclerView 的扁平 UI item，避免 Bottom Sheet 拖動與內容捲動競爭，也避免長站點列表形成巢狀捲動。

啟動參數以可在 process recreation 後重建的 primitive／Bundle 資料傳遞，至少包含卡片摘要、P2P 詳情查詢與首程 ETA 查詢所需欄位；不把 Activity 依賴於來源頁的記憶體物件。來源頁仍保留既有結果清單，因此系統返回後自然恢復原排序與捲動位置。

替代方案：

- 繼續擴展 Bottom Sheet：可少改入口，但長路線、未來地圖及大字體會持續受 Bottom Sheet 高度與巢狀手勢限制，已否決。
- 使用 Fragment destination：可統一單 Activity 導航，但專案目前未採用 Navigation Component；為單一頁引入新導航框架超出範圍，已否決。

### 2. 以領域模型保存步行分段、換乘類型與預計時間

擴充 `RouteDetail`／`RouteDetailLeg`，以結構化欄位保存：

- 起點、換乘、終點 `RouteDetailWalkingSegment`，距離為 nullable；
- `WALK_TO_TRANSFER_STOP`／`SAME_STOP_TRANSFER` 等換乘類型；
- 每段預計上車／下車時間、票價；
- 全程預計起點／到達時間；
- 卡片距離與詳情完整距離是否完整可判定。

總途經站數由 `legs.sumOf { viaStops.size }` 計算，不重複上下車及換乘端點。詳情完整步行距離只在所有已識別為必要的步行段均有距離時合計；若分段不完整，摘要回退至卡片距離並保留「非完整合計」狀態，UI 不把回退值描述成已知完整總量。

替代方案：

- 繼續增加多個裸 `Int?` 欄位：初期較快，但無法穩定表達分段種類、缺失與同站換乘，已否決。
- 以站點座標計算距離：只得到直線距離，會誤導為步行路徑，已否決。

### 3. 集中解析 `getp2pstopinroute.php` 可重現資料

`CitybusRouteDetailParser` 保持唯一 HTML 假設邊界，在現有站點解析之外解析可見步行列、換乘標記與 Citybus 內嵌 `showtimetable1(...)` 方案資料。解析結果必須按 `rawInfo` 的 leg 順序及 route variant 對齊；無法可靠對齊的分段欄位保持空值，不以其他路線或語言重試。

請求 URL、`info`／`ginfo`／`lid`／`l` 參數及「不顯式加入瀏覽器 header／Cookie」契約保持不變。現有 N118 與 N8P → N969 fixture 擴充斷言，另新增同站換乘及缺失距離／時間樣本；live 驗證保留可重現請求與業務簽名。

替代方案：

- Activity 直接掃描 HTML：破壞分層與可測試性，已否決。
- 以 DATA.GOV.HK route-stop 重建 P2P 詳情：可能與 P2P route variant／站序不一致，沿用既有規格禁止此兜底。

### 4. 卡片距離與詳情距離使用不同但明確的語義

路線卡片仍使用 `BusRouteOption.walkingDistanceMeters`，維持目前 `ppsearch_p3.php` 值、顯示及排序。詳情頁在解析完成前顯示卡片摘要；解析完成後，若全部必要步行段距離可用，摘要改為完整合計。例如 N8P → N969 卡片維持 378 米，詳情顯示 403 米。若完整合計不可判定，詳情摘要保留卡片值但以結構化狀態避免誤稱完整總量。

替代方案：

- 在列表階段為每張卡片預取詳情並回填總距離：會放大請求量、改變排序及造成列表跳動，已否決。
- 詳情永遠顯示卡片距離：會與可見分段相加結果矛盾，已否決。

### 5. 使用扁平時間線與按段穩定配色

RecyclerView formatter 把領域資料映射為扁平 item：摘要、步行、乘車起點／卡片、途經站控制、可選途經站、下車、換乘及終點。巴士段使用按 leg index 循環的淺／深色模式調色板；相鄰段必須不同。路線牌、粗實線及未來地圖折線共用該段顏色，但 UI 同時顯示路線號、方向、節點形狀及文字，避免只靠色覺。

步行及步行換乘使用中性細虛線和已確認的四塊實心步行矢量；同站換乘使用文字節點且不顯示距離。紅色僅供錯誤／停運等危險語義。乘車卡片只承載路線、方向、即時 ETA、票價及站數；途經站控制與展開後的小圓點／站名位於卡片外的主時間線。

替代方案：

- 所有乘車段使用同一品牌色：語義單純，但使用者已選擇分段配色，已否決。
- 全時間線使用中性色、只著色路線牌：視覺克制，但多段換乘掃讀較弱，已否決。

### 6. 途經站預設折疊並以列表狀態原位展開

每段的展開狀態由頁面 UI state 以 leg index／stable leg key 保存。控制行整體至少 48dp 可點擊，使用 18dp 矢量 Chevron；展開時旋轉 180°，不以 `⌄`／`⌃` 字符拼接。展開／收起只更新該段對應的扁平 item，其他段狀態及目前視口保持不變。畫面旋轉時保存展開狀態，離開詳情後不持久化。

替代方案：

- 預設顯示前兩站：增加每段高度且資訊重複，已否決。
- 預設全部展開：長路線會把換乘與終點推得過遠，已否決。

### 7. Citybus 預計時刻與首程即時 ETA 分開呈現

Citybus 方案時間一律格式化為中性 `預計 HH:mm`，可出現在起點、每段上下車及最終到達節點。首程 ETA 使用既有 `WaitTimeState` 及 `CitybusFirstLegEtaService` 語義，顯示品牌色 `即時 · 還有 N 分鐘`；有效空結果顯示暫無車輛，技術故障顯示候車暫不可用。

啟動參數攜帶來源卡片最新 ETA snapshot，讓頁面立即展示；若有完整 `FirstLegEtaQuery`，Activity 在背景以生命週期受控工作刷新一次。完成回調必須比對 request generation 與 `LanguageSnapshot`；頁面銷毀、語言改變或重試產生新 generation 後忽略舊結果。後續乘車段只展示 Citybus 預計上車時間。

替代方案：

- 把預計時刻也標成即時／到站：會誤導資料精度，已否決。
- 為每個換乘段立即查 ETA：到達換乘站的時間尚未發生且缺少可靠連續旅程語義，超出本次範圍。

### 8. 載入、部分成功、失敗與快取保持可恢復

Activity 先以啟動參數顯示摘要，再非同步讀取 1 天詳情快取或發起請求。成功時以完整 UI state 取代載入 item；部分欄位缺失時保留可解析站點並隱藏缺失值。請求／解析失敗時保留摘要、App Bar 與返回，正文顯示三語錯誤及重試。重試建立新 generation，失敗結果不進快取。

`RouteDetailCache` 仍以完整 `rawInfo + lang` 隔離，但 cache entry 擴充保存步行分段、預計時間與票價；首程即時 ETA 不寫入 1 天詳情快取。

### 9. 矢量圖示、三語與無障礙

卡片耗時使用已確認的細線鬧鐘 VectorDrawable；卡片與詳情步行使用依參考輪廓重建的四條填充 path VectorDrawable，不打包低解像度來源圖。圖示使用語意 tint，文字資源同步提供香港繁體、獨立簡體與英文。

TalkBack 以合併語意讀出耗時、步行距離、預計／即時來源、途經站數及展開狀態；裝飾線和圓點不重複朗讀。版面允許長站名換行、RecyclerView 捲動及 360dp／font scale 2.0；不以縮字容納翻譯。

## Risks / Trade-offs

- [Citybus HTML 或 `showtimetable1(...)` 欄位順序改變] → 將解析集中於 parser、保存原始 fixture 與可重現 live 樣本，對每個可選欄位獨立降級，站點主結構仍可展示。
- [卡片 378 米與詳情 403 米看似不一致] → 卡片保持上游摘要；詳情只在完整分段可判定時顯示完整合計，並在文案／語意上區分來源，不回填或重排列表。
- [每段顏色被誤認為官方路線色] → 顏色按目前方案 leg index 分配，只用於分段；始終同時顯示路線號、方向與節點形狀。
- [多段路線超過調色板長度] → 使用可重複的模式感知調色板並保證相鄰段不同；顏色循環不作跨方案身份識別。
- [全屏頁啟動參數過大] → 只傳 primitive 查詢與摘要，不傳整份站點詳情或 HTML；詳情從快取／網路重建。
- [Activity 同時刷新詳情與 ETA 造成過期回調] → 兩條工作各自使用 generation 及生命週期取消／忽略，UI state 合併不得以舊資料覆蓋新語言或新重試。
- [展開大量站點造成列表跳動] → 使用 stable item identity 與局部 diff，控制行保留在原位，避免重建整頁或移動其他段狀態。
- [大字體令摘要指標擠壓] → 指標允許換行／流式排列，核心文字不縮小；以三語、360dp 及 font scale 2.0 驗證。

## Migration Plan

1. 先擴充領域模型、parser、formatter、cache entry 與 fixture 測試，保持既有 Bottom Sheet 仍可編譯。
2. 新增卡片 VectorDrawable 與三語／深淺色資源，替換卡片輔助指標但保持數值與排序測試。
3. 新增 `RouteDetailActivity`、啟動參數、RecyclerView item 與載入／錯誤／重試狀態。
4. 將 `MainActivity` 與 `SearchFragment` 的詳情入口切換至 Activity，確認返回恢復來源頁上下文。
5. 移除不再使用的 `RouteDetailBottomSheet` 及相關 Bottom Sheet／巢狀捲動資源與測試。
6. 執行 parser／formatter／UI 測試、`./gradlew build` 及三語×明暗×大字體人工驗收。

若新 Activity 在切換入口後出現阻斷性問題，可在同一變更尚未發布前恢復原入口及 Bottom Sheet；模型與 parser 擴充保持向後相容，回退不需要資料庫遷移。

## Open Questions

無。頁面容器、摘要欄位、卡片與詳情距離語義、分段配色、步行虛線、途經站展開、Chevron、預計／即時時間及 Google 地圖範圍均已由使用者確認。
