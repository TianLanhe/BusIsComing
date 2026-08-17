## Context

目前 `CitybusBusRouteRepository` 在 Citybus P2P 基礎結果交付後，使用首程 `FirstLegEtaQuery`、`showstops2.php` 的 P2P stop map 及 DATA.GOV.HK Citybus ETA 漸進補全候車資料。`RouteDetailActivity` 與 `BusMonitorService` 亦各自建立 `CitybusFirstLegEtaService`，因此路線卡、全屏詳情、前台自動刷新與通知監控雖沿用相同查詢模型，尚未共用可持久化的跨營運商資料與映射。

Citybus、KMB 與 LWB 使用不同 stop ID。KMB Data API 的 ETA endpoint 另要求 `service_type`；相同路線號亦可能有多個 `bound + service_type` 變體。Citybus P2P variant 的上落車 sequence 又不等於公開 route-stop sequence，所以不能按方向字母、站名或 sequence 直接拼接。現有 Citybus P2P stop map 仍是運行時首程上車站身份的唯一來源；本 change 只以公開靜態資料建立該 CTB stop ID 到 KMB／LWB stop ID 的附加映射。

前期以 CTB 與 KMB／LWB 靜態站序直接雙邊 DP 校準，選定跳站成本 `G=100m` 與正規化路線門禁 `T=46`。校準樣本中路線 winner 與門禁接受均為 150/150，站對與參考結果一致 3963/3965，148/150 個方向完全一致；已知差異集中在 `107P I`、`115 O`，另有 118 特別班次及 `110/170/N691` 環線／分段方向限制。這些數字是選取第一版常數的可復現基線，不代表日後上游變更後永遠成立。

全局靜態來源一次完整檢查包含五個 GET：GTFS `routes.txt`、KMB route、KMB route-stop、KMB stop 及 CTB route。CTB 沒有全量 route-stop／stop endpoint，因此特定聯營路線需要在首次使用及每個新香港資料日首次使用時，以兩個方向 route-stop 加 N 個唯一 stop 請求懶載入。所有資料與計算第一版只保存在 App；服務端集中計算是明確技術債。

## Goals / Non-Goals

**Goals:**

- 以官方 GTFS 判斷 CTB 與 KMB／LWB 聯營路線，並可靠建立方向、service type 及站點映射。
- 在不阻塞 Citybus 核心結果的前提下，把 CTB、KMB、LWB 首程 ETA 聚合為各 consumer 共用的結構化結果。
- 以原子、可回退、按語義失效的本機資料快照與映射 cache 控制流量和過期結果。
- 保留每班真實營運商身份，在 ETA 詳情完整展示全部班次及三種品牌文字膠囊。
- 建立可重放的純邏輯／fixture 測試及至少一次可核對的真實雙營運商 App 見證。
- 把算法、校準、限制與服務端遷移技術債形成可持續維護的文件。

**Non-Goals:**

- 不把 DATA.GOV.HK CTB route-stop 當作 P2P 運行時上車站 fallback，也不以距離猜測缺失的上落車映射。
- 不使用站名標準化、方向名稱、端點名稱或 route long name 參與 DP cost 或候選預篩選。
- 不在路線卡、通知或 TTS 加入營運商標籤；這些 consumer 只使用合併後的最早班次。
- 不加入 KMB／LWB 路線規劃、換乘規劃或後續乘車段 ETA。
- 不在本 change 建立服務端、帳號、同步 API、WorkManager 或定時鬧鐘。

## Decisions

### 1. 使用獨立、可重建的靜態路線 SQLite 資料庫

新增獨立於 `bus_is_coming.db` 的靜態路線資料庫。它保存版本化全局快照、CTB 路線懶載入 slice、語義指紋、匹配結果與更新 metadata；既有行程及置頂資料庫不升級、不搬移，也不與可重新下載的大型 cache 共用交易。

全局表以 `snapshot_id` 隔離至少包含：

