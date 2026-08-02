# 路線詳情 Google 地圖整合設計

日期：2026-08-03
狀態：已確認

## 背景

目前路線結果會開啟獨立 `RouteDetailActivity`，並以單一 `RecyclerView` 展示路線摘要、起終點步行、乘車段、轉乘、途經站、Citybus 預計時間與首程即時 ETA。現有資料模型已包含每一巴士站的名稱、站序、stop id、route variant 與經緯度，但頁面仍是一般全屏列表，沒有地圖空間關係。

本設計在現有獨立路線詳情頁上加入 Google Maps SDK for Android。地圖成為整個頁面的背景，路線詳情成為不可隱藏的三段式 persistent bottom sheet。Citybus 繼續負責業務路線、站點、轉乘與 ETA；Google Maps 只負責底圖、目前位置、marker、polyline 與相機互動。

## 目標

- 進入路線詳情時立即顯示既有摘要，並漸進載入 Google 底圖、所有站點與巴士道路幾何。
- 在地圖上展示查詢起點、終點、目前位置、每一巴士站、各乘車段與示意步行連接。
- 以摘要、半屏、全屏三個穩定停靠狀態協調地圖探索與長路線時間線閱讀。
- 讓地圖站點與現有時間線雙向選取、定位和展開。
- 保持 Citybus 詳情、ETA、現有時間線及失敗重試在地圖不可用時仍完整可用。
- 維持 XML、AppCompat、Material Components、輕量 Repository 與現有三語／明暗模式架構。

## 非目標

- 不顯示巴士車輛即時位置，不追蹤使用者乘車進度。
- 不接入 Google Routes API，不提供真實沿街步行導航。
- 不提供交通路況、衛星圖、地圖設定、Google 公交圖層、旋轉或傾斜。
- 不支援長按地圖選擇起終點。
- 不新增收藏、截圖、分享、打車、關注路線、下車提醒或導航操作列。
- 不提供離線重新開啟已查看路線；詳情與幾何只使用一天內存快取。
- 不在本次建立橫屏或平板側欄版面。

## 現有基線與整合邊界

現有 `RouteDetailActivity`、`RouteDetailAdapter`、`RouteDetailUiFormatter`、`CitybusRouteDetailRepository` 與結構化詳情模型保留。頁面容器由固定 App Bar 加 RecyclerView 改為：

```text
RouteDetailActivity
└── CoordinatorLayout
    ├── 全屏 MapView
    ├── 地圖浮層
    │   ├── 返回按鈕
    │   ├── 目前位置
    │   ├── 全覽路線
    │   └── 巴士／步行圖例
    └── Persistent Bottom Sheet
        ├── 48dp 可操作拖動區
        ├── 全屏態標題列
        └── 現有 RecyclerView 時間線
```

地圖使用 `MapView` 而不是新的 Fragment 或模態 `BottomSheetDialogFragment`。Activity 明確轉發 MapView 生命週期，Material `BottomSheetBehavior` 負責三段停靠、巢狀捲動與動態 padding。

Google Maps SDK 類型不得進入 repository、parser 或領域模型。地圖 renderer 只消費純 Kotlin 展示模型，避免資料取得、產品語義與供應商 SDK 耦合。

## 啟動契約與查詢快照

路線結果本身沒有保存本次查詢的起終點，因此詳情啟動契約需要新增查詢快照：

```text
RouteDetailNavigator.open(
    route,
    queryOrigin,
    queryDestination
)
```

快照包含名稱、緯度與經度：

- 常用行程結果使用當前實際查詢所對應 `RouteConfig` 的起終點。
- 搜尋頁使用最近一次成功查詢的 `successfulQueryOrigin` 與 `successfulQueryDestination`。
- 使用者只編輯輸入但尚未重新查詢時，不得把新輸入傳入舊結果的詳情頁。
- 啟動參數只保存可恢復的 primitive 值，支援 configuration change 與 process recreation。
- 快照缺失時仍開啟詳情，但省略查詢起終點 marker 與首尾步行連接。

查詢起點／終點與設備目前位置是不同語義。步行連接永遠基於查詢快照，不能因藍點移動而改寫行程方案。

## 資料來源與元件

### Citybus 路線詳情

現有 `getp2pstopinroute.php` 繼續提供：

- 起點與終點名稱；
- 各乘車段與 route variant；
- 上車、下車及所有途經站；
- 站點名稱、stop id、站序與坐標；
- 分段票價、Citybus 預計時間；
- 起點、轉乘與終點步行段；
- 同站轉乘或步行前往轉車站語義。

### Citybus 道路幾何

