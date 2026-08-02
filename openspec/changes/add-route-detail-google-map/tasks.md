## 1. 基線與 Google Maps SDK 配置

- [x] 1.1 確認目前分支包含已完成 `enhance-route-detail-page` 的獨立 `RouteDetailActivity` 實作，執行既有詳情頁 JVM／instrumentation 契約測試並記錄基線結果；歸檔 OpenSpec 時先處理該前置 change。
- [x] 1.2 在 version catalog 與 `app` 模組加入 Maps SDK for Android 依賴，沿用現有 AGP／Kotlin 配置且不重複套用 Kotlin Android plugin，確認 debug 與 release 均可解析依賴。
- [x] 1.3 從 `local.properties`／環境變數讀取 `GOOGLE_MAPS_API_KEY` 並以 manifest placeholder 注入 `com.google.android.geo.API_KEY`，加入 Maps SDK 版本所需的 manifest 相容配置；任何 key 值均不得寫入源碼、資源、測試輸出或 git。
- [x] 1.4 為地圖載入、控件、圖例、marker、polyline 及詳情窗新增明暗模式共用的 color／dimension／style 資源，確保 Google attribution 可配合 WindowInsets 和動態 map padding 保持可見。

## 2. 詳情啟動契約與查詢快照

- [x] 2.1 新增可序列化的查詢端點模型，擴充 `RouteDetailLaunchArgs`／primitive bundle 契約以攜帶起終點名稱與坐標，同時保留缺失快照時仍可開啟詳情的兼容行為。
- [x] 2.2 擴充 `RouteDetailNavigator` 與常用行程結果入口，從產生目前結果的實際 `RouteConfig` 傳入起終點快照，確認開啟詳情不增加使用次數或重新查詢路線。
- [x] 2.3 擴充搜尋結果入口，只傳入 `successfulQueryOrigin`／`successfulQueryDestination` 對應的最近一次成功查詢快照，禁止使用已編輯但尚未成功查詢的新輸入。
- [x] 2.4 更新 `RouteDetailLaunchArgsTest`、入口契約測試及 process recreation 測試，覆蓋完整快照、部分缺失、舊 bundle 兼容與來源頁物件已不存在的情況。

## 3. Citybus 分段道路幾何

- [x] 3.1 保存使用者提供的 `780-CEF-1`、`104-KET-1` 原始 `getlinep2p.php` 回應及至少一個多段轉乘樣本為測試 fixture，並在測試說明記錄不帶 Cookie／session 的等價最小 cURL。
- [x] 3.2 新增路線幾何 point／segment／結果模型與純 parser，按原順序解析 `pointId,latitude,longitude`，忽略單一 malformed 行並拒絕空回應、非法坐標或不足兩個有效點。
- [x] 3.3 新增 `CitybusRouteGeometryRepository`，只以 `rdv`、`start`、`dest` 構造 `getlinep2p.php` 請求；無效 route variant／站序時不得發送請求，亦不得加入 Cookie、session、語言、時間戳或瀏覽器 header。
- [x] 3.4 在詳情站點坐標可用時驗證幾何首尾合理距離，讓不一致或失敗分段保留站點但不產生替代巴士直線。
- [x] 3.5 實作以 `routeVariant + boardingSeq + alightingSeq` 為 key、與語言無關的一天進程內成功快取、相同請求去重、最多 3 個在途請求及逐段 callback；失敗不得快取。
- [x] 3.6 為 parser、URL／header、站序驗證、端點距離、快取過期、請求去重、三路並發、局部失敗與 generation 作廢補齊 JVM 回歸測試，並以真實最小 HTTP 請求抽查 fixture 仍能重複解析。

## 4. 純地圖展示模型與 Google renderer

