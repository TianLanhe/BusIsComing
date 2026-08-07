## Context

路線詳情目前由 `RouteDetailActivity` 協調 `MapView`、三檔 `BottomSheetBehavior`、並發資料域及列表互動；`RouteMapPresentationBuilder` 生成純 Kotlin marker／line model，`GoogleRouteMapRenderer` 以 marker、帶描邊巴士 polyline 及灰色虛線步行 polyline 差量繪製；`RouteDetailUiFormatter` 和 `RouteDetailAdapter` 產生摘要與時間線。現有 `fix-route-detail-progressive-loading` change 已把詳情載入改為 generation-aware 的資料域 reducer、可靠結構門禁、single-flight 及可分域快取，本 change 必須建立在該行為上，不得重做或倒退。

目前 UI 的主要限制是：marker 仍由 Canvas 基本幾何形狀產生；普通站名依賴 Google marker info window 而沒有碰撞治理；巴士線沒有前進方向，步行仍是灰色虛線；摘要沒有完整可點擊行動鏈；乘車段仍顯示邊框、首程 ETA 及單段站數；全屏仍顯示 Toolbar。

專案固定使用 XML、AppCompat、Material Components、RecyclerView 及 Google Maps SDK for Android。版本目錄目前鎖定 `play-services-maps 20.0.0`。本地 AAR 雖提供 `StrokeStyle`、`StampStyle`、`SpriteStyle` 及 `StyleSpan`，但固定直線、急彎、S 彎及反向 geometry 的裝置 spike 證明 SDK 會重採樣 stamp，使折角變成密集細小鋸齒，不能保持已確認的視覺尺寸與間距；因此正式 renderer 不使用該樣式。

本 change 不增加資料來源。站名、站序、巴士計劃時間、票價與巴士幾何仍由 Citybus 詳情及既有 geometry repository 提供，首程 ETA 仍走既有流程；步行距離、約略時間與步行 path 則消費 `integrate-landsd-pedestrian-routing` 已接受進 walking domain 的 CSDI 狀態及 Citybus fallback。任何 UI 資料只能在目前 `LanguageSnapshot` 與對應 page／structure／dynamic／ETA／walking generation 內消費。

## Goals / Non-Goals

**Goals:**

- 讓地圖起點、終點、上車、下車、同站換乘及普通站在不讀文字時仍可辨識。
- 以不漂移的開放折角嚴格沿有序 geometry 局部切線展示巴士與步行方向。
- 在三語、窄屏、長英文及不同縮放下提供足夠站名而不形成文字牆。
- 把摘要改為無邊框三層資訊，加入緊湊、可點擊、可無障礙操作的完整行動鏈。
- 簡化半屏／全屏時間線，集中首程 ETA 與站數，保留多段方案必要單段票價。
- 全屏移除標題與屏內返回，保留安全區、拖動把手、系統返回及現有資料可靠性。

**Non-Goals:**

- 不修改 Citybus、DATA.GOV.HK、Google Maps 請求、parser、route variant 或 stop id 對齊。
- 不接入 Google Routes API，也不把端點直線描述成真實沿街步行導航。
- 不新增導航操作、即時車輛位置、乘車進度、下車提醒、收藏、分享或監控入口。
- 不重寫 progressive reducer、CSDI runtime、single-flight、domain cache、相機所有權或自動刷新排程。
- 不引入 Advanced Marker、Map ID、遠端圖標、Emoji 或字體 glyph。
- 不為缺失 geometry、計劃時間、票價、距離或顏色製造估算值。

## Decisions

### 1. 擴展純展示模型，不把 SDK 類型帶入資料層

`RouteMapPresentationBuilder` 繼續只接受已驗證 domain detail、查詢端點、有序 geometry、選中狀態與 route plan，輸出：

- stable marker id、角色、坐標、目前語言站名、前後乘車段色 slot、標籤優先級及時間線 target；
- stable path id、有序點列、巴士／步行種類、stroke 語義及 stamp 語義；
- 供相機全覽使用的可靠 bounds 點。

model 不包含 `Marker`、`Polyline`、`BitmapDescriptor`、`Projection`、View 或像素矩形。SDK 物件、像素碰撞及 bitmap cache 只存在於 `GoogleRouteMapRenderer`。這保持 formatter／presentation builder 可做 JVM 測試，也避免 MapView 生命週期滲入 repository。

否決直接在 Activity 組 marker／polyline：它會把資料 identity、render diff、camera callback 及 UI 生命週期重新耦合，並增加過期 callback 覆蓋目前畫面的風險。

