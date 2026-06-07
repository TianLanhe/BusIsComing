## Why

目前路線卡片站點預覽與首程 ETA 的 `stop_id` 透過 DATA.GOV.HK `route-stop/{company}/{route}/{directionPath}` 推導；該接口只按公開路線號與方向提供靜態站序，無法區分 Citybus P2P 內部 route variant。真實樣例 `8X-THR-1` 已證明公開 `8X/outbound` 的 `seq=20` 對應「新都城大廈」，但 P2P variant 的 `seq=20` 實際是「長康街」，導致卡片下車點與詳情不一致。

需要改用 Citybus P2P rawInfo 對齊的停站資料，讓卡片站點、首程 ETA stopId 與點到點詳情基於同一套 route variant 與 station seq 語義。

## What Changes

- 新增 Citybus P2P stop map 能力：對每條候選路線使用 `showstops2.php?r=<rawInfo>&l=<lang>` 解析 `addstoponmap(...)`，取得每段路線的 `stop_id`、站序、route variant、方向、站名、經緯度與起終標記。
- 路線卡片站點預覽改用 P2P stop map，而不是 DATA.GOV.HK `route-stop` 和 `stop` 接口；站點預覽成功時只展示首程上車站名與末程下車站名，不展示「上車」「下車」標籤。
- 首程 ETA 的 `stop_id` 改用 P2P stop map 中首程上車站推導；ETA API 仍使用 DATA.GOV.HK `eta/{company}/{stop_id}/{route}` 查詢即時候車時間。
- 保留既有 ETA 過濾語義：優先使用 `route + stop + dir + seq` 嚴格匹配；嚴格匹配無結果時，使用 `route + stop + dir` 匹配的最近 ETA。
- P2P stop map 成功結果按 `rawInfo + lang` 進程內緩存 1 天；失敗、空響應或解析失敗不緩存，且不回退到 DATA.GOV.HK `route-stop`。
- 保留現有 `route-stop/{company}/{route}/{directionPath}` 推導方式的文檔記錄，作為歷史方案、觀察和調試參考，不作為新運行時 fallback。
- 更新 `docs/citybus-eta-from-ppsearch.md`，將 `showstops2` 路徑設為主方案，並說明公開 `route-stop` 在 route variant 場景下的限制。
- 新增 `8X-THR-1 seq=20 -> 長康街` 回歸測試，以及 `showstops2` 單程與多程 fixture；實作後需要使用當前已恢復的真實網絡做模擬器或真機截圖驗證。

## Capabilities

### New Capabilities
- `citybus-p2p-stop-map`: 從 Citybus P2P rawInfo 查詢並解析與 route variant 對齊的停站映射，供卡片站點與首程 ETA stopId 共用。

### Modified Capabilities
- `route-card-stop-preview`: 卡片站點預覽的資料源由 DATA.GOV.HK `route-stop`/`stop` 改為 Citybus P2P stop map，並調整卡片文案只顯示站名。
- `citybus-first-leg-eta`: 首程 ETA 的 `stop_id` 推導由公開 route-stop 改為 Citybus P2P stop map，同時保留既有 ETA 記錄匹配降級規則。

## Impact

- 影響資料層：新增 `showstops2.php` URL 構造、HTML/JS 片段解析、P2P stop map 模型、1 天成功緩存與失敗不緩存策略。
- 影響 `CitybusFirstLegEtaService` 或其上游 resolver：首程 ETA 查詢前先以 `rawInfo + lang` 取得 P2P stop map，再定位首程上車站 `stop_id`。
- 影響 `RouteCardStopPreviewResolver`：不再透過 DATA.GOV.HK `route-stop` 和 `stop` 查站名，改用 P2P stop map 解析結果。
- 影響主頁 UI：卡片站點預覽行保留漸進補全與失敗隱藏，但展示文案移除「上車」「下車」字樣。
- 影響文檔：`docs/citybus-eta-from-ppsearch.md` 需要更新主流程並留檔記錄舊 route-stop 方案。
- 影響測試與驗證：需要新增 parser、cache、ETA stopId、卡片文案、無 fallback、8X 回歸、多程 fixture、真機或模擬器截圖驗證。
