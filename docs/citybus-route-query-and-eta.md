# Citybus 路線查詢與首程 ETA

## 目的

本文記錄目前生產鏈路中 Citybus 地點、點到點路線、P2P stop map、路線詳情與 DATA.GOV.HK ETA 如何協作。它描述上游原始資料、App 解析與展示之間的邊界，避免以公開 route-stop、fixture 或舊實驗假設替代目前 runtime。

## 語言與請求共同規則

每次網絡工作開始時捕獲不可變 `LanguageSnapshot`：

| App 實際語言 | Citybus `l` | DATA.GOV.HK 欄位順序 |
| --- | --- | --- |
| 香港繁體 | `0` | `tc → sc → en` |
| 簡體 | `2` | `sc → tc → en` |
| English | `1` | `en → tc → sc` |

Citybus mobile 請求不模擬瀏覽器，不附加 Referer、User-Agent 或 X-Requested-With。唯一 Cookie 例外是 `getp2pstopinroute.php` 可帶上與該候選、語言及搜尋模式精確配對的 `PHPSESSID`；地點、路線、stop map、geometry、Google 及 DATA.GOV.HK 請求均不得帶此 session。整體請求失敗不得切換語言重試；cache、in-flight 合併及 callback 必須隔離或核對語言版本。

## 整體資料流

```mermaid
flowchart TD
    Place["bsearch 地點候選"] --> Query["ppsearch T / F / W"]
    Query --> Parse["解析基礎路線、rawInfo 與各模式 session reference"]
    Parse --> Initial["先展示基礎結果"]
    Parse --> StopMap["showstops2 P2P stop map"]
    StopMap --> Preview["上下車站預覽"]
    StopMap --> Eta["DATA.GOV.HK 首程 ETA"]
    Parse --> Detail["getp2pstopinroute 路線詳情與分段步行"]
    Parse --> Geometry["getlinep2p 每段道路幾何"]
    Preview --> Incremental["增量更新路線卡"]
    Eta --> Incremental
    Detail --> Page["全螢幕地圖詳情頁"]
    Geometry --> Page
```

## 地點搜尋

```text
GET https://mobile.citybus.com.hk/nwp3/bsearch_p3.php
    ?q=<query>
    &limit=100
    &timestamp=<millis>
    &l=<lang>
```

`q`、`limit=100`、`timestamp` 和 `l` 均屬目前契約。parser 產生具有名稱及精確座標的 `Place`；只有與目前文字、query generation 和語言版本一致的候選才交給 UI。失敗或無結果不使用 fixture／舊 cache 生成假候選。

## 點到點路線查詢

`CitybusBusRouteRepository` 對同一起終點和香港時間並發查詢三個 `m1` 模式：`T`、`F`、`W`。

```text
GET https://mobile.citybus.com.hk/nwp3/ppsearch_p3.php
    ?slat=<origin latitude>
    &slon=<origin longitude>
    &elat=<destination latitude>
    &elon=<destination longitude>
    &t=<Asia/Hong_Kong yyyy-MM-dd HH:mm>
    &ws=1.3
    &leg=2
    &m1=<T|F|W>
    &l=<lang>
```

三個模式中至少一個成功即可返回部分結果；全部失敗才返回查詢失敗。聚合以乘車段列表、價格、總耗時、步行距離及 P2P `rawInfo` 去重，優先保留具有首程 ETA query 的重複項，基礎結果按總耗時排列。

HTML parser 提取路線、車費、耗時、步行距離、Citybus `showroutep2p(...)`／`showroutestop(...)` 的 `rawInfo` 及詳情參數。parser 只解析上游資料，不產生本地化 UI 句子。

每個 `m1=T/F/W` 回應可各自透過 `Set-Cookie` 提供不同 `PHPSESSID`。repository 只接受 Citybus HTTPS `/nwp3/` 回應中唯一、格式有效的值，把它登記為預設 30 分鐘有效的不透明 reference，並連同語言、原起終點、該 `m1` 模式及 query scope 綁定到候選。聚合與去重不得令候選錯配另一模式的 `lid` 或 session；新搜尋 scope 會使上一 scope 的 references 作廢。日誌、Bundle、fixture 及持久化資料不得包含原始 session 值。

## P2P `rawInfo`

典型值：

```text
2|*|CTB||8X-THR-1||6||31||O|*|CTB||1-MAF-1||5||15||I|*|
```

