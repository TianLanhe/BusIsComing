## Context

現有 `RouteDetailActivity` 在 `onCreate` 同時載入 Citybus 詳情與每段 `getlinep2p.php` 幾何；詳情成功後又會取消目前幾何 handle，帶站點端點重新發起同一批請求。冷快取下，第一次 HTTP 工作可能被 `shutdownNow()` 中斷，而第二次載入又共享同一個 in-flight future；失敗 callback 只提供短暫手動重試，因此頁面可能長期只剩站點，退出重入後才因新請求或進程快取恢復。這不是 Google Map 缺少 invalidate：已接納的幾何 callback 會重建 presentation，renderer 亦會差量加入 polyline。

Citybus 點到點詳情另有未建模的 session 契約。`ppsearch_p3.php` 在每個 `m1` 回應中建立 `PHPSESSID`，候選的 `lid` 只在該 PHP session 內有意義。現有查詢 HTTP 邊界只回傳 body，三個模式各自收到的 session 均被丟棄；其後 `getp2pstopinroute.php` 使用新連線且不帶 Cookie。真實 A/B 證據顯示，無原 session 時仍可依 `info` 重建站點，但 `showtimetable1(...)` 與起點、轉乘、終點步行距離為空；只帶同一次搜尋的 `PHPSESSID` 即可取得完整距離。

目前完整詳情按 `rawInfo + lang` 快取一天，將站點結構、步行距離及計劃時間視為同一時效，也無法區分「上游本來缺失」與「session 丟失造成空值」。本設計必須在不降低穩定資料快取價值、不持久化 session、不中斷現有結果聚合與地圖降級的前提下修正這些契約，並移除已確認不需要的地圖圖例。

## Goals / Non-Goals

**Goals:**

- 冷快取首次開啟即可在同一頁完成道路幾何展示，不依賴退出重入。
- 每段幾何 single-flight，詳情到達只補校驗，不取消相同工作；可恢復失敗自動重試一次。
- 保持 `m1=T/F/W` 各自的 Citybus 搜尋 session，讓 `lid` 與正確 `PHPSESSID` 一起用於詳情取數。
- 解析並展示單段與多段方案的起點、每次步行轉乘及終點距離；同站轉乘保持獨立語義。
- 將短期 session 關聯、穩定業務快取與時間敏感資料分離，維持相同起終點與方案的跨 session 命中。
- 對 session 過期、部分距離、網路失敗、頁面銷毀及語言 generation 提供有界恢復與誠實降級。
- 完整移除地圖浮動圖例而不改變路線線型、marker、時間線或無障礙語義。

**Non-Goals:**

- 不把 Citybus `PHPSESSID` 寫入 SQLite、磁碟快取、日誌、fixture、截圖或長期分析資料。
- 不把 `PHPSESSID`、session reference 或 `lid` 當作穩定業務快取鍵。
- 不接入 Google Routes／Directions 計算沿街步行路徑；地圖步行線仍是示意連接。
- 不改動候選路線排序、去重可見語義、路線卡片總步行距離、ETA 或通知監控。
- 不以站點直線、固定偏移以外的新推算或其他公共 API 偽造缺失 Citybus 資料。

## Decisions

### 1. 以頁面級 geometry coordinator 取代「詳情後取消重載」

詳情頁為每個 `routeVariant + boardingSeq + alightingSeq` 建立單一狀態 `Loading / Candidate / Loaded / Failed`。頁面啟動時每個 key 只提交一次冷載入；詳情稍後到達時，coordinator 對已收到或之後收到的 normalized candidate 補做端點校驗，再更新 presentation。詳情失敗時仍可接受通過結構驗證的 geometry，以保留原有獨立降級。

頁面真正銷毀、語言版本作廢或用戶針對失敗段重試時才取消／建立新 generation。configuration change 若建立新 Activity，舊 callback 由 generation 與 destroyed guard 作廢，新頁可命中進程快取。

被否決的方案：呼叫 `MapView.invalidate()` 或強制重建 renderer，因為 callback 已執行 `renderMap()`；單純延後第二次請求仍保留重複工作和中斷窗口；等待全部詳情與幾何後一次繪製則會失去分段增量展示。

### 2. in-flight future 只負責取得 normalized candidate

`CitybusRouteGeometryRepository` 的共享 future 不捕獲第一個 caller 的端點參數，只負責 HTTP、parser、舊底圖坐標校正及結構驗證。每個 consumer 在取得 candidate 後以自己的可靠站點執行端點校驗；端點不匹配時從成功 cache 移除該 candidate，避免未校驗 owner 留下之後反覆命中的錯誤結果。

