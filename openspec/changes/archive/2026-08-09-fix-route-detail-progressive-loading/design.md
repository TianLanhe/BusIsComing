## Context

路線詳情目前由 `RouteDetailActivity` 協調 MapView、Citybus P2P 詳情、分段幾何與首程 ETA。各資料源已在不同 executor／callback 上工作，但完成狀態散落於 Activity 欄位，視圖更新依賴多組布林值與計數器，因而存在完成順序、重試及過期 callback 互相覆蓋的風險。

站數有兩條確定的錯誤路徑：啟動摘要以 `alightingSeq - boardingSeq - 1` 估算中途站，詳情成功後以 `viaStops.size` 合計中途站，兩者都沒有計入下車站。相鄰上下車時必然得到 0；重新進入後仍依同一公式計算，所以不是舊 UI 狀態跨頁殘留。

詳情 parser 目前只要求找到上、下車端點，沒有證明中間站序完整；`RouteDetailCompleteness` 又描述步行／session 完整性，不描述站序品質。現有 `RouteDetailCache`、`RouteStructureCache` 與 `WalkingDistanceCache` 由每個 repository 實例擁有，與生效 spec 要求的 1 天 App 進程快取不一致；若只把現有實例提升為 singleton，殘缺站序及易變預計時間亦可能被長期復用。

MapView 沒有保存相機時，`restoreCamera()` 直接返回，Google Map 首幀使用世界預設 `(0, 0)`。首次 fit 又等待 geometry 計數歸零，造成非洲底圖先出現、內容集中完成後突然跳轉。幾何 candidate 可早於可靠詳情端點到達；若先交給 renderer，再由詳情晚校驗撤回，會違反「可靠內容只增加、不閃現後消失」的產品要求。

本變更沿用現有 Citybus、DATA.GOV.HK 與 Google Maps 接口，不新增依賴。UI 仍負責生命週期與展示，parser／repository 負責外部格式、驗證、cache 及請求編排；所有 App 自有新文案遵循現有三語與無障礙規則。

## Goals / Non-Goals

**Goals:**

- 以完整可靠站序計算乘坐站數，每段計入中途站與下車站、不計上車站。
- 在可靠站數取得前展示載入或暫不可用狀態，不再以 0 或估算值冒充成功。
- 建立 24 小時進程記憶體結構／步行快取及詳情 single-flight，並阻止殘缺、失敗或易變資料污染長期快取。
- 讓 Map、詳情、每段幾何與 ETA 同時啟動，經單一不可變頁面狀態按完成時機漸進展示。
- 以 generation、stable key、品質單調及生命週期門禁拒絕過期或較差事件；局部失敗與重試不清空其他成功內容。
- 讓地圖首幀位於香港，完整路線最多自動全覽一次，使用者手勢後不再自動搶鏡頭。

**Non-Goals:**

- 不建立磁碟／跨進程快取，不新增 SQLite、偏好或檔案遷移。
- 不改動 Citybus／Google URL、參數、header 契約或 ETA 演算法。
- 不重做 bottom sheet 三檔互動、時間線視覺或地圖角色。
- 不接入 Google Routes、步行導航、車輛即時位置或與詳情頁無關的重構。

## Decisions

### 1. 站數只由已驗證站序派生

新增獨立於 `RouteDetailCompleteness` 的結構驗證結果。每段站點須與 plan 的 route variant、公開路線號、上／下車 seq 對齊，站序唯一、嚴格遞增並完整覆蓋 `boardingSeq..alightingSeq`；端點角色、stop id 與坐標亦須有效。只有驗證成功的結構可以發布、計算站數、驗證幾何或寫入 cache。

摘要站數使用：

```text
rideStopCount = Σ (leg.viaStops.size + 1 alighting stop)
```

相鄰上下車一段顯示 1；多段時每段下車站各計一次，下一段上車站不計。同站換乘不額外增加站數。

首次結構殘缺時，有 recovery context 便沿用現有受控 session／query 恢復；沒有 recovery context 時只直接重試一次。第二次仍殘缺則回傳結構化局部錯誤，不展示或快取殘缺時間線。

**否決方案：**繼續使用 plan 差值作首屏估算會重現 0 站並掩蓋上游缺列；只把公式改為 `alightingSeq - boardingSeq` 仍無法證明 parser 實際取得完整站點，亦可能讓摘要與時間線不一致。

### 2. 快取按穩定語義分域，動態補充保持新鮮

