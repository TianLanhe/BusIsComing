## Context

`enhance-route-detail-page` 已把原有詳情 Bottom Sheet 改為獨立 `RouteDetailActivity`，以單一 RecyclerView 展示摘要、起終點步行、乘車段、轉乘、途經站、Citybus 預計時間與首程即時 ETA。現有 `RouteDetailStop` 已包含 stop id、站序、route variant 與坐標；`RouteDetailLaunchArgs` 則只保存路線結果本身，尚未攜帶本次查詢的起終點坐標。

Google Maps SDK 只提供底圖與繪製能力，不計算 Citybus 候選路線。直接按站點順序以直線連接會穿過樓宇、海面或非行車道路；Citybus `getlinep2p.php` 已確認可按 route variant 與上下車站序返回道路幾何點，因此本 change 需要同時處理新外部資料、地圖生命週期、可拖動詳情窗、定位權限、ETA 前台刷新及部分失敗。

實作必須維持現有 XML + AppCompat + Material Components、輕量 Repository 分層、三語與明暗偏好，不建立後端服務，不把 Google 或 Citybus HTTP／解析邏輯放入 Activity 或 Adapter。

## Goals / Non-Goals

**Goals:**

- 讓 Google Map 成為獨立路線詳情頁背景，現有詳情 RecyclerView 成為摘要／半屏／全屏三段式 persistent bottom sheet。
- 漸進展示查詢起終點、所有巴士站、分段道路幾何、示意步行與設備目前位置。
- 讓 marker 與時間線雙向選取，並在需要時自動展開途經站。
- 讓地圖、詳情、幾何、定位與 ETA 獨立載入、恢復和降級。
- 保留可測試的 parser、cache、展示模型、狀態機與生命週期邊界。

**Non-Goals:**

- 不接入 Google Routes API，不提供沿街步行導航。
- 不顯示巴士車輛位置、乘車進度或背景位置追蹤。
- 不提供交通／衛星圖層、地圖選點、離線模式或新的詳情頁操作列。
- 不把橫屏或平板改成另一套側欄互動。
- 不改動 SQLite、已保存行程、路線排序、通知監控或 `.bicroutes` 格式。

## Decisions

### 1. 在既有 Activity 中使用 MapView 與 persistent BottomSheetBehavior

`activity_route_detail.xml` 改為 `CoordinatorLayout`：全屏 `MapView` 位於底層，浮動返回／定位／全覽／圖例位於中層，包含既有 RecyclerView 的 Material persistent bottom sheet 位於最上層。Activity 完整轉發 MapView 生命週期。

選擇 `MapView` 是因為現有頁面已是 Activity，能直接復用 Adapter、formatter、測試注入點與啟動參數。被否決的方案：

- `SupportMapFragment` 會增加 Fragment 恢復與測試注入複雜度，卻沒有現成 Fragment 邊界可復用。
- `BottomSheetDialogFragment` 的模態、可關閉語義與「詳情窗不可隱藏」衝突。
- 自製動畫面板需要重寫 Material 已提供的 nested scroll、可及性與狀態恢復。

### 2. 以查詢快照補齊行程起終點

`RouteDetailNavigator` 與 `RouteDetailLaunchArgs` 新增可序列化的 `queryOrigin/queryDestination` 名稱與坐標。常用結果從當前實際查詢的 `RouteConfig` 取得；搜尋結果使用最近一次成功查詢快照，不讀取尚未提交的新輸入。

不把起終點寫回 `BusRouteOption`，因為同一候選路線只在一次查詢上下文中有意義，污染路線結果模型會讓查詢與展示責任混合。快照缺失時省略行程端點與首尾步行連接，詳情仍可開啟。

### 3. 以 Citybus getlinep2p 提供巴士道路幾何

新增獨立 `CitybusRouteGeometryRepository`、parser、model 與 cache。每段請求只包含：

```text
getlinep2p.php?rdv=<routeVariant>&start=<boardingSeq>&dest=<alightingSeq>
```

不攜帶 Cookie、`ssid`、`sysid`、時間戳或瀏覽器 header。parser 解析 `pointId,latitude,longitude`，拒絕空內容、少於兩點、非法坐標與無效站序；詳情坐標可用時再校驗幾何首尾與上下車站的合理距離。

被否決的替代方案：

