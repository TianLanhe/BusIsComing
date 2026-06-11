## Context

目前 App 從 `ppsearch_p3.php` 的 `showroutep2p(...)` 解析 P2P `rawInfo`，其中每段 bus leg 包含公司、Citybus 內部 route variant、上車站序、下車站序和方向。例如 `CTB||8X-THR-1||6||20||O`。

既有卡片站點預覽和首程 ETA stopId 推導會先把 `8X-THR-1` 降級為公開路線 `8X`，再查 DATA.GOV.HK `route-stop/CTB/8X/outbound`。這對公開站序與 P2P variant 完全一致的路線可用，但對 `8X-THR-1` 已確認會錯位：公開 `8X/outbound` 的 `seq=20` 是「新都城大廈」，而 Citybus P2P `8X-THR-1` 的 `seq=20` 是「長康街」。

Citybus 前端在每條 `ppsearch_p3.php` 候選路線上同時輸出 `showroutestop('<rawInfo>')`。前端腳本將它轉換為 `showstops2.php?r=<rawInfo>&l=<lang>`，返回的 `addstoponmap(...)` 片段直接包含與 P2P route variant 對齊的 `stop_id`、站序、route variant、方向、站名與坐標。這比 `getp2pstopinroute.php` 更輕，且不需要 `ginfo/lid`。

## Goals / Non-Goals

**Goals:**
- 用 `showstops2.php?r=<rawInfo>&l=<lang>` 建立 P2P stop map，讓卡片站點預覽和首程 ETA stopId 都使用 route variant 對齊的 stop id。
- 保持路線列表首屏先展示，站點與 ETA 在後台漸進補全；前 5 條總耗時最低路線優先補全，剩餘路線後台補全。
- 保持 ETA 查詢使用 DATA.GOV.HK `eta/{company}/{stop_id}/{route}`，並保留嚴格 `seq` 匹配優先、`route + stop + dir` 降級匹配的規則。
- 卡片站點預覽只展示站名，移除「上車」「下車」字樣；失敗、缺資料或解析失敗時隱藏站點行。
- 將現有 `route-stop/{company}/{route}/{directionPath}` stopId 推導方式留檔記錄，用於後續觀察和調試，不作為運行時 fallback。
- 補充 `8X-THR-1 seq=20 -> 長康街`、單程、多程、無 fallback、快取和 UI 截圖驗證。

**Non-Goals:**
- 不改變 `ppsearch_p3.php` 路線查詢參數、`m1` 聚合、去重主策略或默認總耗時排序。
- 不把 DATA.GOV.HK `route-stop` 作為卡片或 ETA stopId 的 fallback。
- 不用 `showstops2` 取代底部詳情的 `getp2pstopinroute.php`；詳情仍按既有接口按需載入。
- 不在站點或 ETA 補全後重新按候車時間排序，除非用戶明確選擇候車時間排序。
- 不新增長期持久化快取；本次仍採用進程內成功快取。

## Decisions

### 決策 1：新增 P2P stop map 作為共享資料源

新增資料層 resolver，按 `rawInfo + lang` 構造：

```text
https://mobile.citybus.com.hk/nwp3/showstops2.php?r=<rawInfo>&l=<lang>
```

解析返回中的每個 `addstoponmap(...)`，生成結構化資料：

```text
P2pStopMap
├─ rawInfo
├─ lang
└─ stops
   ├─ legIndex
   ├─ company
   ├─ routeVariant
   ├─ publicRoute
   ├─ bound
   ├─ seq
   ├─ stopId
   ├─ rawName
   ├─ displayName
   ├─ latitude / longitude
   └─ markerType       // S/E/0 等起終標記，僅作診斷或輔助
```

查找站點時使用 `legIndex + routeVariant + seq`，必要時輔以 `bound`。多段路線中同名或同 stop id 的站點仍按 leg 分開保存，避免換乘段角色互相覆蓋。

替代方案是繼續用 DATA.GOV.HK `route-stop` 或為每張卡片請求 `getp2pstopinroute.php`。前者已被 8X 樣例證明不可靠；後者資料更完整但比卡片預覽需要的資料重，且需要 `ginfo/lid`。

### 決策 2：卡片站點預覽只使用 P2P stop map

卡片上車站取第一段 leg 的 `boardingSeq`，下車站取最後一段 leg 的 `alightingSeq`。兩者都從同一份 P2P stop map 解析：

```text
boarding = stopMap.find(legIndex=0, routeVariant=firstLeg.routeVariant, seq=firstLeg.boardingSeq)
alighting = stopMap.find(legIndex=last, routeVariant=lastLeg.routeVariant, seq=lastLeg.alightingSeq)
```