由 `RouteDetailRuntime` 提供進程級快取擁有者並注入 repository；測試可注入獨立 clock、TTL 與 cache。TTL 維持 24 小時，進程結束即清空。

- 結構 cache key 為 `plan fingerprint + actual language`，value 只包含已驗證的路線段、站序、站名、stop id、坐標、方向、端點名稱及換乘結構。
- 步行 cache key 為穩定端點 context 加 plan fingerprint，只有所有必要步行段完整時才寫入。
- 預計上下車／到達時間、分段票價、ETA、session、Loading／Error、UI 文案及派生站數不進入 24 小時 cache。

結構 cache 命中後可立即發布正確站數、marker 與時間線骨架；同時仍發起本次詳情網絡請求，以取得新鮮分段票價、預計時間及其他動態補充。動態刷新失敗不得清空已發布結構。

cache 的過期檢查與寫入在同一同步邊界內完成；未驗證、部分或失敗結果不能建立 entry，較差結果不能覆蓋完整 entry。現有保存整個 `ParsedRouteDetail` 的 `RouteDetailCache` 收斂至 domain caches，避免不同入口產生不同 freshness 契約。

**否決方案：**直接把整個 `ParsedRouteDetail` 做全局 24 小時快取會重用舊預計時間與分段票價；磁碟快取會增加遷移、清理及資料保護成本，對本次返回重入需求沒有必要。

### 3. 相同詳情請求使用進程級 single-flight

新增 detail request coordinator，以完整 request identity 合併同時進行的相同請求。identity 包含 `rawInfo`、`generalInfo`、`listId`、實際語言、plan fingerprint、recovery context 與 opaque session reference；不同 identity 不合併。identity 只在記憶體比較或雜湊，不把 Cookie、PHPSESSID 或完整 query 寫入日誌。

consumer 離開只移除自己；仍有其他 consumer 時共享工作繼續。最後一個 consumer 離開時，可取消尚未開始或可安全中止的工作。成功或錯誤均結束該次 flight；錯誤不快取，手動重試建立新 domain generation 與新 flight。

**否決方案：**每個 Activity 各自請求會在快速返回／重入時重複外部工作；只用 plan fingerprint 合併則可能把不同語言、端點或 session context 錯誤共用。

### 4. 所有資料源並發，結果只經主線程 reducer 歸併

進頁後同時啟動 Map、可靠 cache／Citybus 詳情、最多 3 段幾何與首程 ETA。不存在人工的「詳情完成後才啟動 ETA」或按秒揭示；同一 HTTP 回應在 parser 與驗證完成後原子發布，各段幾何則可各自完成。

把 Activity 中散落的詳情、幾何、ETA、錯誤、計數與相機旗標收斂為不可變 `RouteDetailPageState`。背景工作只產生 event，event 攜帶 `pageGeneration`、`domainGeneration`、可選 stable key 與結構化結果；主線程 reducer 是唯一狀態寫入口，renderer／adapter 只消費狀態 diff。

核心不變量：

1. page、domain generation 與 stable key 均匹配才接受事件；
2. 已驗證 Success 不被舊 Loading／Error、candidate、較差 cache 或其他資料域失敗覆蓋；
3. 重試只提升失敗資料域或 geometry key 的 generation；
4. 刷新以 `Refreshing(previous)` 保留最近成功內容；
5. Activity 銷毀、語言改變或新頁面 generation 後拒絕舊 callback。

adapter、marker 與 polyline 使用 stable id 增量更新，未變資料域保持展開、選取、列表位置與已渲染內容。

**否決方案：**繼續增加 Activity 布林欄位只能覆蓋已知完成順序，難以證明所有排列安全；把請求改成串行雖能減少競態，卻延長首屏並違反使用者確認的真並發要求。

### 5. 幾何 candidate 通過目前 consumer 端點校驗後才發布

共享幾何工作仍只做 HTTP、解析、坐標校正及基礎驗證。若可靠詳情端點尚未到達，candidate 保存在 coordinator 內部，不進入可渲染 page state。端點到達後，各 consumer 以自己的上下車坐標校驗；成功才產生 geometry Success event，失敗只把該 key 置為局部 Error。

已通過校驗的其他 geometry key 不因某一 candidate 失敗、重試或過期 callback 而撤回。同 key 新 generation 成功可替換舊成功；舊 generation 的失敗不能使新成功回退。