- GTFS 聯營 route 記錄；
- KMB／LWB route、`route + bound + service_type + seq + stop` 及 stop 座標；
- CTB route 清單；
- active snapshot、香港資料日、完整成功時間及各來源驗證 metadata。

懶載入表按 CTB `route + direction` 保存 route-stop，按 stop ID 保存名稱與座標，並保存 route slice 的 verified data day 與語義指紋。匹配 cache 分開保存 winner、狀態與每個 CTB stop ID 的 KMB／LWB stop ID。

新全局資料先寫入新的 `snapshot_id`；五項來源均成功解析及通過入庫校驗後，單一交易切換 active pointer。舊 snapshot 在切換後才清理。資料庫缺失、schema 不兼容或 cache 損壞時可刪除重建，App 回退 Citybus-only；不得因此刪除使用者行程、置頂或偏好。這個可重建資料庫須明確排除 Android cloud backup 與 device transfer，而不順帶裁決其他既有資料的 TD-003。

否決把資料加入現有 `RouteConfigDbHelper`，因為大型可重建 cache 的 schema／清理風險不應耦合使用者資料。亦否決只用記憶體，因為每日下載量、首次 DP 延遲及監控服務跨進程生命週期需要持久化復用。

### 2. 香港資料日、前台觸發與 single-flight 更新

資料日使用 `Asia/Hong_Kong`：當地時間早於 `05:15` 時歸入上一個日曆日，否則歸入當日。沒有成功快照時，首次 App 前台立即檢查；已有快照時，每個資料日首次進入前台且尚未成功檢查才觸發。

`BusIsComingApplication` 初始化 App 級 route database runtime，並由 Activity 前台計數或等價的 App 前台協調器通知首次前台事件。更新在有上限的背景 executor 執行，不阻塞 `Application.onCreate`、Activity 首幀或路線查詢；不用 WorkManager、精確鬧鐘或前台服務。App 未啟動時不更新；進程被殺時未提交 staging snapshot 自然作廢，下次前台重試。

全局檢查固定驗證：

1. 下載 GTFS 繁體資料包並只解析 `routes.txt`；接受有效 `304 Not Modified` 作為成功驗證，聯營門禁只保留 `agency_id=KMB+CTB` 或 `agency_id=LWB+CTB`。
2. 下載 KMB Data API route、route-stop、stop，保留 `co=KMB/LWB`、route、bound、service type、seq、stop ID 及座標。
3. 下載 DATA.GOV.HK Citybus `route/CTB` 清單。

自動與手動檢查共用一個 single-flight。手動操作繞過資料日門禁，但已有任務時只加入觀察，不發第二輪。來源 timeout、非 2xx、無效 JSON／CSV、必要欄位缺失或入庫校驗失敗均令本輪整體不切換；舊快照和上次成功時間保留。對可重試錯誤採有限指數退避，不在一次前台 session 無限重試，尤其避免 KMB 快速重複拉取造成 403。

查詢永遠讀取開始時捕獲的 active snapshot ID，不等待正在進行的每日更新。這是 stale-while-revalidate，而不是「啟動先更新完才可用」。

### 3. 入庫時完成 DP 前置資料校驗

route、route-stop 及 stop parser 在寫 staging snapshot 或 CTB route slice 前校驗必要 ID 非空、seq 為正且在同一變體中唯一、座標可解析且落在合法 WGS84 範圍、route-stop 引用的 stop 存在。非法記錄不留到每次 DP 前重複檢查；若缺失足以令該來源語義不完整，整個 snapshot／route slice 不發布。

這延續已確認的選擇：校驗屬資料取得與入庫責任，DP 接收已驗證的不可變站序。仍保留函式參數與空序列防禦，但不把它描述為每次運行的第二套業務校驗。

### 4. CTB route-stop／stop 按路線每日懶載入