成功 cache 仍以 geometry key 保存一天且不按語言分割。單一等待者取消不得中斷仍有等待者的共享工作；page handle 只停止向已離開頁面派送結果。這比把 endpoint 放入 cache key 更符合幾何本身與語言、session、查詢起終點無關的語義。

### 3. 幾何只對可恢復失敗自動重試一次

傳輸錯誤、timeout、HTTP 成功但空內容或有效點不足可在頁面仍前台且 generation 有效時，經短 backoff 自動重試一次。非法 key、明確 malformed 契約、坐標範圍錯誤或端點不匹配不自動循環。第二次仍失敗後保留站點並顯示局部手動重試；手動重試只重建失敗 key，不清空成功段。

被否決的方案：無限重試會放大私有 endpoint 壓力並耗電；完全只靠 Snackbar 手動重試會重現目前「等待不恢復」；用站點直線 fallback 會誤導道路走向。

### 4. 每個 m1 HTTP 回應建立獨立、最小 Citybus session

查詢 HTTP 邊界回傳 body 與經白名單擷取的 `Set-Cookie: PHPSESSID`。`m1=T/F/W` 各自使用獨立 response context，不使用全域 `CookieManager`，避免並行回應最後寫入者覆蓋其他模式。只接受 Citybus 同源回應的 `PHPSESSID`；其他 consent、廣告、追蹤或未知 Cookie 全部丟棄。

成功解析候選後，process-scoped registry 產生隨機不透明 `sessionRef`，保存 `PHPSESSID`、m1、語言、搜尋起終點及到期時間；`P2pRouteDetailQuery` 只攜帶 `sessionRef` 與恢復所需的非敏感查詢描述。聚合去重若選擇某一候選作代表，必須一併保留該候選自己的有效 sessionRef 與 lid，不得把不同模式的兩者拼接。

被否決的方案：全域 Cookie jar 無法維持三個並行 session；把原始 PHPSESSID 放入長期模型、資料庫或日誌增加洩漏風險；使用使用者提供的瀏覽器 Cookie 不可重現且包含無關資料。

### 5. 詳情請求只回傳匹配 session 的 PHPSESSID

`getp2pstopinroute.php` 保留 `info`、`ginfo`、`lid`、`l`，並在 registry 命中時只增加 `Cookie: PHPSESSID=<matching value>`。靜態 `User-Agent`、`Referer`、`Sec-Fetch-*`、`Accept-Language` 及其他瀏覽器 header 仍禁止。session 只供 Citybus 同源詳情請求，不傳給 `getlinep2p.php`、Google、DATA.GOV.HK 或其他 host。

parser 先讀取 `showtimetable1(...)` header 中 `legCount + 1` 個距離，再以繁體、簡體、英文 DOM／文字標籤作兼容 fallback。結果分類為 `Complete`、`Partial` 或 `SessionMissing`；站點可解析但 timetable payload 與所有步行欄位同時為空時判為 `SessionMissing`，而不是可長期快取的完整詳情。

### 6. session 失效只自動恢復一次

registry miss、Citybus 明確返回 session-missing 形態，或所有應有距離因會話失效而空缺時，detail repository 以原起終點、目前語言及原 m1 重新執行一次 `ppsearch_p3.php`，使用新的香港時間，按乘車段 `routeVariant + boardingSeq + alightingSeq` 及路線鏈匹配原候選，取得新的 sessionRef/lid 後重試詳情。恢復查詢不更新來源結果列表、不改變排序、不增加常用行程使用次數。

每個詳情 generation 最多恢復一次，避免失效 session 或上游改版造成循環。無匹配候選或再次缺失時返回 `Partial`：保留站點、方向、票價與已知距離，未知段不推算，摘要使用卡片總步行距離並標記來源不完整。

被否決的方案：直接拿新 session 的相同 lid 會指向錯誤候選；靜默無限重搜會改變網路負擔；立即要求用戶返回重查則使可自動恢復的正常過期變成操作阻塞。

### 7. session 關聯與業務快取分層

不快取原始完整 HTML，也不以 sessionRef、PHPSESSID 或 lid 作語義 cache key：

- `RouteStructureCache`：以 `plan fingerprint + lang` 保存站點、方向與可穩定對齊的乘車段資料，一天進程內有效。
- `WalkingDistanceCache`：以穩定起點、終點及 plan fingerprint 保存純數值的起點／轉乘／終點距離，一天進程內有效，不按 session 分割；地點有 provider id 時優先使用，否則使用送入 P2P 查詢的規範化坐標。
- 計劃上／下車及到達時間：不併入一天 cache，使用本次搜尋／詳情結果或獨立短 TTL。
- DATA.GOV.HK 即時 ETA：沿用既有刷新策略，完全不進入詳情 cache。