- [x] 4.1 實作純 Kotlin `RouteMapPresentationBuilder`，把查詢快照、`RouteDetail`、分段 geometry、分段色與選取狀態轉換為具有 stable id 的 marker、巴士 polyline、示意步行 connector、圖例及完整行程 bounds。
- [x] 4.2 依 Citybus 詳情保留同站／步行轉乘語義：同站轉乘產生單一複合 marker 且不畫步行線，步行轉乘保留兩端角色並畫灰色示意虛線，首尾快照可用時才畫首尾示意步行。
- [x] 4.3 為普通站、上下車站、轉乘、查詢端點建立不只依賴顏色的 marker 規則；巴士道路使用與時間線一致的分段實線與對比描邊，普通站預設不顯示全部標籤。
- [x] 4.4 實作只持有 Google SDK 物件的 `GoogleRouteMapRenderer`，支援分段增量繪製、差量選取高亮、明暗地圖樣式、平移／縮放，並停用旋轉、傾斜、交通、衛星及多餘內建控件。
- [x] 4.5 為 presentation builder 補齊單段、多段、同站轉乘、步行轉乘、缺失快照、單段幾何失敗、stable id、初始 bounds 與無虛假巴士直線的 JVM 測試。

## 5. 三段式詳情頁結構與狀態

- [x] 5.1 把 `activity_route_detail.xml` 改為 `CoordinatorLayout`：全屏 `MapView`、安全區浮動返回／目前位置／全覽／緊湊圖例，以及承載既有 RecyclerView 的不可隱藏 Material persistent bottom sheet。
- [x] 5.2 建立可單測的摘要／半屏／全屏 detent 狀態與尺寸計算：普通摘要約 25% 至 30% 且按內容增高、半屏約 55% 且不低於摘要、全屏覆蓋內容、摘要不得下拉隱藏。
- [x] 5.3 實作手勢與把手狀態機：摘要內容上滑直接全屏、拖動至少 48dp 把手可停半屏、全屏下拉依次半屏／摘要、把手點擊摘要或半屏進全屏而全屏回摘要。
- [x] 5.4 在摘要／半屏顯示地圖左上浮動返回，在全屏只顯示詳情標題列返回；系統返回、返回手勢及兩個頁面返回入口在任何檔位均直接退出並恢復來源上下文。
- [x] 5.5 讓摘要繼續作為 RecyclerView 第一項並可在展開後捲出；收合至摘要前把列表恢復頂部，維持大量途經站的 nested scrolling 與逐段展開／收合。
- [x] 5.6 建立只保存 primitive／純模型的頁面狀態，於 configuration change／process recreation 恢復 detent、相機、選中站點、展開乘車段及列表位置；真正退出後再次開啟回到摘要與初始全覽。

## 6. 漸進載入、地圖聯動與局部降級

- [x] 6.1 重構 `RouteDetailActivity` 協調流程，讓啟動摘要、MapView、Citybus 詳情、各段 geometry、定位與 ETA 使用獨立狀態並行載入；所有 callback 以生命週期、request generation 與語言版本拒絕過期結果。
- [x] 6.2 完整轉發 MapView 的建立、啟動、恢復、暫停、停止、低記憶體、保存狀態及銷毀生命週期，且 Activity／renderer 銷毀後不得接受非同步 UI 更新。
- [x] 6.3 實作首次可靠完整路線的一次性 camera fit，初始 bounds 包含查詢起點、所有乘車段與查詢終點而不強制包含遠處藍點；詳情窗移動只更新 map padding，不重置使用者鏡頭。
- [x] 6.4 實作全覽路線、目前位置及 marker 主動相機操作；除這些明確操作與站點選取外，不得在 ETA、定位或詳情更新時自動重置相機。
- [x] 6.5 實作 marker／時間線雙向聯動：摘要點 marker 進半屏且只展開所屬段，半屏點 marker 保持半屏並定位，高亮 marker 再點不收起，全屏點時間線站點收至半屏並把 marker 移入可見區。
- [x] 6.6 實作分區錯誤與局部重試：底圖完全不可用時自動全屏保留文字詳情；詳情、單段幾何、定位或 ETA 失敗只降級自身區域；重試只載入失敗／過期部分並保留有效成功內容。

