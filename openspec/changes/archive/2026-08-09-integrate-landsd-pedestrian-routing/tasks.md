## 1. 固定外部契約與純資料模型

- [x] 1.1 在 repository 測試 fixture 加入有效單 path／多 path／含 z 值、欄位缺失、非有限數值、空路線及首尾偏差 30 米內外的 CSDI 回應，讓測試不依賴真實外網。
- [x] 1.2 先以測試鎖定 WGS84 兩端點、`travelMode=3`、米制、固定英文 directions、`outSR=4326`、`returnZ=true` 與 `NA Campus` 等完整 request 參數及有向 round-6 key。
- [x] 1.3 實作可注入的 CSDI request builder、HTTPS source、parser 與原始成功／失敗模型，嚴格驗證正數距離、正數時間、全部獨立 paths、WGS84 點及 30 米軌跡端點門禁。
- [x] 1.4 以純單元測試鎖定總距離「原始值先加總再向上取整」、分段距離向上取整及正數分鐘向上取整且至少 1 分鐘的規則。

## 2. 規劃 Citybus 步行端點

- [x] 2.1 為 `origin`、`transfer:<index>`、`destination` 與 `SameStop` 規劃加入測試，覆蓋單段、多段、步行轉乘、同站轉乘、缺失端點及即使很近仍須查詢的步行轉乘。
- [x] 2.2 擴展 P2P 站點映射與詳情結構整合，以 `showstops2.php` 為坐標主來源，僅在 `routeVariant + sequence + stopId` 全匹配時使用詳情後備坐標，並拒絕兩來源偏差超過 30 米的分段。
- [x] 2.3 讓 Citybus 詳情與站點映射在卡片結果返回後並發取得，使端點已齊的首尾分段立即提交，轉乘分段只等待自身必要語義而不形成全路線串行瀑布。
- [x] 2.4 建立查詢端點 context、plan fingerprint 與有序 segment key／角色／SameStop 的穩定組合模型，並以回歸測試證明不按站名或近距離誤配端點。

## 3. 進程級 cache、single-flight 與並發控制

- [x] 3.1 先建立可控時鐘與 fake transport 的並發測試，覆蓋同 key 合併、反向 key 分離、全 App 同時最多 5 個 attempt、卡片公平排隊、詳情提升尚未執行 flight 及不搶占在途請求。
- [x] 3.2 實作 App 進程共用 pedestrian runtime、有界優先隊列、每 consumer 訂閱 handle、global-5 permit 與原子結果派送，讓卡片、詳情及不同候選路線共用相同 flight。
- [x] 3.3 實作 24 小時記憶體分段成功 cache 與路線組合 cache，覆蓋同步命中、部分命中只查缺失段、原子過期、語言切換重用，以及失敗／衝突／無效回應不快取。
- [x] 3.4 實作每次 attempt 8 秒總時限及約 300 毫秒後最多一次瞬時重試，只重試網絡／連線／timeout／HTTP 5xx，並為 HTTP 4xx、無路線及無效內容建立不重試測試。
- [x] 3.5 實作訂閱感知取消：個別 consumer 離開不影響共享 flight，最後 consumer 離開時移除排隊工作或中止在途 HTTP，且取消後 callback 不寫 cache 或 UI。
- [x] 3.6 擴展 debug diagnostics 及測試 observer，只記匿名 flight id 與 cache／queue／priority／attempt／retry／失敗分類，並以測試禁止完整 URL、任意精度坐標、stops JSON、站名、stop id、session 及返回軌跡點出現在日誌或 release 分析。
- [x] 3.7 先以 fake clock 測試 `AUTOMATIC` 對失敗 request key 的 5 至 30 分鐘指數退避、手動刷新／重新進入各繞過一次及成功清除，再實作不返回快取失敗內容的進程內資格記錄。

## 4. 路線卡片漸進距離與穩定排序