新增 `CitybusRouteGeometryRepository` 與獨立 parser，按乘車段請求：

```text
https://mobile.citybus.com.hk/nwp3/getlinep2p.php
  ?rdv={routeVariant}
  &start={boardingSeq}
  &dest={alightingSeq}
```

請求不得攜帶 Cookie、`ssid`、`sysid`、時間戳、Referer、User-Agent 或其他靜態瀏覽器 header。回應每一有效行解析為 `pointId,latitude,longitude`。

每段幾何必須驗證：

- `start <= dest`；
- 至少兩個有效點；
- 經緯度位於合法範圍；
- 空內容與 malformed 行不能被視為成功；
- 詳情站點可用時，幾何首尾需要與上下車站位於合理距離；
- 不得在幾何失敗時以站點直線冒充巴士道路路線。

每段最多三個並行請求。一天內存快取 key 為 `routeVariant + boardingSeq + alightingSeq`，不包含語言；失敗結果不快取。

### 地圖展示模型

新增純 Kotlin `RouteMapPresentationBuilder`，輸入查詢快照、結構化詳情、分段幾何與選中狀態，輸出：

- 起點、終點、上車、下車、途經與轉乘 marker；
- 各乘車段 polyline 與分段色；
- 示意步行連接；
- 圖例語義；
- 初始 camera bounds；
- marker 與時間線 stable id 對應。

`GoogleRouteMapRenderer` 只把展示模型套用到 GoogleMap，並處理 marker／polyline 實例的差量更新。

## 漸進載入

進入頁面後按以下順序展示：

1. 立即使用啟動參數顯示路線摘要。
2. MapView、Citybus 詳情與各乘車段幾何並行載入。
3. 詳情先到時先顯示時間線、可靠站點 marker 與角色。
4. 幾何先到時暫存，等待詳情坐標完成首尾合理性檢查。
5. 兩者可用後逐段增加道路 polyline，不重新建立整張地圖。
6. 首程 ETA 獨立載入，頁面前台每 60 秒刷新一次。

詳情、幾何、MapView、定位與 ETA 使用獨立狀態。語言版本改變、重試或新 generation 產生後，舊回應不得覆蓋目前頁面。

## 三段式詳情窗

bottom sheet 不允許隱藏或下拉關閉，只有三個停靠狀態：

### 摘要態

- 普通字體目標約佔頁面 25% 至 30%。
- 高度由摘要實際內容決定；大字體可增長至約 40% 至 45%。
- 只顯示路線鏈、總耗時、預計到達、總票價、各乘車段途經站數之和、總步行距離、首程即時 ETA 與必要載入／失敗狀態；站數不重複計算上下車或轉乘端點。
- 不顯示站點時間線或額外操作列。
- 列表鎖定頂部，確保摘要完整可見。

### 半屏態

- 目標約佔頁面 55%，但必須高於摘要所需高度。
- 主要由使用者拖動把手並在中間高度放開，或點擊地圖站點進入。
- 同時提供地圖與時間線上下文。

### 全屏態

- 詳情窗覆蓋地圖內容，保留系統狀態列與詳情標題區域。
- 摘要作為 RecyclerView 第一項，可隨長內容向上捲出。
- 固定標題列只保留返回按鈕與「路線詳情」。

### 狀態轉換

- 初次進入為摘要態。
- 摘要內容向上滑直接進入全屏態，跳過半屏態。
- 拖動把手可以吸附至半屏態。
- 半屏態繼續向上滑進入全屏態。
- 全屏態向下滑依次回到半屏態與摘要態。
- 摘要態繼續向下拖只產生阻尼並回彈，不退出頁面。
- 點擊拖動區時，摘要／半屏進入全屏，全屏回到摘要。
- 內容向下捲動時先回到列表頂部，再降低詳情窗檔位。
- 地圖區域開始的手勢只操作地圖，不改變詳情窗高度。

系統返回、返回手勢與頁面返回按鈕在任何檔位都直接返回原路線結果頁，不逐段收合。

## 返回按鈕與地圖控件

摘要態與半屏態使用地圖左上角的圓形浮動返回按鈕。進入全屏態時，同一語義的返回按鈕遷入詳情窗標題列；畫面不得同時出現兩個返回入口。

地圖只提供：

- 返回；
- 目前位置；
- 全覽路線；
- Google 必要標誌與 attribution。

不提供交通路況、衛星圖、地圖類型、設定、回饋、縮放加減、旋轉或傾斜。Google Logo 與法律文字需要依 bottom sheet 即時高度更新 map padding，始終保持可見。

## 地圖視覺語義

