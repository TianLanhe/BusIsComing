# 路線詳情站數、快取與並發漸進載入設計

日期：2026-08-06
狀態：已確認

## 背景

路線詳情頁目前有兩組彼此相關的體驗問題：

1. 摘要偶爾顯示「共 0 站」，離開後重新進入仍然是 0；
2. Google 地圖首次開啟會先顯示預設 `(0, 0)` 附近的非洲，再等詳情與幾何大致完成後一次跳到路線；其他內容亦傾向成批出現，缺少可感知的漸進載入。

本次排查確認，「重新進入仍是 0」不是舊 UI 狀態跨頁殘留。`RouteDetailActivity` 每次由 `RouteDetailRuntime.repositoryFactory()` 建立新的 `CitybusRouteDetailRepository`，而現有 `RouteDetailCache`、`RouteStructureCache` 與 `WalkingDistanceCache` 都是 repository 實例欄位，所以一般返回後重新進入並不會命中上一個 Activity 的詳情快取。

0 站會穩定重現，是因為兩條路徑都只計算中途站：

- 啟動摘要使用 `RouteDetailLaunchArgs.estimatedViaStopCount`，公式為每段 `alightingSeq - boardingSeq - 1`；
- 詳情成功後使用 `RouteDetail.totalViaStopCount`，公式為每段 `viaStops.size`。

UI 文案卻是「共 N 站／N stops in total」，使用者會自然理解成實際乘坐的站數。若一段路線在相鄰兩站上下車，兩個公式都得到 0；多段相鄰換乘亦會繼續得到 0，因此重新進入不會自行恢復。

排查亦發現兩項資料品質風險：

- `CitybusRouteDetailParser` 只要求找到每段上車站和下車站，沒有驗證兩者之間的站序是否完整。中間某列缺失時仍可產生較短的 `viaStops`；
- `RouteDetailCompleteness.COMPLETE` 表達的是步行距離／session 完整性，不代表站序完整。目前 `RouteStructureCache.put()` 只檢查乘車段非空，若直接把快取提升為真正的進程級 24 小時快取，殘缺站序便可能污染後續頁面。

地圖問題則源於 `restoreCamera()` 在沒有已保存相機時直接返回，MapView 因而先使用 Google Maps 的世界預設鏡頭。`renderMap()` 又以所有幾何請求結束作為首次全覽條件，造成延遲後的大幅跳轉。

## 與既有設計的關係

本設計是對下列既有設計的針對性修正與補充：

- `2026-08-02-route-detail-page-design.md`；
- `2026-08-03-route-detail-google-map-design.md`。

若站數、首屏相機、快取資料域或漸進載入順序有衝突，以本設計為準。其餘已確認的 bottom sheet、時間線、地圖角色、ETA、定位、三語、明暗模式與無障礙契約保持不變。

## 目標

- 摘要的「共 N 站」改為實際乘坐站數：每段包含所有中途站和下車站，不計上車站。
- 尚未取得可靠站序時顯示本地化載入狀態，不再使用可產生誤導的估算值或預設 0。
- 建立真正的進程記憶體 24 小時可靠快取，讓重新進入可立即復用已驗證站序，同時避免快取易變時間與 UI 狀態。
- MapView 首幀位於香港；已保存相機仍優先恢復。
- Map、Citybus 詳情、各段幾何與首程 ETA 真正並發，誰先可靠完成誰先展示，不設人工串行揭示。
- 所有並發結果經單一 reducer 歸併，處理 generation、過期 callback、局部重試、部分失敗、生命週期及成功狀態回退。
- 漸進更新只能增加或更新可靠內容，不能讓已成功內容因較晚失敗、候選資料或重試而消失。

## 非目標

- 不建立磁碟快取或跨進程持久化，不做資料遷移。
- 不重做 persistent bottom sheet 的視覺與三檔互動。
- 不改寫 ETA 計算、刷新來源或通知監控。
- 不替換 Citybus API，不改寫路線幾何演算法。
- 不使用 Google Routes API，也不新增步行導航。
- 不進行與詳情頁無關的 repository 或 UI 全倉重構。
- 本設計確認階段不建立 OpenSpec change，也不開始實作。

