## 1. 建立 Citybus 搜尋 session 契約

- [x] 1.1 在 `data/repository` 為 Citybus HTTP 回應新增可測的 body／response-header 邊界，先加入測試證明只擷取同源 `Set-Cookie` 中的 `PHPSESSID`，並拒絕 ad、consent、tracking 與未知 Cookie。
- [x] 1.2 在 `data/model` 定義不透明 `sessionRef`、原 `m1`、語言與一次恢復所需的非敏感搜尋描述，建立只由乘車段 route variant／上下車站序及路線鏈構成的穩定 plan fingerprint。
- [x] 1.3 實作 process-scoped Citybus session registry，包含隨機 reference、短 TTL、查詢取代／過期清理與 thread-safe 存取；以測試確認原始 `PHPSESSID` 不寫入 SQLite、檔案、Bundle 或可序列化候選模型。
- [x] 1.4 擴充 `P2pRouteDetailQuery`／詳情啟動 primitive 參數以攜帶 session reference 與恢復描述，維持舊 Bundle 缺少新欄位時可讀並降級為 session 恢復。

## 2. 讓 m1 查詢與候選保留正確 session

- [x] 2.1 重構 `CitybusBusRouteRepository` 的 `m1=T/F/W` fetch，使每個並行請求獨立取得 body 與自己的 `PHPSESSID`，初始請求仍不發送 Cookie 或靜態瀏覽器 header。
- [x] 2.2 調整 Citybus 路線 parser／投影流程，讓每個成功候選把 `rawInfo`、`ginfo`、`lid` 與產生它的 session reference 綁定，不把 session 身分納入路線顯示、排序或穩定業務身分。
- [x] 2.3 更新 m1 聚合／去重，確保保留代表候選時一併保留同一原始候選的 `lid + sessionRef`，不得跨模式拼接；加入三個模式回傳不同 session 的並行回歸測試。
- [x] 2.4 擴充 debug 診斷測試，確認完整 Cookie、`PHPSESSID`、session reference、完整坐標／查詢時間及 rawInfo 不出現在日誌，release 路徑不輸出查詢診斷。

## 3. 使用匹配 session 取得與解析完整步行距離

- [x] 3.1 保存脫敏的單段、多段、繁體、簡體、英文 fixture：包含無 session 但站點可解析的空 timetable、匹配 session 的完整距離、部分距離、同站換乘及過期 session 形態；fixture 不包含可用 `PHPSESSID`。
- [x] 3.2 修改 `CitybusRouteDetailRepository`，向 `getp2pstopinroute.php?info&ginfo&lid&l` 只加入候選匹配的 `Cookie: PHPSESSID=...`，以測試確認不發送瀏覽器 header、無關 Cookie或把 session 轉送至其他 host。
- [x] 3.3 擴充 `CitybusRouteDetailParser`，從 `showtimetable1(...)` 按 `legCount + 1` 對齊起點／每次轉乘／終點距離，並支援繁體、簡體、英文 HTML fallback；加入精確數值與分段次序測試。
- [x] 3.4 新增 `Complete / Partial / SessionMissing` 詳情完整性分類，確保站點可解析但 timetable 與所有必要步行欄位同時為空時觸發 session-missing，而不是合法全零或成功空距離。
- [x] 3.5 保持同站轉乘為獨立類型且不產生 `0 米`／步行段；對部分資料保留已知距離、未知段與站點主結構，加入單段、多段、同站及部分距離 parser／repository 回歸測試。

## 4. 實作一次 session 恢復與分層快取

- [x] 4.1 在 detail repository 增加每個 request generation 最多一次的 session 恢復：registry miss 或 `SessionMissing` 時，以原起終點、目前語言、原 m1 及新的香港時間重做 `ppsearch_p3.php`。
- [x] 4.2 實作恢復候選匹配，只接受 route variant、boardingSeq、alightingSeq 與路線鏈一致的結果，使用新候選自己的 `lid + sessionRef` 重試詳情；不得以新 session 中相同 `lid` 直接匹配。
- [x] 4.3 確保恢復搜尋不更新來源結果、排序、捲動狀態或常用行程使用次數；無匹配、二次 session 缺失、語言變更、頁面銷毀及 generation 過期時停止或忽略舊結果，加入確定性測試。
- [x] 4.4 將既有完整 `rawInfo + lang` 詳情 cache 拆為一天 `RouteStructureCache(plan fingerprint + lang)`、一天 `WalkingDistanceCache(stable origin + destination + plan fingerprint)` 與不進入一天 cache 的計劃時間；ETA 保持既有獨立刷新。
- [x] 4.5 以 provider id 或 P2P 規範化坐標建立穩定地點 key；加入跨不同 session 相同上下文可命中、相同乘車段但不同起終點不得串用、語言結構隔離及計劃時間不被長期重用的測試。
- [x] 4.6 確保只有通過資料域完整性校驗的內容才寫入 cache，`SessionMissing`、未知距離、失敗與空回應不覆蓋已有完整步行距離；加入過期、部分更新及後續完整結果覆蓋測試。

## 5. 消除冷載入幾何競態