GTFS gate 命中後才取得該 CTB 路線的 inbound 與 outbound route-stop，再為去重後的 N 個 CTB stop ID 取得 stop record；首次最壞為 `2 + N` 個請求，例如先前 118 樣本為 63 個。跨方向、跨路線重複 stop ID 在同一資料日以 stop-level single-flight 合併，但不改變「首次未命中最多 2+N」的上界描述。

沒有 route slice 時，Citybus ETA 正常交付，路線卡保持可用；CTB route slice 完整成功後才運行 DP 並漸進加入 KMB／LWB ETA。有舊 slice 時，每個新資料日首次使用立即復用舊映射，同時背景重做 `2 + N` 驗證；完整成功且語義指紋改變才令該路線匹配失效並重算。部分 route-stop／stop 成功不得覆蓋舊完整 slice。

手動「路線資料庫更新檢查」只處理五項全局資料，不遍歷已使用路線發出 `2 + N`，也不預計算 DP。

### 5. GTFS gate 後對同路線號全部 KMB／LWB 變體執行雙邊 DP

對一個已驗證 CTB `route + direction` 站序 `A=(a1…am)`，列舉 active snapshot 中相同公開 route 的全部 KMB／LWB `co + bound + service_type` 站序 `B=(b1…bn)`。不按方向名稱、首末站、站名或距離包圍盒預篩選。

令 `d(ai,bj)` 為 Haversine 米數，`G=100`：

```text
D[0][0] = 0
D[i][0] = iG
D[0][j] = jG
D[i][j] = min(
    D[i-1][j-1] + d(ai,bj),
    D[i-1][j]   + G,
    D[i][j-1]   + G
)

normalizedCost = D[m][n] / max(m,n)
```

站對距離不設硬門禁；較遠配對仍可參與整體最優路徑，避免局部閾值令真實偏移站台直接失敗。winner 先按 `normalizedCost`、再按 raw cost、`co`、`bound`、數值化 service type 及原字串穩定排序。最低 winner 只有在 `normalizedCost <= 46` 才接受；不要求領先第二名 `Δ`，不產生置信度分類。

DP 回溯中的對角步驟形成 CTB stop ID → KMB／LWB stop ID 映射，刪除／插入步驟不生成站對。站名只可保存作診斷與人工證據，不能改變 cost、winner 或門禁。

### 6. 匹配 cache 以語義指紋和輸入版本保護

CTB 指紋只包含 route、direction、seq、stop ID、座標；KMB／LWB winner 指紋只包含 co、route、bound、service type、seq、stop ID、座標。名稱、generated timestamp、JSON／CSV 行順序及其他不參與 DP 的欄位不令映射失效。

cache key／記錄至少包含：

- CTB route、direction、CTB fingerprint；
- winner co、route、bound、service type、KMB／LWB fingerprint；
- `G`、`T`、算法版本、raw／normalized cost；
- `MATCHED` 或 `NO_MATCH`；
- 對角站點映射及計算時間。

只緩存確定性 `MATCHED` 與通過完整輸入計算的 `NO_MATCH`。網絡、解析、取消、空缺輸入或資料庫故障不寫 `NO_MATCH`。全局切換後比較 route-level 語義指紋，只刪除受影響 route cache；`G/T` 或算法版本改變令全部匹配 cache 失效。

DP 開始時捕獲 active snapshot ID 與 CTB route slice fingerprint。寫回交易再次核對兩者；任一已變化即丟棄結果，仍有 consumer 時最多按新輸入重算一次。這避免每日更新與慢 DP 競態把舊結果寫入新資料。

### 7. P2P 上落車只有完整映射且同一 winner 順序有效才啟用 KMB／LWB ETA

運行時仍先由 `showstops2.php` 以 `legIndex + routeVariant + sequence` 取得 CTB boarding／alighting stop ID。公開 CTB route-stop 只用作離線 DP 輸入。系統在同一 `MATCHED` winner 中查找兩個 CTB stop ID 對應的 KMB／LWB stop ID 與 seq；只有 boarding 與 alighting 均存在且 `boardingSeq < alightingSeq` 才建立 KMB／LWB ETA query。

