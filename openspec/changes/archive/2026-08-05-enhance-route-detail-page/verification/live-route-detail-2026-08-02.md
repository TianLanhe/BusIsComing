# Citybus 路線詳情 live A/B 驗證（2026-08-02）

## 方法

- 先以同一組公開測試起終點呼叫 `ppsearch_p3.php`，取得當次有效的 `788` 與 `780` `rawInfo`／`ginfo`／`lid`。
- 對兩個結果分別使用 `l=0/2/1` 呼叫 `getp2pstopinroute.php`。
- 每一組語言各執行兩次：一次攜帶瀏覽器式 `User-Agent`／`Referer`／`X-Requested-With`，一次完全不設定這些 header，共 12 個 live 樣本。
- 記錄 HTTP 狀態、body byte 數、SHA-256、可解析 `p2plistcell`／`stopclick1` 站點數，並比較同組 header A/B 的 body 與站點業務簽名。

## 結果

| 路線 | 語言 | 樣本數 | HTTP | 站點標記 | 無 header body SHA-256 | header A/B |
|---|---:|---:|---:|---:|---|---|
| 788 | 0 | 2 | 200 | 5 | `3fc2eda31d1159154f95837813c9e8b4e5233764a8a14815da638ecfa4934f18` | body、業務簽名一致 |
| 788 | 2 | 2 | 200 | 5 | `d73f795225f1cefb2bc8d33a18525c595dc9a953c38339e925bce93b38bc80bc` | body、業務簽名一致 |
| 788 | 1 | 2 | 200 | 5 | `58b7cf4587d685728e65ec3dc84c5840b27e8f1f007ca8f4da98b0d38b11aa9e` | body、業務簽名一致 |
| 780 | 0 | 2 | 200 | 16 | `71e0d2de1733daa8bf47af1abad6304890ed72654291fd874a94bc0c15386d73` | body、業務簽名一致 |
| 780 | 2 | 2 | 200 | 16 | `a0593cafbf11ccc95a8f1ce5002eb59fbf450f2f0899ef8ae72c6bb50c31840e` | body、業務簽名一致 |
| 780 | 1 | 2 | 200 | 16 | `919eb87faa4a5102b2d8ee1538b9eadb72fba367797b395f76be8eb2b4f319a4` | body、業務簽名一致 |

全部 12 個請求均返回 HTTP 200 並具有可解析站點主結構；清除瀏覽器式 header 前後，各組 body hash 與站點業務簽名完全一致，支持 repository 繼續使用空 header 契約。

本次 live 回應的 `showtimetable1(...)` 第一個 payload 為空，但站點主結構有效。這與本變更的部分資料降級契約一致：站點時間線繼續展示，票價、計劃時間及完整步行合計保持可空，不以其他語言或其他路線補值。N118 與 N8P → N969 已保存 fixture 仍負責回歸非空 timetable 欄位映射。

## 360dp 視覺與互動矩陣

- 使用本任務自行啟動的 `Pixel_9_API_36_1`，設定 `1080 × 2400`、`480 dpi`，即 360dp 寬度。
- `RouteDetailVisualMatrixInstrumentedTest` 分別在 font scale `1.0`、`1.3`、`2.0` 執行；每一輪涵蓋香港繁體、簡體、英文與淺／深色，共 18 種組合。
- 每一組均載入包含長站名、長方向、多段換乘、完整步行資料及逐段展開的扁平時間線，驗證標題語言、主題、無省略號及展開後終點可捲達。
- 三輪均通過；另外保存並人工檢視淺色 1.0 倍及深色 2.0 倍截圖，確認固定標題列、摘要、步行人物、虛線步行段、交替乘車段顏色、展開箭頭與大字換行無裁切。

## Instrumentation 回歸

- `RouteDetailActivityTest` 共 3 項通過：摘要／缺失元數據與重建、失敗重試／展開狀態與首程 ETA、工具列返回。
- `DemoScreenshotInstrumentedTest` 的網站截圖流程通過；另有深色 2.0 倍路線詳情截圖測試通過。
- 另以不納入提交的 temporary live smoke instrumentation 使用 production `CitybusBusRouteRepository`、`CitybusRouteDetailRepository` 與 `CitybusFirstLegEtaService` 驗證。當次真實結果為 `N8P → N680`：詳情頁成功呈現 11 站、兩段乘車及換乘步行，首程 DATA.GOV.HK ETA 完成刷新並顯示「即時 · 還有 9 分鐘」；人工檢視截圖確認真實資料下無錯誤 state 或版面異常。