## 7. 目前位置與首程 ETA 生命週期

- [x] 7.1 使用既有定位權限基礎接入 Google 原生藍點：已有權限時只在詳情頁前台更新，首次進頁不自動請求，只有點擊目前位置控件時才發起權限流程。
- [x] 7.2 分別處理一般拒絕、永久拒絕與系統定位關閉的三語說明／設定入口；不得申請背景定位、保存軌跡、把設備位置改寫為查詢起點或持續跟隨相機。
- [x] 7.3 在摘要加入緊湊首程 ETA 並保留首段卡片的即時／Citybus 預計時間分離，後續乘車段不得推算即時 ETA。
- [x] 7.4 使用既有 `FirstLegEtaQuery` 與匹配規則實作前台立即刷新及每 60 秒刷新；進入後台／退出時停止，返回前台且成功值已超過 60 秒時立即刷新，舊 generation／舊語言結果不得覆寫目前頁面。
- [x] 7.5 為 ETA 排程、前後台停止／恢復、NoArrivals／Unavailable 區分、摘要與首段同步及不觸發整張地圖重建補齊單元或 instrumentation 測試。

## 8. 三語、主題與無障礙

- [x] 8.1 為新增標題、檔位、圖例、控件、定位權限、地圖／幾何／ETA 錯誤、重試及 content description 提供香港繁體、獨立審校簡體與自然英文資源，不在 Kotlin 或 XML 硬編碼 App 可見文案。
- [x] 8.2 明確保留第三方站名、方向與 Google 底圖標籤，不承諾 Google 標籤跟隨 App locale；App 自有 marker、圖例、錯誤與時間線仍跟隨 `LanguageSnapshot`／AppCompat 實際語言。
- [x] 8.3 為摘要、把手、marker、地圖控件、時間線選取與失敗狀態補齊 TalkBack 語義；裝飾線條／圓點不得重複朗讀，地圖不得成為站點與轉乘的唯一資訊來源。
- [x] 8.4 驗證約 360dp 與 font scale 1.0／1.3／2.0 下摘要自適應、48dp 觸控目標、長站名換行、三個 detent、控件避讓及 Google attribution；不得以縮字或核心裁切通過。

## 9. 整合測試、真實服務與交付驗證

- [x] 9.1 更新 `RouteDetailActivityContractTest`、`RouteDetailActivityTest` 與視覺矩陣 fixture／啟動工具，覆蓋新啟動參數、即時摘要、舊 bundle 兼容及既有純文字詳情回歸。
- [x] 9.2 新增 instrumentation 測試覆蓋三個 detent、摘要上滑直達全屏、把手半屏、返回直接退出、返回入口遷移、nested scroll、marker／時間線聯動、重建恢復及底圖失敗自動全屏。
- [ ] 9.3 在本任務自行啟動且不佔用他人工作中的模擬器／實機上，以單段與多段真實 Citybus 結果驗證 Google 底圖、所有站點、道路幾何、示意步行、位置權限、60 秒 ETA、局部重試及生命週期；驗證後關閉本任務啟動的模擬器。
- [ ] 9.4 依 `docs/localization-validation-matrix.md` 完成繁體／簡體／英文 × 淺色／深色、360dp、font scale 1.0／1.3／2.0 與 TalkBack 人工檢查，保存必要截圖或測試記錄。
- [x] 9.5 更新 README／相關技術文檔，記錄 `getlinep2p.php` 最小請求、fixture、Maps key 本機配置、Google 標籤語言例外、步行線僅為示意、無背景定位／車輛追蹤，以及 Play Data Safety／隱私披露的發佈前檢查。
- [x] 9.6 執行針對性 JVM 與 instrumentation 測試後運行 `./gradlew build`，檢查 `git status --short` 與 staged 範圍，確認未提交 secret／構建產物並依專案規則建立單一清晰的實作提交。