**否決方案：**先繪製 candidate 再晚校驗雖然看似更快，但不可靠路線可能短暫閃現後消失，破壞內容單調與使用者信任。

### 6. Map 建立時預置香港相機，並明確相機所有權

首次開啟時，在 MapView XML camera attributes 或 `GoogleMapOptions` 中直接配置 `HONG_KONG_DEFAULT_CAMERA`，避免 Google Map 首幀使用 `(0, 0)`。初始建議值為香港中心約 `22.3193, 114.1694`、zoom 約 `10.5`；實作以目標裝置驗收固定城市級視野。

相機優先序為：可恢復的保存相機、首次香港相機、完整路線的一次平滑全覽。可靠站序可用且所有預期 geometry key 均到達終態後，bounds 使用查詢起終點、可靠站點及成功幾何；單段失敗不會讓全覽永久等待。

相機初始由 PAGE 持有。`OnCameraMoveStartedListener.REASON_GESTURE` 一旦出現，所有權切為 USER，本次頁面不再自動 fit；程式動畫不得誤判成手勢。bottom sheet padding、ETA、adapter 或局部 geometry 更新不重置相機。使用者仍可主動點擊全覽、目前位置或站點。

**否決方案：**只在 `onMapReady` 後 move 到香港仍可能暴露世界預設首幀；每段 geometry 到達便 fit 會造成連續跳鏡頭並搶奪使用者操作。

### 7. 錯誤與可觀測性保持資料域隔離

Map、詳情結構、動態補充、每個 geometry key 與 ETA 各自具有 Loading／Success／Error／Refreshing 狀態。局部錯誤只顯示對應重試入口；詳情失敗保留啟動摘要、查詢端點、Map、ETA 與可靠 cache，geometry 失敗保留其他線段和全部可靠站點，ETA 刷新失敗可保留最近成功值。

debug／結構化日誌可記錄安全雜湊後的 key、generation、cache hit／miss／expired、single-flight join、結構校驗原因、stale callback 拒絕及相機所有權轉移。不得記錄 Cookie、PHPSESSID、完整 session reference、完整 URL query、使用者自訂名稱或精確端點坐標。

## Risks / Trade-offs

- **[Citybus 站序可能出現合法缺號]** → 以現有 fixture 與繁／簡／英 live 樣本驗證連續性；若上游確有合法缺號，先更新結構完整性契約與可復現樣本，不靜默放寬為接受殘缺資料。
- **[cache 命中仍刷新動態詳情會增加請求]** → 以 single-flight 合併同 identity 請求；可靠結構先展示，刷新只補充易變資料，避免為減少請求而展示舊時間。
- **[不可變 reducer 增加初期模型數量]** → reducer 保持純 Kotlin，先以事件排列與 generation 測試鎖定不變量，再逐域遷移 Activity 欄位。
- **[進程 cache 持有多條站點結構]** → 維持 24 小時 TTL、僅保存必要 domain value，實作可加入受控條目上限；不持有 Activity／View／GoogleMap。
- **[香港預設到完整路線仍有一次相機移動]** → 使用平滑且最多一次的全覽；使用者任何手勢立即取得所有權，局部更新不再自動移動。
- **[更嚴格校驗可能把舊有部分成功改為錯誤]** → 只把站序主結構設為硬門禁，方向、票價、時間及部分步行仍是可選欄位；保存缺列 fixture 與受控恢復測試。

## Migration Plan

1. 以 fixture／model 測試先加入新站數語義與站序驗證，不改外部入口。
2. 將 domain caches 提升為進程擁有並加入 single-flight；保留測試注入點，移除整體 `ParsedRouteDetail` 長期快取路徑。
3. 引入純 reducer 與 page／domain event，按詳情、geometry、ETA、Map 順序遷移 Activity 狀態；每一步保持現有 UI 可運行。
4. 加入香港 Map options、相機所有權與一次全覽條件，再補齊局部狀態文案與 renderer diff。
5. 完成 focused tests、`./gradlew build`、三語 live 詳情樣本與任務自有 Google Maps 模擬器驗收後交付。

沒有持久化 schema 或外部 API 遷移。若 reducer／快取遷移出現回歸，可回退本 change 的程式提交；進程重啟會清除新 cache，不需資料清理。不得回退站數需求本身而重新展示 0 作為成功。

## Open Questions

無阻塞問題。香港預設 zoom 的最終微調屬裝置驗收參數，不改變「Map 建立首幀位於香港」的能力契約。