## 站數產品語義

### 正式公式

可靠詳情可用後，摘要乘坐站數為：

```text
rideStopCount = Σ（leg.viaStops.size + 1）
```

其中每段的 `+ 1` 是該段下車站；上車站不計。例子：

| 路線結構 | 顯示站數 |
|---|---:|
| 一段相鄰上下車，沒有中途站 | 1 |
| 兩段均為相鄰上下車 | 2 |
| 一段有 4 個中途站 | 5 |

換乘站按乘車段語義計算：上一段的下車站計一次，下一段的上車站不計；不會因同站換乘額外增加一站。

### 載入與失敗文案

`RouteDetailUiItem.Summary` 不再強制持有一個看似可靠的整數，而是持有結構化站數狀態：

```text
RideStopCountState
├── Loading
├── Available(count)
└── Unavailable
```

- 首次開啟且可靠結構快取未命中：顯示本地化「站數載入中」；
- 可靠快取或已驗證網絡詳情可用：顯示「共 N 站」；
- 詳情最終失敗且沒有可靠快取：顯示本地化「站數暫時無法載入」，不得回退為 0。

香港繁體、簡體與英文需提供獨立資源；TalkBack 讀出相同狀態語義。原 `estimatedViaStopCount` 可保留為資料校驗的期望數量，但不得再作為首屏 UI 數值。

## 站序完整性門禁

新增純 Kotlin 結構驗證器，輸入 `P2pRoutePlan` 與 parser 產出的乘車段，輸出明確的成功或失敗原因。每一段必須同時符合：

1. 乘車段數量與順序和 plan 一致；
2. route variant、公開路線號、上車 seq 與下車 seq 和 plan 一致；
3. 上車、所有中途站、下車的 seq 唯一且嚴格遞增；
4. seq 完整覆蓋 `boardingSeq..alightingSeq`，元素數量等於 `alightingSeq - boardingSeq + 1`；
5. 上下車角色唯一，stop id 非空，坐標有限且在合法範圍。

驗證狀態不得復用 `RouteDetailCompleteness`，避免把「步行完整」誤當成「站序完整」。建議使用獨立的 `RouteStructureValidationResult`，並讓 repository 只有在結果為 Valid 時才能：

- 向頁面發布結構 Success；
- 計算及展示乘坐站數；
- 寫入結構快取；
- 使用站點坐標驗證幾何 candidate。

若首次結果缺少中間 seq，視為上游回應／session 可能失效：有 recovery context 時沿用現有查詢恢復流程重新取得有效 session 與 query；沒有 recovery context 時只允許一次直接重試。第二次仍不完整則回傳帶原因的局部詳情錯誤，不能無限循環、展示殘缺時間線或寫入快取。

## 快取與 single-flight

### 進程級擁有者

現有快取從 `CitybusRouteDetailRepository` 的預設實例欄位提升至 `RouteDetailRuntime` 管理的進程級資料擁有者，repository 透過注入使用。測試仍可注入短 TTL、可控 clock 與獨立 cache。

進程快取只存記憶體，TTL 為 24 小時；殺死 App 進程自然清空，不新增 SQLite、SharedPreferences 或檔案格式。

### 分離資料域

快取分為兩個可靠資料域：

1. **RouteStructureCache**
   - key：`planFingerprint + actualLanguage`；
   - value：已驗證的站序、站名、stop id、坐標、route variant、方向、端點名稱與換乘結構；
   - 寫入前移除 `plannedBoardingTime`、`plannedAlightingTime`、`plannedDepartureTime`、`plannedArrivalTime` 等易變時間。
2. **WalkingDistanceCache**
   - key：`endpointContext + planFingerprint`；
   - value：所有必要起點、換乘及終點步行段均齊全時的距離與換乘類型；
   - 任一必要步行距離缺失時不寫入。