只有完成對應完整性校驗的資料域才寫入；session-missing 空距離、失敗結果及未知段不覆蓋既有完整步行 cache。相同起終點、語言與方案可跨新 session 命中；不同起終點即使乘車段相同亦不得串用首尾步行距離。

### 8. UI 逐段展示可靠距離並移除地圖圖例

時間線按起點、每次換乘及終點逐段顯示已知距離；缺失段保留「步行」語義但不顯示推算數字。同站轉乘顯示目前語言的「同站轉乘」，不顯示 `0 米` 或步行虛線。全部必要距離完整時摘要顯示分段之和；否則顯示 `ppsearch_p3.php` 卡片總距離與既有「部分步行距離由路線摘要提供」說明。

從 XML、binding、可見狀態與無障礙樹移除浮動地圖圖例，並清理只由圖例使用的三語資源。巴士實線、示意步行虛線、站點角色、時間線及 TalkBack 描述仍完整存在；Google attribution 與地圖控制位置重新使用空出的安全區域，不新增替代提示。

### 9. 驗證以確定性時序測試與真實 session A/B 為門檻

JVM 測試使用可控 datasource 明確重現「geometry 慢、detail 先回」、「geometry 先回、detail 後校驗」、第一輪暫時失敗後自動成功、部分段永久失敗及 configuration change。repository/parser fixture 保存已脫敏的無 session、正確 session、過期 session、繁體／簡體／英文、單段／多段樣本，斷言每個距離值與完整性分類，而不只斷言存在步行線。

instrumentation 使用生產 repository 驗證冷、暖首次開啟的 BUS line 數、所有 marker、無圖例、摘要／時間線距離、局部重試與生命週期。真實 A/B 記錄 Cookie 有無造成的業務欄位差異，但不得保存 PHPSESSID；任務專用模擬器需在可連接 Google Maps 與 Citybus 的環境執行。

## Risks / Trade-offs

- [Citybus session 契約未公開且可能改變] → 把 Cookie 擷取、session-missing 分類及恢復限制在 provider repository，保存脫敏 A/B fixture 與 live 驗證；失敗時誠實降級而不偽造距離。
- [三個 m1 session 增加記憶體與生命週期複雜度] → registry 只保存白名單 PHPSESSID 與最小恢復描述，使用短 TTL、process scope 及過期清理；不建立磁碟遷移。
- [自動恢復搜尋可能返回不同候選集合] → 只接受乘車段 fingerprint 明確匹配的候選且只嘗試一次；找不到即降級，不替換用戶已選路線。
- [分層 cache 增加模型數量] → 限定為結構、步行、短期計劃時間三個實際時效邊界；不建立通用 cache framework。
- [geometry candidate 在詳情失敗時缺少端點校驗] → 仍要求完整結構與坐標校正；詳情稍後重試成功時補驗，失敗即移除該頁線條與 cache candidate。
- [自動重試增加 Citybus 請求] → 只針對可恢復類型、每段每 generation 一次並有短 backoff；成功 cache 與 single-flight 會抵消目前重複請求。
- [移除圖例降低首次理解線型的提示] → 時間線、marker 角色、線型與無障礙文字仍表達同一語義；這是用戶明確選擇，不新增其他常駐遮擋。

## Migration Plan

1. 先建立 HTTP response/session model、registry、穩定 query fingerprint 與分層 cache，保留舊字段的讀取相容，讓查詢結果仍可正常聚合及啟動詳情。
2. 接入每個 m1 的 session 擷取及詳情 Cookie，完成 parser 完整性分類、一次恢復與快取切換；舊進程內詳情 cache 不需資料遷移，App 重啟即清空。
3. 引入 geometry coordinator 與純 candidate in-flight，移除詳情成功後取消重載，加入有界自動／局部手動重試。
4. 更新時間線與摘要展示，移除地圖圖例及未使用資源，完成三語與無障礙回歸。
5. 依 tasks 執行 JVM、instrumentation、真實服務與完整 build 驗證。若 session 流程需回退，可停止捕獲／回傳 Cookie並恢復既有 partial 詳情；資料庫與已保存行程不受影響。

前置 `add-route-detail-google-map` 已完成實作但尚未歸檔；未來同步／歸檔時必須先處理該前置 change，再同步本 change 的 `citybus-route-geometry` 與 `route-detail-google-map` delta，避免能力基線順序顛倒。

## Open Questions

無。幾何重試、session 恢復、快取語義、部分步行降級、同站轉乘及移除圖例均已與用戶確認。
