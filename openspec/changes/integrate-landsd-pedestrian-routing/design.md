## Context

路線查詢目前先返回包含 Citybus 總步行距離的 `BusRouteOption`，再透過 progressive callback 補充站點預覽與 ETA。`walkingDistanceMeters` 同時參與既有結果身份、去重、排序及監控回退，若直接以新來源覆寫，容易改變 `resultId` 或丟失可靠兜底。

路線詳情已具備可靠站序門禁、進程級結構／Citybus 步行快取、詳情 single-flight、不可變 `RouteDetailPageState`、主線程 reducer、分段巴士幾何、Google Map 增量 renderer、香港首幀及 PAGE／USER 相機所有權。現有步行段只解析 Citybus 分段米數，地圖以查詢端點與站點之間的直線虛線作示意；`fix-route-detail-progressive-loading` 已要求不同資料域真並發、generation 驗證、成功內容單調增加及局部失敗不清空其他內容，本 change 必須沿用而非另建互相競爭的頁面狀態。

新來源為地政總署 3D Pedestrian Route Search API：

```text
GET https://mapapi.hkmapservice.gov.hk/PedRoute/NAServer/route/solve
```

官方接入指引為 `https://portal.csdi.gov.hk/csdi-webpage/apidoc/3d-pedestrian-route-search`，使用條款為 `https://portal.csdi.gov.hk/csdi-webpage/doc/TNC`。受控真實請求確認 `travelMode=3` 可返回 `routes.features[0].attributes.Total_Length`、`Total_Time` 及 `geometry.paths`；樣本的 `Total_Time` 等於 `Total_Length / 60`，表示第一版時間是地政總署固定 1 m/s 步速估算，而非使用者個人步速或即時設施狀態。

## Goals / Non-Goals

**Goals:**

- 用地政總署行人網絡改善路線卡片總步行距離、詳情分段步行距離／約略時間及地圖步行軌跡。
- 讓同一有向端點的 CSDI 請求在卡片、詳情及多條候選路線間共用，並以全局 5 個並發、成功快取、優先級及訂閱取消控制外部負載。
- 延續現有真並發與內容單調模型，讓任何分段成功即可在詳情加入，且過期、重複或亂序 callback 不會重複累加、覆蓋新值或清空其他成功內容。
- 對端點身份、兩個 Citybus 坐標來源及 CSDI 軌跡端點設置可測試門禁；不以直線或猜測數字掩蓋失敗。
- 在 Google 底圖上以法律署名、三語文字、TalkBack 及安全區正確展示地政總署資料。

**Non-Goals:**

- 不重算 Citybus 總耗時、預計到達、各巴士段時間、首程 ETA 或轉乘可達性。
- 不把地政總署固定步速套入通知欄監控的個人步速模型；本次只記錄後續統一時間模型的技術債。
- 不提供 travel mode 選擇、無障礙模式自動切換、轉向文字、開始導航、即時跟隨、偏航重算或 Google Routes。
- 不新增磁碟／跨進程快取、SQLite schema、背景服務、權限或第三方 SDK。
- 不以 CSDI、Citybus 或 Google 任一外部服務的成功可用性作一般自動測試前提。

## Decisions

### 1. 請求契約固定且與 App 語言無關

每個必要步行段只提交兩個 WGS84 端點，`stops` 使用 JSON feature collection；`x` 為 longitude、`y` 為 latitude，端點 `Name` 固定為 `Start`、`End`，不傳 Citybus 不具備的 `z`。固定參數為：

```text
travelMode=3
directionsLengthUnits=esriNAUMeters
directionsLanguage=en
outSR=4326
f=json
returnZ=true
directionStyleName=NA Campus
```

第一版不解析 directions，因此固定英文可讓三種 App 語言共用同一 key、flight 與 cache。回應只接受一個具有有限正數 `Total_Length`、有限正數 `Total_Time` 及非空 `geometry.paths` 的路線；每個 path 至少兩個有效 WGS84 點，坐標陣列只讀前兩項並忽略 z／m。第一個 path 起點及最後一個 path 終點須分別在請求端點 30 米內。任一必要欄位、坐標或端點門禁失敗，整段視為不可用，不發布部分軌跡；有效路線附帶的一般 warning／message 可記診斷但不單獨判失敗。