拆解：

```text
第一項 2                         乘車段數
CTB || 8X-THR-1 || 6 || 31 || O
公司    route variant  上車 下車 bound
```

`8X-THR-1` 是 Citybus 內部 route variant；公開 ETA 路線號是 `8X`。上／下車 seq 是 P2P variant 內站序，不能假設與 DATA.GOV.HK 公開 route-stop 站序相同。

## P2P stop map

```text
GET https://mobile.citybus.com.hk/nwp3/showstops2.php
    ?r=<rawInfo>
    &l=<lang>
```

返回 HTML／script 中的 `addstoponmap(...)` 提供 route variant、bound、seq、stop id、名稱及座標。App 建立 `P2pStopMap` 後，按以下順序找站：

1. `legIndex + routeVariant + sequence` 精確匹配。
2. 找不到時，以 `routeVariant + sequence` 回退，兼容上游 leg index 信息不足。

首程 ETA 使用第一乘車段的上車 seq；卡片預覽使用各段上下車站。展示名稱移除上游 sequence 前綴並按目前 formatter 取主要站名，stop id 和座標仍保留完整身份。

成功且非空的 stop map 按 `rawInfo + lang` 在進程內快取 24 小時；失敗、空內容或解析失敗不作成功快取。ETA 管線和站點預覽管線各自按 request key 合併同類路線，但 `CitybusP2pStopMapResolver` 本身沒有跨管線 single-flight；兩條管線在成功 cache 建立前並發到達時，仍可能各自發出一次 `showstops2` 請求。不得把目前行為描述為全局「同一 rawInfo 保證只請求一次」。

`showstops2` 失敗、缺少站點或無法匹配時，卡片預覽／ETA 分別顯示不可用，不回退 DATA.GOV.HK 公開 `route-stop`。舊 `CitybusRouteStopResolver` helper 只作歷史診斷。

## 首程 ETA

取得 stop id 後：

```text
GET https://rt.data.gov.hk/v2/transport/citybus/eta/{company}/{stopId}/{publicRoute}
```

匹配規則：

1. 先篩選 `route + stop + dir` 完全匹配且 ETA 可解析的記錄。
2. 優先使用 `seq == boardingSeq` 的嚴格記錄。
3. 嚴格記錄不存在時，回退同一 `route + stop + dir`，不得跨路線、站點或方向。
4. 按 `eta_seq`、ETA 時間排序，最多返回三班。
5. 候車分鐘向上取整；已到或已過時間顯示 0 分鐘。

目的地和備註按 App 語言選擇官方 `tc/sc/en` 單欄位回退，並記錄實際欄位語言。`generated_timestamp` 或 `data_timestamp` 用於更新時間；不得把回退值寫入已保存地點或跨語言 cache。

結構化結果區分：缺少首程資料、stop map 請求失敗、stop map 無效、上車站找不到、ETA 請求失敗、ETA response 無效、目前無班次及可用班次。ETA 不可用不隱藏整條路線。

## 路線詳情與步行分段

```text
GET https://mobile.citybus.com.hk/nwp3/getp2pstopinroute.php
    ?info=<rawInfo>
    &ginfo=<ginfo>
    &lid=<lid>
    &l=<lang>
```

詳情展示上下車站、方向、途經站、換乘語義及起點／轉乘／終點步行分段。`ginfo` 和 `lid` 均保留；現有真實樣本已證明移除 `ginfo` 會缺失全程分鐘／到達資訊，而單一 `lid` 樣本不足以證明可安全刪除。

repository 解析候選的不透明 session reference，只有其語言及 recovery context 均與目前詳情 query 相同時，才對 Citybus HTTPS 詳情 URL 加上單一 `Cookie: PHPSESSID=...`。session 已過期、缺失，或 response 顯示 session 語義缺失時，可按原起終點、語言及原 `m1` 模式重做一次 `ppsearch_p3.php`，再以完整乘車段 fingerprint 匹配新候選。恢復最多一次，不以不同計劃、不同模式或另一語言替代；仍失敗時保留可解析的站點與時間線並標記為部分詳情。

詳情資料按領域分開快取：

- 路線結構以乘車計劃 fingerprint + 語言為 key，只保存乘車段及起終點名稱。
- 完整步行分段以穩定起終點 context + 乘車計劃 fingerprint 為 key；缺少任一必要分段時不寫入完整步行 cache。
- 計劃時間不放入上述一天 cache，首程 ETA 亦維持獨立即時管線。