ETA、Citybus 預計時間、session id／session reference、網絡錯誤、Loading、UI 展示文案與派生的站數整數永不進入 24 小時快取。分段票價由本次新鮮回應補入；首屏仍可使用路線結果已有的總票價。

可靠結構快取命中時，頁面可立即顯示站點時間線骨架和正確站數；網絡請求仍並發取得本次預計時間、分段票價及其他動態補充。新鮮回應失敗時，已顯示的可靠結構不得清空。

原 `RouteDetailCache` 的整體 `ParsedRouteDetail` 快取與分域契約重疊，實作時應收斂至上述 domain cache，避免一條路徑保存易變時間、另一條路徑只保存結構。

### 原子、品質單調寫入

每個 cache 提供「先驗證、後原子寫入」入口：

- 未驗證、部分或失敗結果不能建立 entry；
- 較差結果不能覆蓋已驗證完整 entry；
- 過期判斷與讀寫在同一同步邊界內完成；
- 頁面取消或 callback 過期不影響已完成的共享可靠寫入，但不得把 UI 狀態寫入 cache。

### same-key single-flight

新增進程級 detail request coordinator。相同 request identity 的同時請求共用一個上游工作，完成後向仍有效的 consumer 分發結果；不同 identity 絕不合併。

request identity 至少包含：原始 query 的 `rawInfo`、`generalInfo`、`listId`、實際語言、plan fingerprint、recovery context 與 opaque session reference。identity 只在記憶體中比較或雜湊，不記錄 Cookie、PHP session 值或完整敏感參數。

consumer 退出只移除自己；仍有 consumer 時不取消共享工作。最後一個 consumer 離開時可取消尚未開始或可安全中止的工作。成功、結構錯誤與網絡錯誤都結束該次 flight；錯誤不進快取，後續手動重試建立新 generation 與新 flight。

## 並發取得與單一狀態歸併

### 啟動並發

進入頁面後立即並發啟動：

- Google Map 初始化；
- 可靠結構／步行快取讀取及 Citybus 詳情網絡請求；
- 各乘車段道路幾何，維持最多 3 個上游請求；
- 首程 ETA。

它們沒有人工串行依賴。詳情單個 HTTP 回應在 parser 與完整性驗證完成後原子發布，不把同一回應拆成逐字段延時動畫。幾何可逐段發布；早於詳情抵達且尚未驗證端點的 candidate 只能保存在 coordinator 內部。

### 頁面狀態

把目前散落在 `RouteDetailActivity` 的 `detail`、`detailLoading`、`detailFailed`、`geometries`、`failedGeometryKeys`、`geometryPendingCount`、`waitTimeState` 與相機旗標收斂為不可變 `RouteDetailPageState`。狀態至少包含：

```text
RouteDetailPageState
├── pageGeneration
├── launchSummary
├── rideStopCountState
├── routeStructureState
├── dynamicDetailState
├── mapState
│   ├── readiness
│   ├── cameraSnapshot
│   └── cameraOwner
├── geometryStates<GeometryKey, GeometryState>
├── etaState
├── sheet／selection／expandedLegs
└── lifecycleState
```

背景工作只產生 event，不直接改 View 或 Activity 欄位。所有 event post 到主線程，由唯一 `RouteDetailPageReducer.reduce(oldState, event)` 同步產生新快照，再由 renderer／adapter 對舊、新狀態做增量更新。

每個異步 event 必須攜帶：

- `pageGeneration`；
- 對應資料域的 `domainGeneration`；
- 有細分項時的 stable key，例如 geometry key；
- 結構化結果或結構化錯誤。

### reducer 不變量

1. **身份一致**：頁面、資料域 generation 與 stable key 全部匹配才可歸併；
2. **品質單調**：已驗證 Success 不被舊 Loading、舊 Error、candidate 或較差 cache 覆蓋；
3. **可靠才發布**：殘缺站序、未核對幾何不會先顯示再撤回；
4. **局部重試**：重試只提升失敗資料域或 geometry key 的 generation，其他 slice 原樣保留；
5. **生命週期安全**：退出、配置重建、實際語言改變或新頁面 generation 後，舊 callback 直接丟棄；
6. **刷新保留舊值**：ETA 或動態補充刷新時用 `Refreshing(previous)` 表示，不先清除最近成功內容。