**否決方案：**按 App 語言傳 `directionsLanguage` 會為未展示的 directions 製造三份等價請求；接受只有距離而沒有可靠 paths 會令文字和地圖來源不一致；把多個 paths 接成一條線會穿越真實網絡缺口。

### 2. 先以 Citybus 語義規劃分段，再並發提交可用端點

每條候選路線需要三類穩定 segment id：`origin`、`transfer:<index>`、`destination`。查詢起終點取自產生該結果的 `P2pRouteRecoveryContext`／啟動快照；巴士站坐標以 `showstops2.php` 為主來源。Citybus 詳情和 `showstops2.php` 在卡片結果返回後並發取得，且共用現有 24 小時結構快取與詳情 single-flight：

- 起點至首段上車站、末段下車站至終點在主端點可用後即可提交，不等待其餘分段。
- 轉乘必須等待 Citybus 詳情判定 `SAME_STOP` 或 `WALK_TO_TRANSFER_STOP`；不得以坐標接近或 stop id 相同自行改寫語義。
- `SAME_STOP` 形成明確跳過狀態，不請求 CSDI、不展示 0 米／0 分鐘，也不生成 path。
- Citybus 明確為步行轉乘時，即使坐標非常接近仍提交 CSDI。

Citybus 詳情坐標只可在 `routeVariant + sequence + stopId` 全部匹配同一端點時後備，不允許跨路線 variant、只按名稱或只按 sequence 對齊。兩來源均可用時不平均；直線差不超過 30 米採用 `showstops2.php`，超過 30 米視為來源衝突並令該分段回退。有效分段組合以查詢端點 context、plan fingerprint 及有序 segment key 保存，讓重入可直接組合分段 cache。

**否決方案：**只查 `showstops2.php` 無法可靠識別 Citybus 同站語義或取得分段回退距離；只用詳情坐標會放棄 P2P stop map 的主身份來源；以名稱或近距離合併可能把相鄰月台、道路兩側或不同入口誤當同一站。

### 3. Citybus 原值與 CSDI 顯示狀態分離

`BusRouteOption.walkingDistanceMeters` 保持為不可變 Citybus 原始總距離，繼續服務 result identity、去重、監控與最終兜底；新增獨立展示狀態，而不是覆寫此欄位。分段狀態為：

```text
Loading
CSDISuccess(rawDistanceMeters, rawTimeMinutes, paths)
CitybusFallback(distanceMeters?, reason)
SameStop
```

cache 保存 CSDI 原始 Double 距離／分鐘與全部子路徑，不保存已格式化文字或取整值。展示規則為：

- 路線總距離先累加所有必要 CSDI 原始距離，再對總和 `ceil` 至整數米。
- 詳情各段距離分別 `ceil` 至整數米。
- 詳情各段正數時間分別 `ceil` 至整數分鐘且至少 1 分鐘，並標示為約略時間。
- 同站不參與必要分段數及合計。

詳情 `RouteDetailPageState` 增加按 stable segment id 保存的不可變狀態與 domain generation；每個 event 只替換一個 key，摘要每次由整張狀態表重新派生，禁止在 callback 內用 `+=` 累計。卡片只接收完整 `WalkingDistanceDisplayState` 快照。

**否決方案：**逐段先取整再相加會令總距離隨分段數系統性偏大；直接改寫 Citybus 欄位會失去兜底並可能改變 identity；並發 callback 增量累加無法抵抗重複、重試及亂序事件。

### 4. 進程級協調器擁有 cache、single-flight、隊列及重試

新增可注入的進程級 pedestrian runtime，供常用／搜尋卡片與 `RouteDetailActivity` 共用。分段 request key 為：

```text
round6(startLat,startLon) -> round6(endLat,endLon) + travelMode=3
```

坐標保留 6 位小數（約 0.1 米），方向不可交換，language 不進 key。協調器依序：同步查成功 cache、加入或建立 single-flight、排入有界優先隊列、取得全 App 共用的 5 個 permit、執行可取消 HTTP、原子寫 cache，再向仍有效的訂閱者派送同一結果。

卡片使用普通優先級；詳情訂閱可提升尚未執行的同一 flight 或新 flight，但不搶占已執行請求。單次嘗試總時限 8 秒；只對連線／網絡異常、timeout 及 HTTP 5xx 在約 300 ms 後自動重試一次。HTTP 4xx、無路線、必需欄位缺失或回應無效不重試；重試仍屬同一 flight 且受 5 個 permit 約束。