### 2. marker 使用本地固定比例 vector，角色語法固定

renderer 依角色及模式把 VectorDrawable 等比 rasterize，並以「資源／角色、色 slot、模式、選中狀態、density bucket」作 cache key：

- 起點：綠色地圖針，中心白孔；
- 終點：珊瑚紅地圖針，中心白孔；
- 上車：目前乘車段色實心圓，內含白色巴士正面；
- 下車：目前乘車段色空心圓環，內含同色 Lucide `log-out`；
- 同站換乘：前後段色各佔半環，內含中性環形換向箭頭；
- 途經站：低強度中性小圓點及白色隔離邊緣。

Lucide SVG 本地轉為 VectorDrawable，保持原 viewBox 比例，不作非等比縮放；第三方告知加入 Lucide 版權及 ISC 全文。步行摘要只復用現有 `ic_walking_person`，不得以預覽占位或手工拉伸替換。

否決純 Canvas 任意幾何與 Emoji：前者無法穩定表達已確認的 bus／log-out／雙色環細節，後者跨字體與平台不一致且無法受控等比。

### 3. 方向折角按屏幕投影與局部切線重排

巴士段仍由白色 outline polyline 加分段色 core polyline 組成。方向層由 renderer 在 camera idle 或可見 padding 更新後取得 Google Maps `Projection`，把同一有序 geometry 投影成屏幕折線，按固定屏幕間距插值位置，並由相鄰屏幕點計算每個位置的局部切線；再以 `flat(true)` 的白色開放折角 marker 放回對應地理坐標。步行段不繪製任何灰色承載線，只以較大、較粗的灰色開放折角 marker 使用同一算法。折角 marker 不可點擊，且不參與資料層 identity、相機 bounds 或站名語義。

固定直線、急彎、S 彎及反向點序的最小裝置實驗先驗證 SDK stamp/style；該路徑因重採樣形成密集鋸齒而失敗。修訂後的屏幕投影方案須繼續通過：

1. 步行只留下粗灰折角而無底線；
2. 折角在直線及拐角保持固定視覺尺寸與稀疏間距；
3. 每個折角嚴格貼合所在屏幕線段的局部切線；
4. 反向 geometry 使全部折角反轉；
5. 縮放及 padding 改變後在 camera idle 重排，不沿用舊角度或舊位置。

不得使用整段起終點 bearing、固定角度 bitmap、逐幀 camera move 重排或脫離 geometry 的手工位置。若 projection 暫不可用或某段不能產生可靠局部切線，巴士保留可靠實線、步行省略該段折角；每次重排設置折角數量上限，避免異常長 geometry 造成無界 marker 數量。

### 4. 站名在 camera idle 後以投影碰撞模型放置

renderer 維護獨立、不可點擊的 label overlay／等效標籤物件；每次 camera idle 或地圖可見 padding 改變後取得 projection，把候選位置轉成屏幕矩形。每個標籤依右、左、上、下順序生成候選，按下列成本評分：

- 超出地圖可見矩形或被 bottom sheet／system inset／目前可見 CSDI 署名遮擋；
- 與 marker、路徑、已接受關鍵標籤重疊；
- 與普通標籤重疊；
- 位於屏幕邊緣外側。

優先級固定為起終點 > 上下車／換乘 > 選中途經站 > 其他途經站。關鍵標籤沒有零碰撞候選時選最低成本並配可讀 halo；普通標籤可被隱藏。舊側仍有效時保留，避免相機每次 idle 都跳邊。camera move frame 不做文字量測。

地圖只顯示目前 `LanguageSnapshot` 的 Citybus 原文，單行限寬並省略；完整名稱仍進入 marker 互動、時間線及 content description。否決固定放右側：窄屏、地圖邊緣及長英文會直接遮擋或越界。

### 5. 摘要由 formatter 生成三層展示資料

`RouteDetailUiFormatter` 增加不含 View 的摘要 segment model，包括 stable `detailTargetId`、種類、路線號／walking icon token、色 slot及可空耗時。摘要固定為：

1. 總耗時，後接可用 Citybus 預計到達；
2. 起點步行、各乘車段、每次換乘及終點步行的完整行動鏈；
3. `RideStopCountState`、總步行距離、總票價及首程 `WaitTimeState`。