- 各巴士乘車段使用與現有時間線一致的模式感知分段色。
- 巴士路線使用帶對比描邊的彩色實線。
- 普通途經站使用低強度小圓點，預設不顯示站名。
- 上車、下車、轉乘、查詢起點與查詢終點使用較大且形狀不同的 marker。
- 目前位置使用 Google 原生藍點，不與查詢起點合併成同一資料語義。
- 同站轉乘合併成單一複合 marker，不繪製步行線。
- 步行轉乘保留上一下車站與下一上車站，使用灰色示意虛線連接。
- 即使兩站坐標相同，Citybus 標記為步行轉乘時仍保留其步行語義；不得以坐標猜測改寫供應商語義。

地圖可見時常駐緊湊圖例：

```text
彩色實線  巴士路線
灰色虛線  步行連接（示意）
```

步行 marker 或連接被選中時再次顯示「示意」語義。App 不得把虛線描述為真實沿街路徑或提供「開始導航」。

## 相機與站點聯動

- 完整路線首次可用時執行一次全覽，邊界包含查詢起點、所有乘車段與查詢終點。
- 遠離路線的目前位置不納入初始 bounds，避免整段路線縮成不可讀區域。
- bottom sheet 移動只更新 map padding，不重置使用者平移或縮放。
- 點擊「全覽路線」才恢復完整行程視角。
- 點擊「目前位置」才把鏡頭移至藍點；地圖不自動跟隨移動。
- 頁面前台持續更新藍點，進入後台或退出後停止，不申請背景定位，不保存位置軌跡。

地圖與時間線雙向聯動：

- 摘要態點擊站點 marker 時進入半屏態。
- marker 顯示站名、角色與所屬路線。
- 普通途經站尚未展開時，只自動展開其所屬乘車段，其他段保持原狀。
- 時間線捲動並高亮對應站點；再次點擊同一 marker 不自動收起。
- 全屏態點擊時間線站點時回到半屏態，地圖居中並高亮該站。
- 收合回摘要態之前，列表自動回到頂部。

## 即時 ETA

摘要態新增緊湊首程即時 ETA。首個乘車段仍同時保留 DATA.GOV.HK 即時 ETA 與 Citybus 預計時間，兩者使用不同來源標籤與視覺層級。後續乘車段不得推算即時 ETA。

- 進入頁面立即刷新一次首程 ETA。
- 頁面前台每 60 秒刷新一次，與現有通知監控頻率一致。
- 進入後台停止計時器。
- 回到前台且距上次成功刷新超過 60 秒時立即刷新。
- ETA 失敗只降級 ETA 區域，不影響地圖、詳情或檔位。
- 退出頁面後取消計時器與在途回調。

## 定位權限

- 已授權時頁面可直接啟用前台目前位置藍點。
- 未授權時進入頁面不自動彈出權限請求。
- 使用者點擊「目前位置」時才請求權限。
- 一般拒絕顯示中性說明；永久拒絕提供前往 App 設定的入口。
- 系統定位關閉時提供前往系統定位設定的入口。
- 定位拒絕、超時或服務不可用不影響路線地圖與時間線。

## 錯誤與降級

| 失敗項目 | 行為 |
|---|---|
| Google 底圖完全不可用 | 自動進入全屏詳情，顯示地圖不可用提示 |
| 單一乘車段幾何失敗 | 保留該段所有站點，不畫虛假的站點直線 |
| Citybus 詳情失敗 | 保留啟動摘要、查詢起終點、目前位置及已通過基礎驗證的幾何；時間線顯示錯誤與重試 |
| 查詢快照缺失 | 省略查詢起終點及首尾步行連接 |
| 定位失敗 | 隱藏藍點與定位結果，不影響其他內容 |
| ETA 失敗 | 只顯示即時候車不可用 |
| 可選欄位缺失 | 隱藏缺失值，保留可靠主結構 |

「重試缺失內容」只重試失敗部分，不重新請求仍有效的成功資料。底圖完全不可用時不保留大面積無意義空白；若只有幾何或詳情局部失敗而底圖仍可用，則不自動改變目前檔位。

## 狀態與生命週期

新增不持有 View、GoogleMap、Marker 或 Polyline 的可恢復狀態：

```text
RouteDetailScreenState
├── sheetDetent
├── selectedStop
├── expandedLegs
├── listPosition
├── cameraSnapshot
├── detailState
├── geometryStates
├── etaState
└── locationPermissionState
```

configuration change、主題／語言重建與短暫背景切換恢復 bottom sheet 檔位、相機、選中站點、途經站展開狀態與列表位置。process recreation 從 primitive 啟動參數重建資料請求與展示狀態。使用者真正返回結果頁後再次開啟同一路線，視為新一次查看，從摘要態與完整路線鏡頭開始。