每個 consumer 取得獨立 handle。離開只移除自己；仍有 consumer 時 flight 繼續。最後 consumer 離開時，排隊工作移除，在途連線斷開並中斷；完成後 callback 不再派送給已取消 consumer。卡片一旦任一必要段最終失敗，立即發布完整 Citybus 總距離並解除其他只為該卡存在的訂閱；仍被詳情或其他卡片共用的工作繼續。

**否決方案：**每張卡各建 thread pool 不能形成真正全局上限；以無界 executor 搭 semaphore 難以移除排隊任務或提升詳情優先級；反向共用 key 會忽略扶手電梯、閘口及單向行人網絡。

### 5. 成功分段 cache 與路線組合 cache 分層

兩層 cache 都只存在 App 進程記憶體並以 24 小時 TTL 原子過期：

- 分段 cache：request key → 原始 CSDI 成功結果。
- 組合 cache：查詢起終點 context + plan fingerprint → 有序 segment key、角色及 SameStop 標記。

組合 cache 不複製軌跡結果。重入時若組合與全部必要分段均命中，卡片首幀直接顯示 CSDI 合計，詳情可立即重建分段文字及 paths，不製造假 Loading；若只缺部分分段，既有成功立即可用並只提交缺失 key。失敗、來源衝突、無效 response 及格式化 UI 狀態均不進入成功 cache。為避免預設一分鐘自動刷新把相同永久或暫時失敗放大成請求風暴，runtime 另保存不含結果內容的進程內失敗資格：`AUTOMATIC` trigger 對同一有向 request key 採 5 分鐘起始、最長 30 分鐘的指數退避；退避只決定是否可建立新 flight，不向 consumer 返回快取失敗。明確 `MANUAL` pull refresh 或使用者重新進入可各繞過一次目前退避，成功後立即清除該 key 的失敗資格；語言切換不繞過也不重請語言無關結果。

**否決方案：**只保存整條路線總數會失去跨不同候選路線的端點共享及詳情 paths；磁碟 cache 會引入 schema、隱私及清理成本；快取失敗會令一次暫時故障在 24 小時內持續回退。

### 6. 邏輯查詢會話跨 configuration change 保持訂閱

搜索與詳情使用可跨 configuration change 的邏輯會話／ViewModel 持有 pedestrian subscription 和原始狀態；View、Activity 或 Fragment 只替換 observer。搜尋會話在新查詢、清空結果或真正離開流程時結束；詳情會話在返回關閉頁面時結束。自動刷新建立新基礎查詢 generation 時，舊結果專屬訂閱立即失效，成功 cache 仍可由新結果重用；基礎路線回應可結束自動刷新 cycle，而後續 CSDI callback 不延長該 cycle，下一個新查詢則取消不再匹配的舊 consumer。這避免旋轉、主題或語言切換時短暫零訂閱令在途工作被取消後重請，也避免舊查詢更新新列表。

卡片事件仍經 `RouteQueryCoordinator` 驗證目前 query generation、`resultId` 與 segment id；UI 派送另外驗證 language version。詳情事件進入既有主線程 reducer，驗證 page generation、walking domain generation 與 segment id。CSDI 原始結果與語言無關，舊 UI observer 被拒絕後，新 observer 從會話目前 snapshot 以新語言重新格式化，不重請網絡。ETA、站點預覽、CSDI 與自動刷新基礎結果共用一個結果狀態／projection 入口，按目前排序及置頂規則只投影一次。

**否決方案：**讓 coordinator 直接修改 adapter／Map 會跨越 data 與 UI 邊界；把訂閱綁定 View 生命週期會與「最後訂閱取消」共同造成 configuration change 重請；為避免重請而永不取消則會浪費網絡並可能持有頁面。

### 7. 卡片與詳情採用不同聚合回退邊界

卡片只顯示一個總數，所有必要非同站分段都成功才使用 CSDI 原始距離總和；任何一段最終失敗即完整回退 Citybus 卡片總距離，不混合來源。初始顯示 `查詢中…`／`查询中…`／`Checking…`；成功或回退均只顯示整數米，不加來源或「約」。

