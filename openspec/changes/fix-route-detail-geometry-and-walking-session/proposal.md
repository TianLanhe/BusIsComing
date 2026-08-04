## Why

目前路線詳情在冷快取首次開啟時可能只展示站點而永久缺少巴士道路線，必須退出重入才恢復；同時 `ppsearch_p3.php` 建立的 Citybus PHP session 沒有傳遞至 `getp2pstopinroute.php`，導致起點、轉乘及終點的分段步行距離全部缺失。這兩項問題會讓地圖與文字詳情在正常使用路徑中呈現不完整資訊，現有測試亦未覆蓋相關時序與 session 契約。

## What Changes

- 讓每個乘車段的 Citybus 道路幾何在一次詳情頁生命週期內只發起一次冷載入；詳情稍後返回時補做端點校驗，而不取消並重發同一請求。
- 對可恢復的幾何網路／空回應失敗在前台自動重試一次，持續失敗時保留所有站點、提供局部手動重試，且不以站點直線冒充巴士道路。
- 在並行 `m1=T/F/W` 點到點查詢中分別擷取 Citybus 回傳的 `PHPSESSID`，以不透明、短生命週期的 session reference 把候選路線與產生其 `lid` 的搜尋會話關聯；不得使用靜態、瀏覽器、廣告或追蹤 Cookie。
- 詳情請求只向 `getp2pstopinroute.php` 傳送匹配會話的 `PHPSESSID`，從 `showtimetable1(...)` 及三語 HTML 解析起點、每次步行轉乘與終點距離；同站轉乘維持「同站轉乘」且不顯示 `0 米`。
- session 缺失或過期時自動重做一次原點到點查詢並匹配原候選方案；仍無法恢復時展示可用站點與已知分段，摘要回退至 `ppsearch_p3.php` 的總步行距離並保留不完整說明。
- 將 session 關聯與業務快取分離：`PHPSESSID`／`lid` 不作長期快取鍵；站點結構、步行距離、計劃時間與即時 ETA 按各自穩定輸入及時效管理，且不得快取 session 缺失造成的空距離。
- 移除路線詳情地圖上的「巴士路線／步行連接（示意）」浮動圖例；實線、虛線、marker、時間線及無障礙文字的既有語義保持不變。
- 本變更不新增 Google Routes 步行導航、不改動路線結果排序或卡片步行距離、不持久化 Citybus session、不改動 SQLite／已保存行程／通知監控，也不進行無關架構重寫。

## Capabilities

### New Capabilities

- 無。

### Modified Capabilities

- `citybus-route-query-api`: 修改「不攜帶 Cookie」契約，改為禁止靜態／瀏覽器 Cookie，同時允許擷取每個 `m1` 回應的短期 `PHPSESSID`，並只在匹配的詳情請求中回傳該必要 session Cookie。
- `route-detail-bottom-sheet`: 補充分段步行距離、同站轉乘、部分資料降級、session 恢復與穩定業務快取契約，取代只按 `rawInfo + lang` 快取完整詳情的行為。
- `citybus-route-geometry`: 在已完成的地圖能力上加入冷載入 single-flight、延後端點校驗、可恢復失敗自動重試及不要求退出重入的契約。
- `route-detail-google-map`: 移除常駐地圖圖例，並維持線型、marker、時間線及無障礙文字對路線與示意步行的完整表達。

## Impact

- 資料與模型：`CitybusBusRouteRepository`、P2P 路線 parser／model、`CitybusRouteDetailRepository`／parser／cache，以及短生命週期的 session registry；`m1=T/F/W` 各自保持搜尋與詳情關聯。
- 幾何與生命週期：`RouteDetailActivity` 的幾何協調狀態、`CitybusRouteGeometryRepository` 的 in-flight／取消／校驗／重試邊界，以及 process recreation、真正退出和語言 generation 作廢行為。
- UI：路線詳情 formatter／adapter／layout、三語部分資料與重試文案、移除地圖圖例及其資源；bottom sheet 三檔、地圖控制、線條樣式及返回行為不變。
- 外部資料：生產路徑仍使用真實 `ppsearch_p3.php`、`getp2pstopinroute.php` 與 `getlinep2p.php`；保留不含敏感 session 值的等價 cURL、單段／多段三語 fixture、空 session 對照及短暫幾何失敗證據。
- 相容性與私隱：不把 `PHPSESSID` 寫入 SQLite、Intent 長期資料、日誌、fixture、截圖或提交；session 不作語義快取鍵，失效後只允許一次受控恢復查詢。
- 驗證：新增慢速詳情／幾何順序、冷暖快取、自動及局部重試、三個 `m1` session 隔離、session 過期恢復、單段／多段精確分段距離、三語解析與無圖例 UI 回歸；最後執行 `./gradlew build`，並以任務專用模擬器驗證真實 Google 底圖和 Citybus 結果。