MapView 必須收到 `onCreate`、`onStart`、`onResume`、`onPause`、`onStop`、`onDestroy`、`onLowMemory` 與 `onSaveInstanceState`。任何舊 generation、舊語言或已銷毀頁面的回調都不得更新 UI。

## 主題、語言與無障礙

- Google 地圖明暗模式使用 AppCompat 當前實際 `uiMode`，不只是系統模式。
- App 自有 marker、圖例、錯誤、按鈕和無障礙文案提供香港繁體、獨立簡體與自然英文。
- Citybus 站名繼續使用現有實際語言 mapping；幾何快取不按語言隔離。
- Google 底圖道路、地區與 POI 標籤可能跟隨設備語言，視為第三方底圖例外。
- 地圖不是唯一資訊來源；所有站點、轉乘與步行語義必須在時間線中完整可讀。
- 拖動區提供至少 48dp 觸控與焦點範圍。
- 點擊拖動區可在摘要／半屏與全屏之間切換，TalkBack 讀出目前狀態和操作。
- 半屏態不承載獨有資訊，因此輔助操作不需要精確停在半屏。
- 角色以形狀、大小、文字與位置共同表達，不只使用顏色。
- font scale 2.0 下不得縮字、裁切核心內容或隱藏必要指標。

## Google Maps 配置與安全

- 使用 `GOOGLE_MAPS_API_KEY` 注入 Manifest 的 `com.google.android.geo.API_KEY`。
- key 只存在本機／CI secret，不寫入源碼、資源或 git。
- Google Cloud 已完成 Billing、Maps SDK for Android、Android package、debug／Play App Signing SHA-1 與 API 限制。
- Android package 為 `com.golink.busiscoming`。
- Maps SDK key 不與 Geocoding 或未來 Web Service API 共用。
- 依目前 Maps SDK 版本要求加入必要的 Google Play Services 及舊版相容 manifest 配置。
- 發佈前重新檢查 Google Maps SDK 對 Play Data Safety 與隱私政策的影響。

## 驗證

### 單元測試

- 以使用者提供的 `780-CEF-1` 與 `104-KET-1` 回應建立幾何 fixture。
- 覆蓋空內容、malformed 行、非法坐標、少於兩點、反向站序與端點不合理。
- 驗證 geometry URL 只包含 `rdv/start/dest`。
- 驗證一天內存快取命中、過期、失敗不快取與分段隔離。
- 驗證單程、多段、同站轉乘、步行轉乘、缺失起終點與局部幾何失敗的地圖展示模型。
- 驗證三檔狀態機、禁止隱藏、返回直接退出、摘要自適應高度與狀態恢復。
- 驗證 ETA 前台 60 秒刷新、後台停止及舊 generation 作廢。
- 驗證啟動參數可保存查詢快照並支援 process recreation。

### Instrumentation 與人工驗證

- 摘要、半屏、全屏三檔與所有轉換手勢。
- 地圖 marker 與時間線雙向聯動及自動展開途經站。
- MapView 生命週期、Activity 重建與 process recreation。
- 定位已授權、首次拒絕、永久拒絕與系統定位關閉。
- 底圖、詳情、單段幾何與 ETA 各自失敗的降級。
- Google Logo、法律文字、地圖控件不被 bottom sheet 或 WindowInsets 遮擋。
- 繁體／簡體／英文、淺色／深色、360dp、font scale 1.0／1.3／2.0。
- 橫屏與寬屏保持可用，但不要求側欄版面。
- 以真實 Maps SDK key 和帶 Google Play Services 的設備驗證明暗底圖與目前位置。

Live Citybus 驗證至少覆蓋單程、多段轉乘、同站轉乘與步行轉乘，確認無 session 的 `getlinep2p.php` 請求可重複解析並保存回歸樣本。最終執行完整 `./gradlew build`。

## 完成標準

- 點擊路線卡後立即進入詳情並顯示摘要，不被地圖載入阻塞。
- 地圖成功時可靠展示完整巴士道路幾何、所有站點、查詢起終點、示意步行與前台目前位置。
- 三檔詳情窗、返回按鈕遷移、相機 padding、地圖手勢與時間線捲動互不衝突。
- 地圖與時間線選取保持一致，途經站可由 marker 自動展開。
- 地圖、幾何、詳情、定位或 ETA 任一失敗時，其餘可靠內容仍可使用。
- App 不聲稱即時車輛位置或真實步行導航。
- 既有路線詳情、路線結果、排序、ETA 與通知監控行為不發生無關回歸。