若特別班次的 P2P stop 未出現在 winner、任一站落在 DP gap、順序相反、同一 CTB stop 對應不唯一或 winner 已失效，該首程只查 Citybus，不使用最近站、同名站、另一 service type 或另一方向猜測。這保留 118 特別班次與環線已知限制的保守邊界。

### 8. 單一跨營運商 ETA repository 供所有 consumer 使用

引入不依賴 UI 的跨營運商首程 ETA 接口，集中協調現有 Citybus 查詢、靜態資料 gate／match 與 KMB Data API：

```text
https://data.etabus.gov.hk/v1/transport/kmb/eta/{stop_id}/{route}/{service_type}
```

KMB ETA response 的單筆記錄不包含 `stop`；站點身份由請求 URL 的 `{stop_id}` 與已映射 boarding stop 一致來保證。response 必須再次嚴格匹配 winner 的 `co`（KMB 或 LWB）、route、dir、service type 及可解析 eta。營運商身份來自上游 `co`，不能因 endpoint 位於 `/kmb/` 就把 LWB 改標成 KMB。未知 co 不猜測、不展示。

`EtaArrival` 增加結構化 `operator`，必要時另保留 source sequence；UI sequence 在合併後重新編號。所有有效 CTB 與 KMB／LWB arrivals 按 `etaMillis` 升序，完全同時時按 operator code 與 source sequence 穩定排序，不跨營運商去重，也不再於聚合層 `take(3)`。每筆保留目的地、備註、來源語言及來源 timestamp。

合併狀態規則：

- 任一來源有有效 arrivals：返回完整 `Available`，並保留各來源成功／空／故障診斷；另一來源故障不能刪除成功班次。
- 所有適用且成功的來源均為有效空陣列：返回 `NoArrivals`。
- 沒有任何 arrivals，且至少一個適用來源技術失敗：返回 `Unavailable`，不能把未知另一方誤稱為暫無車輛。
- GTFS 不適用、`NO_MATCH` 或 P2P 上落車門禁失敗：Citybus 結果維持既有語義，並保留結構化「未啟用跨營運商」原因作診斷。

路線查詢先交付 Citybus 結果；映射與 KMB／LWB 結果晚到時以原 route result ID、query generation、語言版本及 snapshot identity 漸進更新。全屏詳情、前台自動刷新與 `BusMonitorService` 改為注入同一 App 級接口／factory，而不是各自直接 `CitybusFirstLegEtaService()`；consumer 仍只按合併後第一／第二班執行現有顯示、排序和監控停止邏輯。

### 9. ETA Bottom Sheet 完整列表與三種品牌膠囊

路線卡維持現有高度、寬度及文案，不加入營運商。使用者在至少兩班 ETA 時點擊候車區，Bottom Sheet 固定展示標題、方向及更新時間；班次內容使用可滾動容器並展示所有合併 arrivals，不設三班 UI 上限。來源更新時間取目前完整列表各來源 timestamp 中最舊的有效值，避免用較新一方時間掩蓋另一方較舊資料。

每列在「第 N 班」與候車分鐘之間加入獨立文字膠囊：

| Operator | 香港繁體 | 簡體 | English | 背景 | 文字 |
| --- | --- | --- | --- | --- | --- |
| CTB | 城巴 | 城巴 | CTB | `#ECCF00` | `#004891` |
| KMB | 九巴 | 九巴 | KMB | `#E60012` | `#FFFFFF` |
| LWB | 龍運 | 龙运 | LWB | `#F15622` | `#17211F` |

CTB 色來自 Citybus 公開企業黃與官網藍，KMB 色來自官網主紅，LWB 色從九巴集團《龍運透視 2025》標誌實色提取。三組小字對比度分別約為 5.78、4.80、4.79，均達 WCAG AA；文字永遠存在，不能只靠顏色。深淺色沿用相同品牌色對，不使用完整 logo。每列無障礙描述包含班次、營運商、候車分鐘、到站時間與可用備註。

