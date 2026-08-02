## Why

現有獨立路線詳情頁能完整展示站點、步行、轉乘與首程即時 ETA，但仍以純列表呈現，使用者無法理解查詢起終點、各巴士站、轉乘與整條路線的空間關係。現在既有詳情模型已具備所有站點坐標，亦已確認 Citybus `getlinep2p.php` 可提供分段道路幾何，適合在不取代 Citybus 業務資料的前提下加入 Google 地圖展示。

## What Changes

- 將現有 `RouteDetailActivity` 改為全屏 Google `MapView` 背景，以及不可隱藏的摘要／半屏／全屏三段式 persistent bottom sheet；保留現有 RecyclerView 路線時間線與失敗重試。
- 讓詳情啟動契約攜帶本次成功查詢的起終點快照，並在地圖上區分查詢起終點、所有巴士站、轉乘角色與設備目前位置。
- 新增 Citybus `getlinep2p.php?rdv=<routeVariant>&start=<boardingSeq>&dest=<alightingSeq>` 幾何 repository、parser、分段並發與一天進程內快取；請求不攜帶 session、Cookie 或瀏覽器 header。
- 以與時間線一致的分段色繪製巴士道路實線，以灰色虛線表示「步行連接（示意）」；同站轉乘不繪製虛假步行線，幾何失敗時不以站點直線冒充巴士道路。
- 加入地圖與時間線雙向選取、marker 自動展開所屬途經站、全覽路線、目前位置及動態 map padding；Google Logo 與法律文字不得被詳情窗遮擋。
- 摘要態展示緊湊首程即時 ETA，詳情頁前台每 60 秒刷新首程 ETA，進入後台或退出頁面後停止。
- 讓底圖、詳情、分段幾何、定位與 ETA 獨立降級；底圖完全不可用時自動展開全屏文字詳情，其餘單項失敗不得清空可靠內容。
- 支援三語、明暗模式、大字體、Activity／MapView 生命週期與可恢復頁面狀態；Google 底圖標籤允許沿用設備／第三方語言。
- 本變更不提供巴士車輛即時位置、乘車進度追蹤、Google Routes 步行導航、交通圖層、離線模式、地圖選點或參考 App 的收藏／分享／導航操作列。

## Capabilities

### New Capabilities

- `citybus-route-geometry`: 定義 `getlinep2p.php` 的最小請求、幾何解析與驗證、分段並發、一天進程內快取及局部失敗語義。
- `route-detail-google-map`: 定義獨立路線詳情頁的 Google 地圖背景、三段式詳情窗、標記與線條、相機、定位、雙向聯動、狀態恢復、無障礙及地圖失敗降級。

### Modified Capabilities

- `route-detail-bottom-sheet`: 在已完成的獨立全屏詳情頁基線上，把固定列表版面改為地圖背景與 persistent bottom sheet，同時保留摘要、時間線、重試和來源結果上下文。
- `citybus-first-leg-eta`: 在路線詳情摘要加入首程即時 ETA，並定義詳情頁前台 60 秒刷新、後台停止及局部失敗行為。

## Impact

- UI：`RouteDetailActivity`、`RouteDetailAdapter`、`RouteDetailUiFormatter`、詳情 XML、三語資源、明暗色彩、WindowInsets、BottomSheet 手勢與無障礙。
- 啟動與狀態：`RouteDetailNavigator`／`RouteDetailLaunchArgs`、常用結果與搜尋結果入口、查詢起終點快照、configuration change 與 process recreation。
- 資料層：新增 Citybus 路線幾何 model／parser／repository／cache；沿用 `getp2pstopinroute.php` 作為站點與轉乘主來源，沿用 DATA.GOV.HK 作為首程 ETA 來源。
- 外部依賴：新增 Maps SDK for Android、Manifest API key metadata 與版本所需相容配置；使用已完成 Billing、package、SHA-1 與 API 限制的 `GOOGLE_MAPS_API_KEY`，不把 key 提交至 git。
- 相容性：不改動 SQLite、已保存行程、`.bicroutes`、路線排序、Citybus 查詢結果或通知監控資料格式；本 change 以已完成的 `enhance-route-detail-page` 實作為基線。
- 驗證：保存 `780-CEF-1`、`104-KET-1` 等可復現幾何 fixture 與 live 請求證據；新增 parser、cache、展示模型、狀態機、ETA 生命週期與 instrumentation 測試，覆蓋三語×明暗、360dp、font scale 1.0／1.3／2.0、定位與局部失敗，最後執行 `./gradlew build` 及真實 Google 地圖驗收。