兩個 cache 預設在進程內保存 24 小時。沒有 matching session 的 response 仍可提供部分詳情，但不得把摘要總步行距離冒充每段精確距離。

卡片 stop map 和詳情 response 是兩個上游來源。短時間不一致時，各自展示其對應內容並記錄診斷，不讓卡片資料覆寫詳情。

## 路線道路幾何

每個乘車段以 Citybus route variant 與上下車 seq 請求：

```text
GET https://mobile.citybus.com.hk/nwp3/getlinep2p.php
    ?rdv=<routeVariant>
    &start=<boardingSeq>
    &dest=<alightingSeq>
```

請求只帶上述三個 query 參數，不帶 Cookie 或瀏覽器 header。parser 保留上游點序及 point id；repository 在 parser 之後、端點驗證與成功 cache 之前，只對 geometry 套用 Citybus 舊底圖到 WGS84 的固定校正：

```text
googleLatitude  = citybusLatitude  + 0.0001935197
googleLongitude = citybusLongitude - 0.0000697374
```

站點、查詢端點、裝置位置與 Google 資料不作此位移。合法 geometry 至少有兩個有效點，首尾須落在各自上下車站 2 公里內；同 key 的同時載入共享 single-flight，整頁最多三路並發。成功結果按 route variant + 上下車 seq 在進程內快取 24 小時；失敗不快取，cache hit 仍按目前 consumer 的端點重新驗證，不匹配時移除該項。

暫時網絡、空 response 或有效點不足可在前台自動重試一次；malformed response、非法 key 及端點不匹配不自動循環。多段路線只重試失敗段，保留其他已成功 polyline；若 geometry 不可用，頁面保留 Google 底圖、站點及文字時間線，但不以站點直線偽造巴士道路線。

## 全螢幕詳情呈現

`RouteDetailActivity` 以 Google 地圖為背景及不可隱藏的摘要／半屏／全屏三檔 persistent Bottom Sheet 展示同一路線快照。文字詳情、geometry、Maps、位置及 ETA 各自載入和降級；同站轉乘使用合併角色 marker 且不畫步行線，需要步行的轉乘即使兩端坐標相同仍保留兩個站點角色。地圖只用灰色示意線表示步行，不提供導航或精確步行路徑，也不保留常駐圖例。

首程 ETA 進頁立即載入，頁面保持前台時從上一輪完成後每 60 秒刷新；進入後台停止。後續乘車段只展示 Citybus 計劃時間，不假裝具有即時 ETA。地圖失敗、Play services 不可用、定位拒絕或局部 geometry 失敗時，完整文字時間線與已成功資料仍可操作。

## 取消、增量更新與去重

- `RouteQueryCoordinator` 以 query id、owner active 狀態和語言版本交付 callback。
- repository 提交新 progressive query 時使舊 ETA／預覽 generation 作廢並關閉舊 executor。
- 相同首程 ETA request key 的路線在 ETA 管線內合併一次請求，再把結果分發到多個 result id。
- 詳情結構、完整步行分段、geometry 及 ETA 使用獨立 cache／generation，任一域失敗不清除其他域的成功資料。
- 相同 geometry key 的同時請求共享載入；consumer 取消只停止其 callback，不把已成功段標成失敗。
- ETA 與預覽互不等待；快者先更新對應卡片。

## 變更與驗證要求

修改 URL、參數、header、HTML／JSON parser、route variant、seq、stop id 或語言 mapping 時：

- 保存脫敏等價請求或原始 fixture，不把完整座標、API key、`PHPSESSID`、可還原 session 的 reference 或個人行程寫入日誌。
- 覆蓋三語、日／夜、直達／換乘、各 `m1` session 配對、session 恢復、部分 m1 失敗、空資料、geometry 端點、格式漂移和過期 callback。
- 以真實服務確認 fixture 仍代表目前上游；真實失敗不得改用 fixture 作生產結果。
- 若要引入公開 route-stop fallback、改變 session 邊界、坐標校正、跨管線 single-flight 或新 cache，先建立 OpenSpec change 說明身份準確性、私隱、失敗和並發影響。

行程與結果 UI 工作流見 `journey-query-workflow.md`；簡體站名延期問題見 `technical-debt.md`。