短屏及 font scale 1.0／1.3／2.0 下，標題區保持可見、列表可滾動、核心文字不裁切；品牌膠囊可以隨文字內容擴闊但不能壓縮 ETA 到不可讀。面板打開期間只接受同 route result ID 及目前 generation 的增量結果，並在一次 render 中同步更新分鐘與 operator，避免錯配。

### 10. 設定頁展示全局資料庫檢查狀態

在現有「路線資料」分組新增標準設定行「路線資料庫更新檢查」。摘要顯示五項全局來源最近一次全部成功驗證的香港時間，而不是任一 provider 的 generated timestamp 或 CTB route slice 時間。

狀態至少區分：尚未完成、最近同步時間、檢查中、已是最新、已更新及檢查失敗。點擊時繞過每日門禁；檢查中設定行禁用重複操作並觀察同一 single-flight。成功即刷新摘要；失敗以本地化短提示說明仍使用上次資料，不清除時間。設定 Fragment 銷毀時只解除觀察，不取消仍由其他 consumer／App runtime 使用的更新；過期 view callback 不可寫入重建後畫面。

### 11. 真實見證、獨立 oracle 與可重放 fixture 並行

新增由 instrumentation argument（例如 `runRealJointEta=true`）顯式啟用的真實驗證，不加入普通穩定 CI。它在任務自有模擬器上使用生產 parser、repository、SQLite snapshot、DP 與真實 HTTP，從當前聯營候選的已映射站對動態尋找 CTB 與 KMB／LWB 同時有至少一筆 ETA 的見證；優先歷史成功候選、起點／前段站點、非環線及較低 DP cost，並以受限並發、輪轉與退避避免輪詢風暴。

找到見證後不重新請求，將同一次 repository 結果交給真實 ETA Bottom Sheet，並保存：route／direction、兩方 stop ID、co／service type、DP cost、snapshot／fingerprint、請求時間、脫敏 URL、原始 JSON、UI hierarchy 及截圖。測試以獨立小型 oracle 從原始 JSON 計算 `(operator, etaMillis, display time)`，核對 UI 全部行數、排序、標籤和絕對時間；分鐘跨界只允許一分鐘差。原始真實回應及固定 clock 轉為具來源時間與 hash 的 fixture，供日常單元及 instrumentation 重放。

當受限窗口內沒有任何雙方班次，live test 必須為 skipped／inconclusive，報告「未取得實時雙營運商證據」；fixture 成功不能取代至少一次 live 見證，也不能把沒有車當成功。另保留已知 118 P2P 真實冒煙路徑，驗證正常 `FirstLegEtaQuery` 能接入映射與聚合，即使該時段只返回一方 ETA。

### 12. 文件與服務端遷移技術債

新增 `docs/cross-operator-route-stop-matching.md`，保存官方來源、schema 語義、DP 公式、校準基線、cache／失效、運行時 gate、已知限制、實證方法及未來多營運商擴展。實作完成後同步 `docs/architecture.md`、`docs/citybus-route-query-and-eta.md`、`docs/localization-guidelines.md` 與 README 導航。

`docs/technical-debt.md` 新增服務端遷移條目：目前影響是每個客戶端重複下載約 5 MB 解碼靜態資料、執行相同 DP 並承受上游限流；延期邊界是不在本 change 建後端或改變客戶端可回退性。推薦服務端集中更新官方來源、版本化算法、預計算／懶計算映射並以 cacheable API 或簽名快照下發。關閉條件至少包含 API schema／版本協商、資料新鮮度與 SLA、客戶端離線／舊版回退、灰度及回滾、來源合規、監控、帶寬／儲存收益及服務端結果與本機 golden corpus 一致。

## Risks / Trade-offs