詳情允許各段獨立成功：成功段展示 `距離 · 約略分鐘` 並繪製 path；失敗段顯示 Citybus 分段米數而沒有時間或 path；Citybus 分段米數亦缺失時顯示距離暫不可用，不從總距離反推。摘要在全部必要 CSDI 段成功前保持查詢中；任一段最終失敗後立即顯示完整 Citybus 總距離，即使其他成功段仍可繼續加入時間線與地圖。Citybus 總耗時、到達、巴士段時間及 ETA 始終保持原值。

不新增 CSDI 專用手動重試入口。自動重試耗盡後頁面已可用；pull refresh 或重新進入會重新嘗試失敗段。現有 Citybus 詳情、巴士幾何、Map 與 ETA 錯誤／重試入口不變。

**否決方案：**卡片混合成功與 Citybus 分段可能產生無法解釋的總數；等待所有剩餘分段後才回退會延長已確定無法成功的 Loading；顯示來源標籤或每段錯誤按鈕會增加主通勤信息噪音。

### 8. 步行 path 由 presentation model 增量渲染並遵守署名

`RouteMapPresentationBuilder` 不再由端點製造步行直線。只有 `CSDISuccess` 生成 path presentation；每個子路徑的 stable id 為 `walk:<segmentId>:path:<index>`，保留上游有序點列與 path 邊界。`GoogleRouteMapRenderer` 在 camera idle／padding 更新後把該同一有序 path 投影到屏幕，以固定屏幕間距插值並按每個位置的局部屏幕切線放置較粗灰色開放折角 marker；不得另畫灰色實線、點線或虛線底圖，也不得在子路徑空隙補線。Loading、SameStop、失敗或回退均不生成 path presentation，marker 及其他成功 bus／walk paths 保持。renderer 只按 stable id 增刪差異；若 projection 或局部切線無法可靠取得，省略該步行折角而不使用整段 bearing、固定角度或脫離 geometry 的圖標。

地圖建立仍以香港中心作首幀。可靠站點結構到達後，用查詢起終點及所有可靠站點自動 fit 最多一次；晚到 bus geometry 或 pedestrian paths 不移動相機。使用者任何手勢把所有權交給 USER，之後異步結果不得自動 fit。使用者點擊「全覽」時，才以目前全部 marker、bus geometry 及 pedestrian paths 計算完整 bounds。

第一條 CSDI path 實際顯示時，在地圖左下安全區、Google Logo 與 bottom sheet 上方顯示官方地政總署標誌及雙行短署名：繁中 `步行：地政總署 · CSDI`／`© 香港特區政府`，簡中及英文使用獨立資源；點擊可開啟完整來源、版權及免責說明。全部沒有 CSDI path 時隱藏。署名與 map padding 必須避免遮擋 Google Logo、法律文字、返回、定位及全覽控件，並作為站名碰撞模型的保留矩形；不以另一個常駐圖例取代。

**否決方案：**保留端點直線直到 path 到達會短暫展示錯誤走法；path 失敗後畫直線會讓使用者誤認為真實導航；等待晚到 geometry 再首次 fit 會重現信息集中出現與搶鏡頭。

### 9. 步行排序以數值可用性分組且保持身份穩定

步行 `CSDISuccess` 與 `CitybusFallback` 都是可排序數值；升序或降序先排序所有數值，Loading 永遠置後。相同數值與 Loading 以本次查詢初始索引作 tie-break，避免每次 callback 任意換位。只有目前按步行距離排序時，步行狀態更新才造成位置變化；常用頁的置頂卡按 token 保持原序，只重排未置頂結果，搜尋頁重排全部結果。`resultId` 不包含新的展示狀態且不隨 CSDI 完成改變。任何 CSDI 漸進重排與自動刷新列表替換均經同一 viewport anchor policy：提交前保存第一可見 stable id 與相對頂部 pixel offset，提交後恢復；route 消失時選新排序中最接近的下一項，不能因 callback 到達而跳回頂部。

**否決方案：**降序時把 Loading 反轉到頂部會遮擋已有可比較結果；使用 callback 完成順序作 tie-break 會令慢網下排序不確定；重建 result identity 會讓 DiffUtil、置頂及已開啟詳情失去對應。

### 10. 診斷不記錄可重建使用者軌跡的資料

