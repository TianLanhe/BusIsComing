## Context

主頁路線結果卡片目前只展示路線、價格、耗時、候車與步行距離。`CitybusRouteParser` 已從 `showroutep2p(...)` 中解析首程 bus leg 用於 ETA，但尚未保留完整 P2P plan；因此卡片無法知道多段路線的末程下車站。

`add-route-detail-bottom-sheet` 已規劃使用 Citybus `getp2pstopinroute.php` 在點擊後按需展示完整站點詳情，且明確不在列表階段預取所有詳情。本變更只解決卡片列表掃讀：用 DATA.GOV.HK 的結構化 `route-stop` 與 `stop` API 推導首程上車站和末程下車站，並通過緩存與請求去重避免請求量按卡片數線性增長。

`refine-route-result-cards` 會調整卡片視覺層級與底部信息區。本變更的站點預覽行應兼容該佈局：放在路線/等候行下方、價格/耗時/步行信息區上方；若該 change 尚未實作，則放在現有路線行與價格行之間。

## Goals / Non-Goals

**Goals:**

- 在每張有可用預覽資料的路線卡片中展示單行 `上車 A站  →  下車 B站`。
- 從 `showroutep2p(...)` 的 `rawInfo` 解析完整 P2P bus legs，支援單段與多段路線。
- 用第一段 bus leg 的 `boardingSeq` 推導上車站，用最後一段 bus leg 的 `alightingSeq` 推導下車站。
- 通過 DATA.GOV.HK `route-stop` 查 stop id，再通過 `stop/{stop_id}` 查站名。
- 將 `route-stop`、`stop name` 和 `preview` 結果做 1 天進程內成功緩存；失敗不緩存。
- 路線列表先展示，站點預覽後台漸進補全，並限制並發、去重請求、忽略舊查詢回調。
- 卡片開始展示站點預覽後，去重規則保留 `rawInfo` 或首末站不同的候選路線。

**Non-Goals:**

- 不展示途經站、換乘站、每段上下車站或站點時間線；這些仍屬於底部詳情彈層。
- 不使用 Citybus `getp2pstopinroute.php` 作為卡片預覽的首屏資料來源。
- 不用 DATA.GOV.HK 重建 bottom sheet 詳情，也不改變 `add-route-detail-bottom-sheet` 已確認的詳情資料來源。
- 不阻塞路線列表首屏展示，不因站點預覽失敗改變路線查詢成功/失敗狀態。
- 不新增 SQLite 持久化表；緩存隨 App 進程結束自然清空。

## Decisions

### 決策 1：新增輕量 `P2pRoutePlan` 作為共用解析模型

本變更先補齊完整 `rawInfo` bus legs 解析，建議模型如下：

```text
P2pRoutePlan
├─ rawInfo
├─ lang
└─ legs
   ├─ company
   ├─ routeVariant
   ├─ route
   ├─ boardingSeq
   ├─ alightingSeq
   ├─ bound
   └─ directionPath
```

首程 ETA 可繼續由第一段 leg 派生 `FirstLegEtaQuery`，避免 `showroutep2p` 解析規則分散到兩套邏輯。`add-route-detail-bottom-sheet` 後續落地時也應復用或合併此模型，不應再新增另一套 rawInfo parser。

備選方案是等待 bottom sheet change 先實作完整模型。本方案會阻塞卡片預覽，而且兩個 change 並行時容易各自實作一套解析器，因此放棄。

### 決策 2：卡片預覽只取首程上車站與末程下車站

單段路線使用同一 leg 的 `boardingSeq` 和 `alightingSeq`。多段路線使用第一段 `boardingSeq` 作為上車站，最後一段 `alightingSeq` 作為下車站。

這符合卡片預覽的掃讀目標：回答“我在哪裡上車、最後在哪裡下車”。換乘站與每段站點屬於詳情彈層，不放在列表中，避免卡片變高且信息層級混亂。

### 決策 3：使用 DATA.GOV.HK 結構化 API 做預覽

預覽站點解析使用兩步：

```text
route-stop/{company}/{route}/{directionPath}
  -> seq -> stop_id

stop/{stop_id}
  -> name_tc / name_en / coordinates
```

