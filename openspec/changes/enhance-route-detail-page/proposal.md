## Why

現有路線詳情 Bottom Sheet 只展示基本站點結構，未能把 Citybus 已提供的起終點步行、換乘方式、分段票價及預計時間整理成可直接依序閱讀的完整行動鏈；路線卡片的耗時與步行文字亦缺少清楚的視覺辨識。產品即將加入 Google 地圖，現在需要先把詳情升級為可承載長路線與未來地圖模組的獨立全屏頁。

## What Changes

- **BREAKING**：路線結果卡片點擊後不再開啟 Bottom Sheet，改為進入保留返回路徑的獨立全屏路線詳情頁。
- 路線卡片以已確認的小鬧鐘及高保真步行人物矢量圖示標示耗時與步行距離，但保持 `ppsearch_p3.php` 步行距離及排序邏輯不變。
- 詳情頁以路線摘要與分段時間線展示總價、總耗時、預計到達、總途經站數及完整步行距離。
- 解析並展示 `getp2pstopinroute.php` 中的起點、換乘與終點步行段、換乘類型、分段票價及 Citybus 預計上下車時間。
- 每段巴士使用不同顏色的粗實線；步行使用中性細虛線；路線牌、時間線及未來地圖折線共享分段顏色語義。
- 首程 DATA.GOV.HK ETA 與 Citybus 預計時刻使用不同標籤及視覺層級，後續乘車段不偽造即時 ETA。
- 途經站預設按段折疊，以矢量 Chevron 控制在卡片外原位展開；站名與圓點直接屬於主時間線。
- 提供部分資料降級、載入失敗重試、生命週期過期回應隔離、三語、深淺色及無障礙行為。
- 本次不接入 Google Maps、不加入步行導航、不改變路線卡片步行距離語義、排序或通知欄監控。

## Capabilities

### New Capabilities

- 無。

### Modified Capabilities

- `route-detail-bottom-sheet`：沿用歷史能力 ID，將詳情容器由 Bottom Sheet 改為全屏頁，並擴充完整步行、換乘、分段時間／票價、摘要、時間線、折疊、錯誤與生命週期要求。
- `route-query-results-layout`：將路線卡片耗時與步行標籤改為已確認的矢量圖示，同時保留既有數值來源、排序與無障礙語義。
- `citybus-first-leg-eta`：在全屏路線詳情頁展示首程即時 ETA，並明確區分即時 ETA、Citybus 預計時刻、暫無班次及技術故障。

## Impact

- 影響 `ui/main` 的路線卡片 Adapter、`MainActivity`／`SearchFragment` 詳情入口及現有 `RouteDetailBottomSheet`，並新增全屏詳情 Activity、XML／RecyclerView item、Drawable 及三語資源。
- 擴充 `data/model`、`data/repository/CitybusRouteDetailParser`、`CitybusRouteDetailRepository` 與詳情快取；生產資料仍來自 `getp2pstopinroute.php`，首程即時 ETA 仍來自 DATA.GOV.HK。
- `getp2pstopinroute.php` 請求參數與不攜帶靜態瀏覽器 header 的既有要求保持不變；新增解析假設需以單程、步行換乘、同站換乘與缺失欄位 fixture 固化。
- 路線卡片的 `ppsearch_p3.php` 步行距離、排序、查詢流程及結果列表資料格式保持相容；詳情頁可使用更完整的分段步行合計。
- 需要更新現有 `route-detail-bottom-sheet`、`route-query-results-layout` 與 `citybus-first-leg-eta` 規格，並新增 parser／formatter 單元測試、全屏頁 instrumentation 測試及三語×明暗×大字體人工驗收。