配置重建時恢復可序列化的 UI／相機狀態並建立新 Activity consumer；進程級可靠 cache 和 in-flight coordinator 不持有 Activity、View、GoogleMap、Marker 或 Adapter。

## 香港首幀與相機所有權

### 首幀規則

MapView 建立時便在 XML camera attributes 或 `GoogleMapOptions` 設定集中管理的 `HONG_KONG_DEFAULT_CAMERA`，而不是等 `onMapReady` 後才修正世界預設鏡頭。建議城市級預設值為香港中心約 `22.3193, 114.1694`、zoom 約 `10.5`；最終常數需以目標裝置可見範圍驗收後固定。

相機優先順序：

1. configuration／process recreation 可恢復的有效 `cameraSnapshot`；
2. 首次開啟的香港預設相機；
3. 完整路線可用後的一次平滑全覽。

查詢起點與終點來自 launch args，可在 Map ready 後立即加入，不等待 Citybus 詳情或幾何。

### 一次自動全覽

完整路線 bounds 的就緒條件是：可靠站序已可用，且所有預期 geometry key 均到達終態（成功或局部失敗）。bounds 使用查詢起終點、所有可靠站點及成功幾何；單段幾何失敗不能讓頁面永遠等待。

頁面只有在 `cameraOwner == PAGE` 且尚未執行自動全覽時，平滑全覽一次。不得因每段幾何到達、bottom sheet 高度變化、ETA 刷新或 adapter 更新而反覆 fit bounds。

透過 `GoogleMap.OnCameraMoveStartedListener` 判斷 `REASON_GESTURE`。使用者一旦拖動、縮放或旋轉，狀態改為 `cameraOwner == USER`，本次頁面不再自動改動鏡頭。程式自身的相機動畫不得誤判成使用者操作。「全覽路線」與「目前位置」按鈕仍可由使用者主動觸發，並更新保存的 camera snapshot。

bottom sheet 只更新 map padding；配置重建恢復相機 snapshot、是否已自動全覽及 camera owner。

## 漸進展示規則

頁面不是按固定秒數或固定順序揭示，而是按可靠資料域的真實完成時間更新：

1. **立即**：route chain、總耗時、路線結果總票價、預計到達、步行回退值與已有 ETA；站數為 Loading；
2. **Map ready**：香港底圖與查詢起終點；
3. **可靠結構 cache／網絡先到**：正確站數、站點 marker 與可用時間線骨架；
4. **新鮮動態詳情到達**：分段票價、預計時間、步行補充原位更新；
5. **各段 geometry 通過端點驗證**：按 stable key 增量加入 polyline，不重建整張地圖；
6. **ETA 到達或刷新**：只更新 ETA slice。

同一 reducer tick 可以同時增加多項已在該時刻可靠的內容；「漸進」不要求故意把一個完整回應拆成串行動畫。Adapter、marker 與 polyline 使用 stable id 做 diff，未變資料域不得因另一資料域更新而重新建立、折疊、清空或丟失選取狀態。

## 失敗與恢復矩陣

| 失敗資料域 | 頁面表現 | 必須保留 | 重試 |
|---|---|---|---|
| Google Map | 地圖區顯示可重試錯誤或沿用既有降級 | 摘要、站數、時間線、ETA | 只重建 Map 域 |
| 詳情網絡，且有可靠結構 cache | 保留站數與站點骨架，動態補充顯示暫不可用 | Map、所有可靠結構、geometry、ETA | 新 detail generation |
| 詳情網絡／結構殘缺，且無 cache | 站數顯示暫不可用，時間線顯示重試 | 啟動摘要、查詢端點、Map、ETA | 一次受控恢復後允許手動重試 |
| 單段 geometry | 其他可靠線段繼續顯示，不以直線冒充道路 | 地圖鏡頭、marker、其他線段與所有非 geometry 內容 | 只提升該 geometry key generation |
| ETA | ETA 區域顯示暫不可用；刷新失敗可保留最近成功值 | 計劃時間、靜態詳情、Map | 獨立定時或手動刷新 |