`route-stop` 的 key 為 `(company, route, directionPath)`，同一路線方向可服務多張卡片。`stop` 的 key 為 `(company, stopId, lang)`；雖然 DATA.GOV.HK stop endpoint 以 stop id 查詢，保留 company 和 lang 方便和上層模型一致，也避免未來多公司或多語言擴展時重構 key。

備選方案是為每張卡片請求 Citybus `getp2pstopinroute.php`。它和 bottom sheet 資料完全一致，但每次查詢可能對所有候選路線產生額外詳情請求，與目前按需詳情策略衝突。

### 決策 4：緩存分三層，並只緩存成功結果

建議緩存：

```text
RouteStopCacheKey = company + route + directionPath
StopNameCacheKey  = company + stopId + lang
PreviewCacheKey   = rawInfo + lang
```

三層緩存 TTL 均為 1 天。`route-stop` 和 `stop name` 復用率高，可以讓多條相似候選路線共享基礎資料；`preview` 緩存則讓同一張卡片在重新綁定或排序後快速回填。

失敗、空響應、找不到 seq、找不到站名或解析異常都不緩存，避免短暫 API 問題污染後續查詢。

### 決策 5：列表先展示，預覽漸進補全

路線查詢成功後立即展示卡片主信息。站點預覽在後台執行，流程為：

```text
routes
  -> parse/collect preview plans
  -> collapse unique route-stop requests
  -> collapse unique stop name requests
  -> build preview per route
  -> callback update matching resultId
```

預覽補全需要沿用查詢 generation 或同等機制。用戶重新查詢、切換已保存路線、離開頁面或列表被替換後，舊預覽回調必須被忽略。

### 決策 6：卡片 UI 使用一行弱化預覽

卡片成功預覽文案固定為：

```text
上車 A站  →  下車 B站
```

該行放在路線/等候行下方、價格/耗時/步行信息區上方。標籤 `上車`、`下車` 使用次級文字風格，站名使用正文或略強文字風格；整行最多一行，超長站名用省略策略，不能擠壓右側候車時間或底部信息區。

預覽尚未完成、缺少 metadata 或查詢失敗時隱藏該行，而不是展示錯誤文案。這保持卡片安靜，不讓輔助信息干擾核心路線結果。

### 決策 7：去重規則納入可見站點差異

現有去重主要依賴路線段、價格、耗時和步行距離。站點預覽成為卡片可見信息後，如果兩條候選路線在這些字段相同但 `rawInfo`、首程上車站或末程下車站不同，系統應保留為不同卡片。

實作上可以先把完整 `rawInfo` 納入 dedup key；如果後續真實樣例顯示同一 `rawInfo` 仍可因語言或站名查詢產生差異，再調整為首末站 key。

## Risks / Trade-offs

- [Risk] DATA.GOV.HK 站名與 Citybus P2P 詳情 HTML 的站名格式可能不同。→ Mitigation：明確將卡片信息定義為“預覽”，完整核對仍以底部詳情為準。
- [Risk] 多段路線的末程下車站推導依賴完整 rawInfo 解析，髒資料會導致預覽缺失。→ Mitigation：解析失敗只隱藏預覽，不影響路線卡片主信息。
- [Risk] 加入 `rawInfo` 去重可能增加卡片數量。→ Mitigation：這些差異對用戶可見，保留更符合預覽語義；仍可通過排序和摘要控制列表可掃讀。
- [Risk] route-stop/stop API 暫時失敗會造成部分卡片沒有預覽。→ Mitigation：失敗不緩存，後續重新查詢或同進程重試可恢復。
- [Risk] `route-stop` resolver 與 ETA 現有服務重構可能影響候車時間。→ Mitigation：保持現有 ETA URL、過濾與緩存語義不變，新增測試保護 ETA 行為。

## Open Questions

無。已確認：新開獨立 change；卡片單行展示 `上車 A站  →  下車 B站`；使用 DATA.GOV.HK；多段取首程上車與末程下車；進程內 1 天緩存；列表先展示、預覽後台補全；請求聚合與並發限制；失敗隱藏預覽；去重納入可見站點差異；本 change 自己先補齊完整 P2P legs 解析模型。