分段耗時依種類保持資料權威：巴士段只能由本次新鮮 dynamic detail 的計劃時間邊界計算，純函數把 `HH:mm` 邊界轉為分鐘並在終點早於起點時跨午夜一次；成功步行段只顯示目前 walking domain 由 CSDI 原始 `Total_Time` 向上取整且至少 1 分鐘的約略時間；Citybus fallback、Loading、SameStop 或任一不可靠值輸出 `null`。`RouteStructureCache` 不新增計劃時間、CSDI 時間或票價欄位，Citybus 總耗時與預計到達亦不由摘要分段重算。

可見 segment 使用 content-wrap、約 2dp gap、約 22dp 底色高度及共同底部 baseline；水平容器單行捲動。每個可見塊保留獨立語義節點，TouchDelegate 只在不增加可見寬度的前提下向上下擴張至至少 48dp 有效操作高度；水平方向以可見塊邊界或相鄰中點裁切，不能互相覆蓋或增加大段空白。

否決把各段設為等寬：路線號與步行內容長度差異大，會重現使用者已指出的大塊空白。也否決從距離另行估時；步行只接受 CSDI 明確返回的固定步速 `Total_Time` 並標示約略，巴士仍只接受 Citybus 計劃時間。

### 6. 摘要跳轉使用 stable id 與 generation-aware pending target

每個摘要 segment 對應現有或新增時間線 stable id：起終點步行、乘車段、transfer 與末段步行均有唯一 target。Activity 收到點擊後進入 FULL，查找 adapter current list：

- 已存在：`scrollToPositionWithOffset`，短暫低強度高亮，把焦點移至標題並觸發 accessibility announcement；
- 未存在且資料仍載入：保存 `(pageGeneration, structureIdentity, detailTargetId)`；
- 同一 page 與 structure identity 的列表提交完成且目標出現：執行一次並清除；
- 頁面或結構 identity 改變、頁面離開或目標資料域最終失敗：清除並在適用時朗讀本地化不可用狀態；單純 dynamic detail、ETA 或 walking generation 更新不得清除仍有效目標。

pending target 不保存到進程外，不跨一次詳情開啟；configuration change 若保留同一 page 與 structure identity，可與其他 interaction state 一起恢復。這延續現有 reducer 的過期 callback 邊界，同時避免每分鐘動態刷新誤取消使用者剛觸發的跳轉。

### 7. 時間線 rail 與內容分離，移除乘車卡片語法

`RouteTimelineRailView` 只繪製：整體起終點白環彩色圓心、步行灰色輕量點線、巴士分段色連續實線及普通途經小點。上車與下車行不再繪製大節點或額外空心圓。`RouteDetailAdapter` 以留白、字級及 rail 分組，不使用乘車段外框或卡片底色。

BusLeg item 移除 `stopCount` 與 `liveEta` 展示責任。多段方案才在路線／方向同一行末端顯示可用 `fareHkd`；單段方案依摘要總票價去重；缺失值整體隱藏。途經站展開控制仍保留 `viaStops.size`，但只表示可展開項目數，不作摘要或乘車段站數。

第一段 ETA 只由摘要第三層讀取 `WaitTimeState`。這避免計劃時間與即時 ETA 在同一乘車段混讀，也不改變 ETA resolver 或刷新策略。

### 8. 全屏只改 chrome，不改三檔狀態機與返回語義

`activity_route_detail.xml` 移除或永久隱藏 sheet Toolbar 占位；FULL 時地圖及 floating back 隱藏，sheet 在 status bar safe inset 後以 drag handle + RecyclerView 鋪滿。SUMMARY／HALF 保留地圖左上 floating back。FULL 向下仍可到 HALF；handle 點擊沿用現有檔位策略。

`OnBackPressedDispatcher` 在三個檔位都直接 finish，不先收合。失敗頁也不恢復 App Bar 或屏內返回；系統返回與 retry 仍可操作。

### 9. 漸進載入、快取與失敗保持現有權威邊界

本 change 不增加 network request。presentation／formatter 只消費 reducer 已接受的內容；map、bus geometry、dynamic detail、ETA、walking 繼續各自獨立降級。24 小時 cache 僅保留已驗證結構與 CSDI 原始成功結果，不保存 Citybus 動態計劃時間、分段票價或格式化摘要分段耗時。

方向 projection／局部切線失敗只影響對應折角，巴士可靠實線仍保留；label collision 失敗回退為關鍵 marker 可操作及完整無障礙標題；某分段時長或票價缺失只隱藏該值；詳情主結構失敗繼續保留啟動摘要和局部重試。任何過期 generation 都不能完成 pending scroll、覆寫 label 或更新地圖。

