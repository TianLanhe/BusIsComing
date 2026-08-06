## Why

Citybus 提供的步行距離只適合作為概略資料，路線卡片與詳情頁目前亦以站點直線虛線表示步行段，無法反映香港實際行人網絡、室內連接及轉乘走法。現在需要接入地政總署 3D Pedestrian Route Search API，在不改寫 Citybus 總耗時、巴士分段時間或 ETA 的前提下，提供較可信且可漸進載入的步行距離、分段時間與地圖軌跡。

## What Changes

- 新增地政總署步行路線資料源，固定使用 `travelMode=3` Recommended Path、WGS84 起終點及米制結果，解析距離、時間與全部獨立 `geometry.paths`。
- 以 Citybus `showstops2.php` 作站點坐標主來源，Citybus 詳情坐標只在 `routeVariant + sequence + stopId` 完全匹配時後備；兩來源偏差超過 30 米或 CSDI 軌跡端點偏差超過 30 米時拒絕該分段。
- 建立 App 進程級、全局最多 5 個請求的成功快取與 single-flight 協調器；相同有向端點請求由卡片與詳情共用，支援優先級、訂閱取消、8 秒超時及一次瞬時錯誤重試。
- 路線卡片先以短文案顯示步行距離查詢中，完整 CSDI 分段成功後改用向上取整的總距離；任一必要分段最終失敗時回退完整 Citybus 距離。
- 按步行距離排序時，已有數值的卡片依方向排序，查詢中卡片固定在數值之後並保持穩定相對順序；漸進完成只重排未置頂區域。
- 路線詳情摘要只在全部必要分段成功時顯示 CSDI 總距離，否則回退 Citybus 總距離；各步行段獨立展示 CSDI 距離及向上取整的約略分鐘，失敗段只保留 Citybus 分段距離。
- Google 地圖移除端點直線步行示意；成功分段按 CSDI 各子路徑分別繪製中性虛線，局部失敗不補畫假路徑，並在實際展示地政總署資料時顯示精簡署名及完整可開啟說明。
- 保持 Citybus 總耗時、預計到達、各巴士段時間與 ETA 不變；第一版不提供 travel mode 選擇、轉向文字、開始導航、即時跟隨或重新規劃。
- 實作及驗證完成後，在 `docs/technical-debt.md` 記錄 Citybus 總耗時／巴士時間與地政總署固定步速時間尚未統一的技術債及關閉條件。

## Capabilities

### New Capabilities

- `landsd-pedestrian-routing`: 定義地政總署步行路線請求、端點規劃、回應驗證、分段模型、進程快取、single-flight、並發、取消、重試、診斷及生命週期契約。

### Modified Capabilities

- `bus-route-walking-distance-table`: 路線卡片改為漸進查詢地政總署總步行距離，並在失敗時整體回退 Citybus 距離。
- `bus-route-results-sorting`: 步行距離查詢中與已取得數值的結果採用穩定、置頂感知的漸進排序規則。
- `route-detail-bottom-sheet`: 詳情摘要與各步行段改用地政總署距離／時間狀態，同時保留 Citybus 總耗時與分段巴士時間契約。
- `route-detail-google-map`: 步行連接由直線示意改為地政總署實際多子路徑、法律署名及不搶奪相機的增量渲染。

## Impact

- 受影響代碼集中於路線查詢 progressive callback／排序、Citybus P2P 端點與詳情共享、步行資料 model／repository／process runtime、`RouteDetailPageState` reducer、時間線 formatter／adapter、`RouteMapPresentationBuilder`、Google Map renderer 及三語資源。
- 新增無憑證 HTTPS 外部來源 `https://mapapi.hkmapservice.gov.hk/PedRoute/NAServer/route/solve`；不新增背景權限、磁碟快取、SQLite migration 或第三方 SDK，亦不得以 fixture 取代生產 HTTP。
- 成功 CSDI 分段與路線組合只在記憶體保存 24 小時；失敗不快取。精確坐標、完整 URL、請求 JSON、站點 ID、使用者地點與軌跡不得寫入日誌或線上分析。
- 需覆蓋三語、TalkBack、窄屏／大字體、亂序 callback、配置重建、部分失敗、排序移動、地圖子路徑與署名安全區；真實 Citybus／CSDI 只讀抽查受外部服務可用性限制，不能成為一般自動測試依賴。
- 本 change 依賴目前已實作的路線詳情可靠結構、主線程 reducer、香港首幀與相機所有權；不得回退 `fix-route-detail-progressive-loading` 已建立的內容單調及局部降級行為。