- **[GTFS agency 只說明聯營，不保證每個方向／特別班次站序一致]** → GTFS 只作 route gate；實際方向、service type 與站點必須通過全量 DP 及 P2P 上落車順序門禁。
- **[T=46 可能接受未來新增的錯誤近似或拒絕真實大改道]** → 保存 cost、winner 與可重放 corpus；上游／算法版本變更後重跑比較，調整 T 必須升算法版本及全量失效。
- **[沒有 Δ 時近似同分 winner 可能不具語義唯一性]** → 以穩定排序保證可重現；環線／歧義路線列入限制與 golden tests，缺少完整上落車順序時不發 KMB／LWB ETA。
- **[CTB 每日懶驗證產生 2+N 請求]** → 只對實際查詢的聯營路線執行、同日復用、stop ID single-flight、有限並發與 stale-while-revalidate；手動全局檢查不擴散請求。
- **[KMB 大型 endpoint 或頻密 ETA 可能限流／403]** → 全局每日 single-flight、條件請求可用時復用、有限退避、舊 snapshot 回退；ETA consumer 合併相同 request identity 並限制並發。
- **[全局快照與懶 route slice 同時更新]** → 所有讀取與 DP 綁定 snapshot／slice identity，寫回前二次核對；切換只用原子 pointer，不原地改 active rows。
- **[兩方 ETA 時鐘、timestamp 或短時間資料不同步]** → 保留每筆 operator 與 source timestamp，以絕對 eta 排序；面板顯示保守的最舊來源更新時間，不跨營運商去重。
- **[只有一方空而另一方故障時容易誤報暫無車輛]** → 沒有任何已知 arrival 且任一適用來源故障時使用 `Unavailable`，只有所有適用來源成功為空才使用 `NoArrivals`。
- **[新增靜態 DB 被備份或損壞]** → 明確排除可重建 DB 備份；任何損壞回退 Citybus-only 並重新下載，不觸碰使用者 DB。
- **[品牌色在小字或色弱情境不可讀]** → 使用已計算達 AA 的文字／背景對，固定顯示營運商文字與完整無障礙描述，不只以顏色區分。
- **[真實雙營運商班次受時段限制]** → 動態掃描多條真實聯營路線、保存同批 response 證據並永久回放；未找到時誠實標記未取得證據，不降低完成門檻。

## Migration Plan

1. 先加入純 model、parser、資料日、fingerprint、DP 與獨立靜態 DB schema／migration 測試；既有 Citybus ETA 生產接線保持不變。
2. 接入每日全局更新與設定觀察，驗證無 snapshot、更新失敗、304、原子切換、資料庫損壞及備份排除；此階段查詢仍可 Citybus-only。
3. 接入 CTB route slice 懶載入、DP cache 與 P2P 上落車 gate，以 118、S1／R8 及 golden corpus 驗證 KMB／LWB winner。
4. 以共享跨營運商 ETA 接口替換三個直接建立 `CitybusFirstLegEtaService` 的生產 consumer；先通過 deterministic fixture，再打開真實 KMB／LWB 請求。
5. 更新 Bottom Sheet、設定 UI、三語、深淺色與無障礙；路線卡外觀保持不變。
6. 在任務自有模擬器完成真實 118 冒煙與動態雙營運商見證，保存 evidence／fixture；若未取得 live 見證，明確報告未完成該門檻。
7. 更新長期文件與技術債後運行全量 build、定向 unit／instrumentation、OpenSpec strict validation，再提交。

回滾時可停用跨營運商 runtime 或刪除獨立靜態 DB，所有 consumer 回到既有 Citybus P2P＋ETA 路徑；因沒有遷移使用者行程資料，回滾不需要還原 `bus_is_coming.db`。

## Open Questions

無。第一版 operator 範圍為 CTB、KMB、LWB；GTFS gate、`G/T`、資料日、懶更新、完整 ETA 列表、品牌膠囊、真實見證及服務端技術債均已確認。