- [x] 5.1 先新增可控 datasource 回歸測試，重現冷快取下 detail 先於慢 geometry 返回時同一 key 只發起一次、最終 BUS line 在不退出頁面的情況下出現。
- [x] 5.2 新增相反順序測試：geometry candidate 先返回時等待／接受後續端點校驗，detail 到達不得取消或重發同一 key，且有效線條不得被較晚 callback 清空。
- [x] 5.3 重構 `CitybusRouteGeometryRepository` 的 in-flight future，只共享 HTTP、parser、坐標校正與結構驗證 candidate；各 consumer 獨立執行端點校驗，第一個 caller 的端點不得綁定共享 future。
- [x] 5.4 調整共享工作的取消語義：單一頁面離開只停止向該 consumer 派送，仍有 consumer 時不 `shutdownNow()`；端點不匹配時移除該 success cache candidate，失敗結果不快取。
- [x] 5.5 在 `RouteDetailActivity`／獨立純 Kotlin coordinator 中為每段維護 `Loading / Candidate / Loaded / Failed`，頁面啟動只載入一次，detail callback 只補校驗並重建 presentation。
- [x] 5.6 對傳輸錯誤、timeout、空回應與有效點不足加入前台短 backoff 自動重試一次；非法 key、明確 malformed 坐標與端點不匹配不自動重試，並加入錯誤分類 policy 單測。
- [x] 5.7 保留局部手動重試，只重新載入最終失敗／過期 key且不清空成功段；加入多段部分失敗、自動重試成功、永久失敗不畫假直線及頁面銷毀停止排程的測試。
- [x] 5.8 覆蓋冷 cache、暖 cache、configuration change、真正退出重入、語言 generation 作廢與共享 consumer，確認舊 callback 不更新新頁且相同有效 key 一天 cache 仍可復用。

## 6. 更新步行 UI 並移除地圖圖例

- [x] 6.1 更新路線詳情 model／formatter／adapter，讓時間線分別展示起點、每次步行換乘及終點的已知米數；未知段只顯示步行語義，同站換乘顯示三語專用文案且不顯示 `0 米`。
- [x] 6.2 更新摘要完整性：所有必要距離完整時顯示分段之和；部分距離時顯示 `ppsearch_p3.php` 卡片總距離與三語「部分步行距離由路線摘要提供」說明，不把缺失段當零或回填列表排序資料。
- [x] 6.3 從路線詳情 XML、Activity binding／可見狀態及無障礙樹移除整個地圖圖例容器，刪除只由圖例使用的三語字串／尺寸／樣式，且不新增替代常駐說明卡。
- [x] 6.4 更新 map padding／浮動控件驗證，確保摘要、半屏、全屏及 WindowInsets 變化後 Google Logo、法律文字、返回、目前位置與全覽控件可見且不互相遮擋。
- [x] 6.5 擴充 formatter／adapter／instrumentation 測試，覆蓋三語、明暗、360dp、font scale 1.0／1.3／2.0、TalkBack 語義、完整／部分／session-missing 步行狀態及圖例節點完全不存在。

## 7. 真實服務與模擬器驗收

- [x] 7.1 以脫敏等價 cURL／測試工具完成 `ppsearch_p3.php -> getp2pstopinroute.php` A/B：無 session 時確認空距離，僅匹配 `PHPSESSID` 時確認單段與多段的每個精確距離；覆蓋繁體、簡體、英文且不保存 session 值。
- [x] 7.2 驗證三個 `m1` 同時返回時各自 `lid + session` 正確，並驗證 session 過期後一次恢復能匹配原方案；記錄無匹配／再次失敗的誠實降級結果。
- [ ] 7.3 使用任務專用且完成後關閉的 Google Play 模擬器，從冷 App 進程首次開啟真實單段與多段 Citybus 結果，確認 Google 底圖、所有站點、每段道路幾何、示意步行、精確分段距離及 60 秒 ETA 均在同頁出現而不需退出重入。
- [ ] 7.4 在同一任務專用模擬器驗證第一輪 geometry 暫時失敗後自動恢復、單段永久失敗的局部手動重試、configuration change／背景前台／真正退出重入、位置權限及無圖例三檔 UI；高縮放抽查 N118 等校正幾何仍貼合 Google 道路。
- [x] 7.5 更新 `docs/route-detail-google-map-validation.md` 或對應驗證記錄，區分自動化證據、真實服務證據與受網路限制未完成項，不記錄 API key、PHPSESSID、完整 Cookie 或其他敏感資料。

## 8. 最終回歸與交付

- [x] 8.1 執行新增及相關 JVM 測試，至少覆蓋 Citybus 路線查詢／聚合、session registry、詳情 parser／repository／cache、geometry repository／coordinator、presentation builder 與 UI formatter。
- [x] 8.2 執行相關 route-detail instrumentation suites，確認既有 bottom sheet 三檔、返回、地圖／時間線聯動、定位、ETA、三語／明暗／大字體與生命週期未回歸。
- [x] 8.3 執行 `./gradlew build`、`openspec validate fix-route-detail-geometry-and-walking-session --strict` 與 `git diff --check`；如外部網路阻止真實驗收，保留失敗輸出及明確剩餘風險而不得宣稱通過。
- [x] 8.4 核對 proposal、design、四個 delta specs 與實作一致，確認前置 `add-route-detail-google-map` 的同步／歸檔順序，更新本 tasks 勾選及 OpenSpec 狀態。
- [x] 8.5 檢查 `git status --short` 與 staged 範圍，只提交本 change 實作、測試、規格及驗證記錄，保留用戶既有 `app/build.gradle.kts` 或其他無關改動，並依專案規則建立 conventional commit。
