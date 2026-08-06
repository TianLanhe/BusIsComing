## Why

路線詳情摘要目前把「途經站數」誤作「乘坐站數」，相鄰上下車時會穩定顯示 0，且現有實例級快取無法在重新進入時提供可靠結構；同時地圖首幀落在 `(0, 0)` 附近並等待多個資料源後集中更新，造成非洲底圖閃現與內容突現。需要以可驗證的站序、真正的進程級快取及單一並發狀態歸併，讓頁面先展示已知可靠資訊，再單調補全且不因局部失敗丟失內容。

## What Changes

- 將摘要「共 N 站」定義為每段中途站加下車站之和，不計上車站；可靠站序未取得時顯示載入或暫不可用，而不是估算或顯示 0。
- 驗證 Citybus 詳情的乘車段、端點與完整連續站序；殘缺站序不展示、不寫快取，並只允許一次受控恢復。
- 將站點結構與完整步行距離提升為 24 小時進程記憶體快取，分離預計時間、分段票價、ETA、session 與 UI 狀態；相同詳情請求使用進程級 single-flight，部分結果不得覆蓋完整結果。
- 讓 Map、Citybus 詳情、各段幾何與首程 ETA 並發載入，經帶 page／domain generation 與 stable key 的單一 reducer 歸併；可靠成功內容不得被過期 callback、較差結果或其他資料域失敗清空。
- 在 MapView 建立時設定香港城市級預設相機，保留已保存鏡頭優先權；完整路線只自動平滑全覽一次，使用者操作後頁面不再自動搶回鏡頭。
- 幾何 candidate 在可靠詳情端點完成校驗前只留在內部，不能先繪製後撤回；單段幾何、詳情、ETA 或地圖失敗只提供對應局部重試。
- 增加站數、站序品質門禁、快取／single-flight、事件排列、過期 callback、candidate 晚校驗、香港首幀、相機所有權及三語狀態的回歸驗證。

## Capabilities

### New Capabilities

- `route-detail-progressive-loading`: 定義路線詳情各資料域並發啟動、generation 事件歸併、可靠內容單調發布、局部失敗與局部重試的頁面契約。

### Modified Capabilities

- `route-detail-bottom-sheet`: 修正摘要乘坐站數語義，新增站序完整性門禁、站數載入／失敗狀態，以及可跨重新進入復用的進程級結構／步行快取與詳情 single-flight 契約。
- `route-detail-google-map`: 新增香港首幀、已保存相機優先、一次自動全覽與使用者鏡頭所有權契約，並禁止其他資料域更新重置鏡頭。
- `citybus-route-geometry`: 明確 candidate 必須在目前 consumer 的可靠端點校驗通過後才可發布，且晚到的失敗／過期事件不得撤回其他成功幾何。

## Impact

- 主要影響 `RouteDetailActivity`、`RouteDetailLaunchArgs`、`RouteDetailUiFormatter`、詳情 UI model／adapter、`GoogleRouteMapRenderer`、相機展示策略與三語資源。
- 資料層影響 `CitybusRouteDetailParser`、`CitybusRouteDetailRepository`、`RouteDetailCache`／domain caches、`RouteDetailRuntime`、詳情 request coordinator、`RouteGeometryLoadCoordinator` 及相關 model／formatter。
- 更新 `route-detail-bottom-sheet`、`route-detail-google-map`、`citybus-route-geometry` 主能力的可觀察契約，並新增 `route-detail-progressive-loading`；不修改 Citybus 或 Google 外部接口參數，不新增第三方依賴。
- 快取只在 App 進程記憶體內保存，不新增 SQLite、偏好或檔案格式，沒有資料遷移；Cookie、PHPSESSID、完整 session reference 與完整查詢參數不得進入日誌或持久化資料。
- 驗證包含 deterministic fixture／unit tests、reducer 競態排列、完整 `./gradlew build`，以及只使用本任務自行啟動的 Google Maps 模擬器完成香港首幀、慢網漸進載入、局部重試、三語／明暗／無障礙人工驗收。