局部 Error 不能把整個頁面切回單一錯誤畫面；局部重試亦不能重新請求或清除仍有效的其他資料域。

## 可觀測性

為排查上游與競態問題，debug／結構化日誌記錄：

- detail／geometry 資料域、generation、stable key 的安全雜湊；
- cache hit、miss、expired、rejected write 與 single-flight join／complete；
- 結構校驗失敗原因，例如 missing seq、duplicate seq、endpoint mismatch；
- callback 因 stale page／domain generation 被拒絕；
- camera owner 由 PAGE 轉為 USER 的原因。

不得記錄 Cookie、PHPSESSID、完整 session reference、完整 URL query、使用者自訂地點名稱或精確查詢端點坐標。

## 驗證

### 純邏輯與 fixture 測試

- 一段相鄰上下車顯示 1；兩段相鄰上下車顯示 2；多個中途站時加上下車站且不計上車站。
- 既有 same-stop fixture 回歸，確認兩段相鄰乘車不再顯示 0。
- 缺失中間 seq、重複 seq、逆序、錯誤端點、非法坐標均被結構驗證拒絕。
- 結構殘缺不發布、不寫入 cache，受控恢復最多一次。
- 24 小時結構／步行 cache 的命中、過期、語言／端點隔離與進程級重新進入。
- 部分或較差結果不能覆蓋完整 cache；易變時間與 UI 狀態不在 cache value 中。
- 兩個相同 request identity 的 consumer 只產生一次上游請求；不同 identity 不合併。

### reducer 與競態測試

- Map、detail cache、detail network、ETA 與多段 geometry 以不同完成排列到達，最終可靠 state 一致。
- retry 後舊 Loading／Error 晚到，不覆蓋新 Success。
- 語言改變、退出或配置重建後的舊 callback 被拒絕。
- geometry candidate 在詳情端點晚到且不匹配時從未進入可渲染 state。
- 單段 geometry 失敗不清除其他線段；單域重試不改變其他 slice。
- ETA 刷新 Loading／Error 保留最近成功值。
- stable id diff 保留已展開乘車段、選中站點及列表位置。

### 相機與 Android 驗收

- 相機優先序純邏輯測試：保存狀態優先，其次香港預設，最後一次自動全覽。
- MapView 首次建立即具有香港 camera options，不依賴 detail callback 才修正。
- 使用者 gesture 後晚到的完整 bounds 不再觸發自動 fit；程式相機動畫不誤鎖。
- geometry 局部失敗仍能結束首次全覽等待。
- bottom sheet padding、ETA 或 adapter 更新不重置相機。
- 執行完整 `./gradlew build`。
- 使用本任務自行啟動且符合 Google Maps 條件的模擬器，驗證香港首幀、慢網漸進加入、局部失敗／重試、使用者接管鏡頭、返回後 cache 命中。
- 人工覆蓋香港繁體、簡體、英文、淺色／深色、長文案、TalkBack 與目標 font scale；任務結束後關閉本任務啟動的模擬器。

## 實作切分建議

1. 先以測試鎖定新站數公式與結構完整性驗證，修正 model／formatter 的站數狀態；
2. 再把 domain cache 提升為進程擁有，拆除整體 Parsed detail 的易變快取並加入 single-flight；
3. 引入純 reducer 與 event generation，逐步把 Activity 散落欄位遷移進不可變 PageState；
4. 修改 Map 建立時的香港 camera options、camera owner 與一次全覽條件；
5. 完成各資料域的增量 renderer、局部錯誤／重試與三語／無障礙資源；
6. 依上述分層測試、完整 build 與任務自有模擬器完成驗收。

實作前應以 OpenSpec proposal／design／delta specs／tasks 固化對外需求及任務依賴；本文件作為已確認設計輸入，不直接取代生效 spec。