沿用 `RouteDetailDiagnostics` 的 test observer 與 debug-only Logcat，不新增 release 線上分析。coordinator 為每次 flight 分配進程內遞增匿名 ID；可記錄 cache hit／miss／expired、queue、priority promote、permit、single-flight join、訂閱數、attempt、retry、失敗分類、端點來源、30 米門禁原因及 stale callback。不得記錄完整 URL、stops JSON、精確或粗略坐標、地點名稱、stop id、`rawInfo`、session、使用者自訂名稱或返回軌跡點。

**否決方案：**對坐標 key 使用目前非加密 `hashCode` 仍可能由有限地理網格猜測；記錄完整 response 有披露使用者行程與上游 payload 的風險。

## Risks / Trade-offs

- **[首次卡片查詢增加 Citybus 詳情結構請求]** → 只按完整 request identity single-flight，重用 24 小時可靠結構，與詳情頁共用；起終點 CSDI 段在主端點可用後先行，不把所有工作串行化。
- **[地政總署服務限制短時間大量請求或暫時不可用]** → 全 App 公平上限 5、端點 single-flight、24 小時成功 cache、訂閱取消、8 秒邊界及最多一次瞬時重試；失敗立即使用 Citybus，不阻塞基本路線功能。
- **[預設一分鐘自動刷新反覆重試 CSDI 失敗段]** → `AUTOMATIC` trigger 使用 5 至 30 分鐘進程內指數退避；失敗不作成功 cache，手動刷新／重新進入仍可各繞過一次，成功即清除退避。
- **[30 米門禁可能拒絕少數合法網絡吸附]** → 保存不含坐標的原因診斷與固定 fixture；不得為提高成功率靜默放寬，需以可復現真實樣本另行調整規格。
- **[24 小時記憶體 cache 可能重用已變更的行人設施]** → 只保存進程內成功結果、失敗不 cache、進程重啟清空；第一版不承諾即時扶手電梯／升降機開放狀態。
- **[Citybus 總耗時與 CSDI 分段分鐘不一致]** → UI 不嘗試加總重構總耗時，分段分鐘清楚標示約略；實作完成後登記技術債，關閉需統一個人步速、時間依賴公交與轉乘重算。
- **[大量漸進卡片移動可能令使用者迷失]** → 只有主動選擇步行排序時移動，Loading 固定在數值後且 tie-break 穩定，置頂區域不動。
- **[邏輯會話未正確結束可能持有訂閱]** → ViewModel／session owner 不持有 Activity、View 或 GoogleMap；新查詢、返回與 clear 均有明確 close 測試，最後訂閱觸發排隊／在途取消。
- **[混合 Google 底圖與地政總署 overlay 的署名空間有限]** → 使用官方標誌、可見精簡來源／版權及可開啟完整說明，按 Insets 與 sheet 動態避讓，人工驗證 Google attribution 及控件不被遮擋。

## Migration Plan

1. 先建立純 model、request builder／parser、兩個 30 米門禁、取整與 segment planner 測試；加入可注入 fixture fetcher，生產預設仍走真實 HTTPS。
2. 實作進程級雙層 cache、優先隊列、global-5 single-flight、可取消 HTTP、重試及匿名診斷；以純並發測試證明上限、去重、訂閱與完成順序。
3. 把路線查詢接入 Citybus 詳情／端點規劃與 walking callback，新增卡片顯示狀態、`INITIAL／MANUAL／AUTOMATIC` 退避語義、統一 projection 及 viewport anchor；保留原 `walkingDistanceMeters` 和既有 result identity。
4. 擴展詳情邏輯會話、page reducer、formatter、adapter 與 presentation model，移除步行直線並沿每個 CSDI path 加入局部切線開放折角、相機門禁與署名 overlay。
5. 完成 focused tests、`./gradlew build`、三語／TalkBack／窄屏與任務自有 Google Maps 模擬器驗收，再作少量 Citybus + CSDI 只讀抽查並記錄外部驗證限制。
6. 實作與驗證完成後更新 `docs/technical-debt.md`，再按 OpenSpec apply 流程核對 tasks、嚴格驗證及提交。

沒有持久化 schema 或資料遷移。若需回退，可回退本 change 的程式與資源提交；Citybus 原始距離、時間與既有詳情／地圖資料仍保留，進程重啟即清除新增 cache，不需清理使用者資料。

## Open Questions

無阻塞問題。地政總署外部服務的長期可用性及行人設施即時狀態不在第一版保證範圍；未來若引入其他 travel mode、個人步速或導航文字，須另立 change 重新定義 cache key、語言、無障礙及總耗時契約。