- [x] 4.1 在不改寫 `BusRouteOption.walkingDistanceMeters` 或 result identity 的前提下，加入獨立 `WalkingDistanceDisplayState`、查詢會話訂閱及 generation／resultId 門禁；新查詢、清空或真正離開時須正確釋放訂閱。
- [x] 4.2 串接卡片端點規劃與 pedestrian runtime：全部必要分段成功才發布 CSDI 原始總和，任一必要分段最終失敗即發布完整 Citybus 總距離並解除只為該卡存在的其餘訂閱。
- [x] 4.3 更新 `BusRouteCardBinder`／formatter 與繁中、簡中、英文資源，使 cache 未命中時只顯示 `查詢中…`／`查询中…`／`Checking…`，成功或回退只顯示整數米且不加來源或「約」。
- [x] 4.4 更新 `BusRouteSorter`、`PinnedRouteProjector` 及常用／搜尋結果狀態，使數值依方向排序、Loading 永遠置後、相同值與 Loading 按初始索引穩定；只有步行排序因更新移位且置頂 token 區不動。
- [x] 4.5 擴展 coordinator、formatter、adapter diff 與 sorting 測試，覆蓋 cache 首幀、亂序／重複 callback、任一段失敗立即整體回退、步行與非步行排序、常用置頂、搜尋全量及卡片總耗時／到達／ETA 不變。
- [x] 4.6 以可跨 configuration change 的邏輯查詢會話保存訂閱與原始步行 snapshot，驗證旋轉、主題及語言切換只替換 observer／重新格式化，不重請 CSDI 或讓舊 callback 覆蓋新查詢。
- [x] 4.7 把 ETA、站點預覽、CSDI 與自動刷新基礎結果接入同一結果 projection，補充基礎結果已完成 cycle 後 CSDI 仍可更新、下一 query generation 取消舊 consumer、只排序一次及 stable-id＋pixel-offset 視口錨點測試。

## 5. 詳情分段狀態、摘要與時間線

- [x] 5.1 擴展 `RouteDetailPageState` 與 reducer，以 stable segment id 保存 Loading、CSDI 成功、Citybus 回退及 SameStop，並以 page／walking generation 門禁和整表重新派生防止亂序、重複及競態累加。
- [x] 5.2 讓詳情邏輯會話訂閱共享 pedestrian runtime，重用卡片成功 cache、提升排隊 flight，並在 configuration change 保持訂閱、真正返回時解除；Citybus 詳情、ETA、巴士幾何及 CSDI SHALL 保持資料域並發與局部降級。
- [x] 5.3 更新 `RouteDetailUiFormatter`／adapter：成功段展示向上取整米數與至少 1 分鐘的約略時間，SameStop 不顯示 0 值，失敗段只顯示 Citybus 分段米數，連 Citybus 米數亦缺失時顯示三語「距離暫不可用」。
- [x] 5.4 更新詳情摘要：等待必要段時顯示查詢中，全部成功時用原始 CSDI 分段先加總再向上取整，任一最終失敗時立即完整回退 Citybus 卡片總距離且保留其他成功分段。
- [x] 5.5 加入 reducer、formatter、adapter 與 Activity 整合測試，證明 CSDI 只影響分段步行距離／約略時間／軌跡，Citybus 總耗時、預計到達、巴士段計劃時間與首程 ETA 均不被重算。

## 6. Google 地圖步行軌跡、相機與署名

- [x] 6.1 先擴展 `RouteMapPresentationBuilderTest`，鎖定每個 CSDI 子路徑獨立 stable id、有序點列及 path 邊界，以及 Loading／SameStop／失敗／回退不生成任何步行直線或連接線。
- [x] 6.2 更新 `RouteMapPresentationBuilder` 與 `GoogleRouteMapRenderer`，移除端點直線步行示意，只沿每個 CSDI path 的同一有序 geometry 以屏幕投影固定間距＋局部切線的較粗灰色開放折角增量渲染；不得顯示灰色底線或在子路徑間補線，單段失敗不得清空 marker、巴士幾何或其他成功步行 path。
- [x] 6.3 擴展相機 policy 與測試：Map 首幀固定香港、可靠端點／站點結構到達後最多自動 fit 一次、使用者手勢取得相機所有權、晚到巴士幾何／步行 paths 不移動相機，全覽按目前全部可靠內容重新計算。
- [x] 6.4 按官方條款加入地政總署官方標誌、三語雙行精簡署名及可開啟的完整來源／版權／免責說明；只在至少一條 CSDI path 實際顯示時出現，沒有 path 時隱藏。
- [x] 6.5 更新地圖 padding／安全區、站名碰撞保留矩形與無障礙語義，驗證 CSDI 署名不遮擋 Google Logo、Google 法律文字、返回、目前位置、全覽控件、站名或 bottom sheet，且不重新引入常駐路線圖例。
- [x] 6.6 擴展 renderer、camera、Activity 及 process recreation 測試，覆蓋多 paths、漸進新增／移除、過期 callback、先手勢後結構、全覽、署名顯隱及重新開啟恢復初始摘要／相機。