- 站點直線不能代表巴士道路，失敗時亦不得作為 fallback。
- `showstops2.php`／`getp2pstopinroute.php` 適合提供站點，不包含足夠道路採樣。
- Google Routes 不應取代 Citybus 候選方案，且客戶端 Web Service key 需要額外安全與計費架構。

### 4. 以純展示模型隔離 Google Maps SDK

`RouteMapPresentationBuilder` 把查詢快照、RouteDetail、各段 geometry、分段色與選取狀態轉成純 Kotlin marker／polyline／walking connector／camera bounds。`GoogleRouteMapRenderer` 才持有 GoogleMap、Marker 與 Polyline。

此邊界讓 parser、路線語義、同站／步行轉乘、相機範圍與 marker stable id 可在 JVM 測試，不需要真實 Google Map。Activity 只協調生命週期和 UI 事件，不直接組合 HTTP 或解析資料。

### 5. 詳情與幾何並行且分段增量展示

進入頁面立即展示啟動摘要。MapView、Citybus 詳情與每段 geometry 並行載入；geometry 最多三段並發。詳情先到時先顯示時間線與站點，geometry 通過校驗後逐段加入道路線。所有非同步工作攜帶 request generation 與語言版本，舊頁面、舊重試或舊語言結果不得更新目前 UI。

詳情沿用 `rawInfo + lang` 一天進程內快取。geometry 使用 `routeVariant + boardingSeq + alightingSeq` 一天進程內快取，不按語言隔離；失敗結果不快取。本次不建立磁碟快取，因為離線資料版本、ETA 時效與過期提示需要獨立設計。

### 6. 使用三個穩定詳情窗檔位

摘要態按內容自適應，普通字體目標為 25% 至 30%；半屏態目標為 55%；全屏態覆蓋地圖內容。`isHideable=false`，摘要態繼續下拉只回彈。

- 摘要內容向上滑直接進入全屏。
- 拖動 48dp 把手區可停靠半屏。
- 全屏向下依次經半屏、摘要。
- 點擊把手時，摘要／半屏進入全屏，全屏回摘要。
- 摘要態點 marker 進入半屏。
- 系統返回、返回手勢和頁面返回在任何檔位都直接退出。

摘要／半屏時返回按鈕懸浮在地圖；全屏時遷入詳情標題列，任何時刻只保留一個返回入口。摘要是 RecyclerView 第一項，展開後可捲出；收合摘要前列表回到頂部。

### 7. 地圖相機尊重使用者操作與 attribution

完整路線首次可用時執行一次全覽，初始 bounds 包含查詢起點、所有路線段與查詢終點，不強制包含遠處目前位置。bottom sheet 移動只調整 map padding，不重置使用者鏡頭；只有 marker 選取、目前位置與全覽按鈕主動移動相機。

Google Logo 和法律文字隨 bottom sheet 高度更新 padding，不能被窗體或 WindowInsets 遮擋。地圖停用旋轉、傾斜、交通與衛星圖層，只保留平移、縮放、目前位置和全覽路線。

### 8. Citybus 決定轉乘語義，步行只作示意

各乘車段使用與時間線相同分段色和帶描邊實線。普通站為小圓點，上下車、轉乘、查詢端點使用不同形狀。Citybus 標記同站轉乘時使用單一複合 marker 且不畫步行線；標記步行轉乘時保留兩個站點和灰色示意虛線，即使坐標相同亦不自行改判。

地圖常駐「彩色實線＝巴士路線、灰色虛線＝步行連接（示意）」圖例。App 不把虛線描述為真實道路或導航。

### 9. 目前位置只在頁面前台更新

已授權時啟用 Google 原生藍點；未授權時進入頁面不自動彈權限，只有點擊目前位置按鈕才請求。一般拒絕、永久拒絕與系統定位關閉分別提供中性說明或設定入口。

位置只在頁面前台持續更新，不自動跟隨相機、不申請背景位置、不保存軌跡。查詢起點與藍點保持不同語義。

### 10. 首程 ETA 使用前台 60 秒刷新

摘要新增緊湊首程 ETA，首段卡片仍分開展示 DATA.GOV.HK 即時 ETA 與 Citybus 預計時間。頁面前台每 60 秒刷新首程，回到前台且成功資料已超過 60 秒時立即刷新；後台與退出時停止。後續乘車段不推算即時 ETA。