展示時共用既有站名裁切邏輯，只展示逗號前第一段。UI 文案改為 `A站 → B站` 或等效只含站名的單行樣式，不再展示「上車」「下車」標籤。若 stop map 缺失、解析失敗或任一站點找不到，卡片隱藏站點行。

### 決策 3：首程 ETA stopId 改用 P2P stop map

首程 ETA 不再用 `route-stop/{company}/{route}/{directionPath}` 找 `stop_id`。新流程：

```text
firstLeg from rawInfo
  -> P2P stop map
  -> routeVariant + boardingSeq 找 stopId
  -> eta/{company}/{stopId}/{publicRoute}
  -> 優先 strict seq，否則 route + stop + dir 降級
```

ETA API 仍要求公開路線號，所以 URL 使用 `publicRoute`，但 stopId 來自 P2P route variant。若 P2P stop map 失敗或找不到首程上車站，候車時間顯示 `-`，不回退 DATA.GOV.HK `route-stop`。

### 決策 4：快取、去重與並發

P2P stop map 成功結果按 `rawInfo + lang` 進程內快取 1 天。失敗、空響應、缺少站點或解析失敗不快取。

同一次路線查詢中，多張卡片共享相同 `rawInfo + lang` 時只請求一次 `showstops2`。站點補全和 ETA 查詢分兩段限流：

- `showstops2` 並發上限：2。
- ETA 並發上限：3。
- 先補全排序後前 5 條總耗時最低路線，再補全剩餘路線。

路線列表先展示主信息；站點行初始隱藏，成功後回填。ETA 初始仍可展示查詢中狀態，完成後更新候車時間。後台更新不得改變默認總耗時排序。

### 決策 5：不使用 route-stop 運行時 fallback，但保留文檔

`route-stop/{company}/{route}/{directionPath}` 保留在文檔中作為歷史方案和調試參考，說明它只按公開路線號與方向提供靜態站序，不能表達 `8X-THR-1` 這類內部 route variant。新運行時不使用它推導卡片站點或 ETA stopId，也不在 `showstops2` 失敗時回退到它。

### 決策 6：底部詳情保持既有接口，差異只做診斷

底部詳情仍使用 `getp2pstopinroute.php?info=<rawInfo>&ginfo=<ginfo>&lid=<lid>&l=<lang>`。它返回的是詳情列表資料，展示時以自身解析結果為準。

若實作中同時具備 P2P stop map 與詳情資料，且發現同一 `routeVariant + seq` 的 stopId 不一致，系統記錄診斷資訊，但不向用戶提示，也不讓卡片覆寫詳情內容。原因是兩個接口可能存在短暫資料更新差異；詳情頁展示完整行程時應以詳情接口當次返回為準。

### 決策 7：文檔與測試覆蓋真實錯位案例

`docs/citybus-eta-from-ppsearch.md` 更新為：

- 主流程：`ppsearch_p3.php` -> `showstops2.php` -> P2P stop map -> ETA。
- 留檔：舊 `route-stop` 推導方式和限制。
- 例子：`8X-THR-1` 中 `seq=20` 對應「長康街」，公開 `8X/outbound` 中 `seq=20` 對應「新都城大廈」。

測試使用真實 fixture 覆蓋單程 `8X-THR-1` 和多程樣例，並保留 mock-free 的真機或模擬器截圖驗證作為實作完成後檢查。

## Risks / Trade-offs

- [Risk] `showstops2.php` 是 Citybus 網頁內部接口，HTML/JS 片段格式可能變化。→ Mitigation：解析器集中封裝，使用真實 fixture 覆蓋單程、多程、缺字段和錯位案例；解析失敗只隱藏站點並讓 ETA 不可用。
- [Risk] `showstops2` 與 `getp2pstopinroute` 短時間資料不同步。→ Mitigation：兩者都以 `rawInfo` 為 key；卡片和 ETA 用 stop map，詳情用詳情接口，差異只記錄診斷。
- [Risk] 新增 `showstops2` 請求會增加查詢後網絡量。→ Mitigation：按 `rawInfo + lang` 去重和快取，前 5 條優先、剩餘後台補全，並限制並發。
- [Risk] 停站解析失敗會使部分 ETA 從可用變為不可用。→ Mitigation：這比顯示錯站更安全；失敗不快取，後續查詢可重新嘗試。
- [Risk] 文檔中保留 route-stop 方案可能被誤用。→ Mitigation：明確標註它是歷史/觀察方案，不是新運行時 fallback，並寫入設計風險。