## 7. 多語、無障礙與整合回歸

- [x] 7.1 完整審校繁中、獨立簡中及英文的查詢中、距離不可用、約略時間、CSDI 來源／版權／免責與 content description，並檢查窄屏、大字體及 RTL 非需求邊界不造成裁切或核心內容丟失。
- [x] 7.2 執行受影響的 repository、parser、cache、coordinator、sorter、formatter、reducer、presentation、renderer 與 camera focused tests，修復任何 Citybus 詳情、ETA、置頂或監控回退回歸。
- [x] 7.3 使用 fake Citybus／CSDI 延遲與失敗矩陣驗證最多 5 個並發、共享端點不隨候選路線線性增加、不同完成次序內容單調、取消／重入及成功 cache 與失敗重試。

## 8. 裝置驗收、完整構建與技術債

- [x] 8.1 在不接管既有模擬器的前提下，定義並啟動本任務自有 Google Play／Google Maps 設備畫像，驗收香港首幀、卡片漸進距離、詳情局部成功、真實多子路徑、相機所有權、全覽及署名安全區，完成後關閉該模擬器。
- [x] 8.2 在繁中、簡中、英文、淺色、深色、約 360dp、font scale 1.3／2.0 與 TalkBack 場景驗收卡片短文案、時間線、署名、觸控／朗讀語義及 Google attribution 不被遮擋。
- [x] 8.3 對少量可復現香港端點執行只讀 Citybus + CSDI 真實抽查，核對固定參數、距離／時間／多 paths、30 米門禁及 single-flight 計數；記錄外部可用性限制且 SHALL NOT 讓一般測試依賴服務成功。
- [x] 8.4 執行 `./gradlew build`，確認生產 HTTP 未被 fixture 取代、沒有新增 key／權限／SQLite schema／背景服務或敏感日誌，並核對全部 OpenSpec scenarios 已由自動化或明確人工證據覆蓋。
- [x] 8.5 僅在實作與上述驗證完成後更新 `docs/technical-debt.md`：記錄 Citybus 總耗時／巴士時間與 CSDI 固定 1 m/s 分段分鐘仍不一致、通知監控個人步速保持獨立，以及以統一步速、時間依賴公交、轉乘重算、清楚標示與回歸測試作關閉條件。
- [x] 8.6 重新執行 `openspec validate integrate-landsd-pedestrian-routing --strict`，如實勾選 tasks，檢查 `git status --short` 與 staged diff 只包含本 change 授權範圍後按 apply 流程提交。

## 9. 精簡 CSDI 署名

- [x] 9.1 先補充署名 layout／instrumentation 測試，鎖定可見背景約 116×29dp、官方標誌約 15dp、既有雙行三語內容、淺色低對比 surface，以及不含粗描邊或厚重陰影。
- [x] 9.2 更新 CSDI 署名資源與 layout，在保持官方標誌比例及相同兩行內容的前提下縮小可見尺寸，並讓可見短署名字體放大在約 1.3 倍封頂。
- [x] 9.3 以 `TouchDelegate` 或等效機制提供至少 48dp 有效觸控區，保持完整對話框正常跟隨系統字體比例，並驗證 TalkBack 只產生一個可理解操作節點。
- [x] 9.4 把站名碰撞保留區改為可見署名矩形加必要安全邊距，不以觸控矩形擴大視覺避讓；bottom sheet 拖動期間以 `translationY` 跟隨，僅在穩定 detent 更新精確 margin。

## 10. 署名回歸與完成檢查

- [x] 10.1 在繁中、簡中、英文、明暗主題、約 360dp、font scale 1.0／1.3／2.0 與 TalkBack 場景量測署名、標誌及有效觸控區，確認 Google Logo、法律文字、站名、地圖控件和 bottom sheet 均不被遮擋。
- [x] 10.2 在任務自有 Google Maps 模擬器驗證實際 CSDI path 顯隱、拖動流暢度及完整說明入口，完成後關閉本任務啟動的全部模擬器。
- [x] 10.3 執行受影響的 focused tests、`./gradlew build` 與 `openspec validate integrate-landsd-pedestrian-routing --strict`，如實記錄真實網絡或設備限制並核對 diff 僅含授權範圍。