沿用現有 ETA service 與匹配規則，不啟動通知監控服務。ETA 失敗只降級摘要與首段的 ETA 區域。

### 11. 地圖、詳情、幾何、定位與 ETA 獨立降級

頁面狀態不持有 Android View 或 Google SDK 實例，分別保存 map、detail、geometry、ETA、location、sheet detent、camera、selected stop、expanded legs 與 list position。

- 底圖完全不可用時自動全屏，保留文字詳情並顯示地圖錯誤。
- 單段 geometry 失敗時保留站點，不畫該段虛假直線。
- 詳情失敗時保留摘要、查詢端點、目前位置與可獨立驗證的 geometry。
- 定位或 ETA 失敗只影響自身區域。
- 重試只重新請求失敗部分。

### 12. 主題、語言、金鑰與資料披露

Maps SDK 使用 AppCompat 當前 `uiMode` 選擇明暗地圖；App 自有地圖文案提供繁體、簡體、英文。Google 底圖道路與 POI 標籤允許跟隨設備或第三方語言，不能承諾跟隨 App 內 locale。

Manifest 以 `GOOGLE_MAPS_API_KEY` placeholder 注入 `com.google.android.geo.API_KEY`。key 不進入源碼或 git，並使用已配置的 Android package、debug／Play SHA-1 與 Maps SDK API 限制。發佈前重新檢查 Play Data Safety 與隱私披露。

## Risks / Trade-offs

- [Citybus 私有 mobile endpoint 可能變更或回空] → 保存使用者提供的兩份 fixture、擴充多種 live 樣本、封裝 parser、拒絕 malformed／空成功並保留逐段降級。
- [Google Map、RecyclerView 與 BottomSheet 手勢競爭] → 地圖起始手勢只交給地圖；把手與 RecyclerView 使用 Material nested scroll，並以 instrumentation 覆蓋三檔轉換。
- [MapView 生命週期遺漏造成洩漏或黑屏] → Activity 明確轉發全部生命週期，狀態模型不保存 Google SDK 實例，重建後由 renderer 重畫。
- [路線點過多造成重繪成本] → geometry 只在 route／theme／selection 變化時差量更新，不跟隨 ETA 或位置更新重畫全部 polyline；實測後才考慮不改變形狀的簡化。
- [大字體使摘要超過目標比例] → 摘要高度以內容為準並允許增長，不縮字或裁切；半屏高度不得低於摘要。
- [Google 底圖語言與 App locale 不一致] → 把 Google 文字視為第三方內容例外，App 自有 marker、圖例和時間線仍嚴格三語。
- [多個外部狀態同時失敗導致狀態爆炸] → 使用分區狀態與單向 presentation builder；重試只處理失敗分區，可靠資料不被清空。
- [新的 dependent change 與尚未歸檔的 `enhance-route-detail-page` 規格重疊] → 實作與 delta spec 明確以該已完成 change 的獨立 Activity 行為為基線；歸檔時先處理前置 change，再同步本 change。

## Migration Plan

1. 確認 `enhance-route-detail-page` 的完成實作與規格作為基線，保留既有詳情測試。
2. 加入 Maps SDK 依賴、Manifest metadata、相容配置與本機 key 注入，不提交 secret。
3. 擴充詳情啟動參數與兩個結果入口，先以測試固定查詢快照。
4. 實作 geometry parser／repository／cache 與 fixture，再加入 presentation builder／renderer。
5. 把 Activity 版面改為 MapView + persistent bottom sheet，接入三檔狀態、相機、marker 聯動與生命週期。
6. 接入位置權限與 60 秒 ETA 前台刷新，完成局部錯誤重試。
7. 執行單元、instrumentation、三語明暗大字體、真實 Citybus 與真實 Google 地圖驗證，最後執行 `./gradlew build`。

回滾時可移除 Maps SDK、geometry 與 map renderer，並把 Activity layout 還原為固定 Toolbar + RecyclerView；詳情 model、Citybus 詳情解析、ETA、SQLite 與查詢結果格式均不需要資料遷移。

## Open Questions

無。三檔行為、返回、步行示意、定位、ETA 頻率、失敗降級、語言例外、快取與範圍均已在提案前確認。