### 10. 本地化、無障礙與驗證分層

所有 App 自有文案、content description、不可用朗讀及摘要格式同時提供香港繁體、獨立簡體與自然英文。路線號、Citybus 站名及方向保持第三方原文。顏色之外同時有形狀、圖標、位置及文字語義。

驗證分層：

- JVM：展示模型、角色色、同站／異站換乘、跨午夜耗時、缺失值、站數狀態、stable target、碰撞評分與舊側穩定規則；
- instrumentation／view：segment 約 22dp 可見高度、至少 48dp 有效操作高度且水平方向不重疊、水平捲動、baseline、adapter 無邊框、fare／ETA 去重、pending target、focus／announcement 及 full-screen chrome；
- 地圖裝置：固定直線、急彎、S 彎及反向 geometry 的屏幕投影折角、無底線步行、縮放／padding／漸進更新、marker 比例與站名避讓；
- 視覺／無障礙矩陣：繁體、簡體、英文，淺／深色，360dp、窄屏，font scale 1.0／1.3／2.0，TalkBack，以及單段、多段、同站／異站換乘、缺失時間／票價／geometry。

Android 裝置驗證只能使用本任務啟動且符合畫像的 AVD；任務結束後關閉。完整構建及定向測試通過後再進行裝置矩陣。

## Risks / Trade-offs

- [SDK stamp API 存在但視覺行為不滿足尺寸與間距要求] → 保留失敗 spike 證據，改用 camera-idle 屏幕投影、固定屏幕間距及逐位置局部切線；固定 geometry 與反向點序裝置測試是正式方案的門檻。
- [地圖 label overlay 與 Google projection 在快速手勢期間不同步] → 只在 camera idle 及穩定 padding 後重算，camera move 時保留或暫時隱藏舊標籤，不逐幀佈局。
- [關鍵站密集時仍無法完全無碰撞] → 固定優先級、最低衝突候選及 halo；普通站名先隱藏，完整名稱仍可通過選擇及時間線取得。
- [22dp 可見塊與 48dp 觸控高度產生重疊] → 視覺層與語義觸控層分離，只向上下擴張 TouchDelegate，水平方向按可見塊邊界或相鄰中點裁切，並加入坐標級 instrumentation 測試。
- [多段摘要單行過長] → 內容寬度不壓縮，使用顯式可水平捲動容器；TalkBack 順序按資料列表而非目前可見區。
- [前置 change 保持 active 令主 spec 尚未包含最新基線] → apply 直接以目前已實作代碼、active delta 與測試共同核對；本 change 的重疊 requirement 保留 progressive 可靠站數及 LandSD 步行狀態完整場景，不要求本次迭代先歸檔前置 change。
- [Lucide 許可遺漏] → 把第三方告知與資源來源核對列為獨立任務及發佈檢查項。

## Migration Plan

1. 先確認 `fix-route-detail-progressive-loading` 與 `integrate-landsd-pedestrian-routing` 的目前實現、測試及 active delta 仍是本 change 基線；兩者無需為本次 apply 預先歸檔，若期間主 spec 改變則重新校驗重疊 requirement。
2. 完成 Maps SDK stamp 最小裝置 spike；若其視覺門檻失敗，先修訂 design/spec，再以同一固定 geometry 驗證屏幕投影與局部切線方案。
3. 先擴展純展示模型與 JVM 測試，再增加 vector 資源、renderer 方向 marker、角色 marker cache 及 label collision。
4. 擴展摘要／時間線 presentation，完成 adapter、觸控、pending target、無障礙與三語資源。
5. 移除 Toolbar／卡片邊框等舊 chrome，保留系統返回及三檔狀態機。
6. 運行定向測試、`./gradlew build` 與任務自有 AVD 視覺／TalkBack 矩陣；未通過方向貼合即不視為完成。

回滾以 UI 層提交為邊界：presentation model、renderer、摘要／adapter 及 layout 可整體回退至現有 marker／虛線／卡片 UI，不遷移本地資料庫，也不改變服務端或長期 cache 格式。若只發生個別 runtime projection／局部切線失敗，則使用設計內的安全省略路徑而不是回滾資料層。

## Open Questions

無未確認產品決策。Maps SDK stamp 的裝置實驗已失敗；屏幕投影與逐位置局部切線方案已按同一固定 geometry、反向點序及縮放場景通過裝置驗證。
