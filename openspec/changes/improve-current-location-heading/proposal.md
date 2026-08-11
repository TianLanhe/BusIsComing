## Why

路線詳情目前直接使用 Google Maps 原生 My Location 圖層；該圖層在靜止時顯示圓點、移動時才以行進 bearing 顯示箭頭，無法表達使用者期望的「手機頂部朝向」。這會造成方向箭頭時有時無，亦會把移動方向誤認為手機朝向，因此需要改為可驗證、具誤差資訊的設備朝向來源。

## What Changes

- 以 App 自有目前位置標記取代 Google Maps 原生藍點，使用實心方向箭頭持續表達手機頂部朝向，並保留位置精度範圍。
- 前台期間以 Fused Location 持續更新位置，並以 Fused Orientation Provider 的設備 heading 更新箭頭；不以行進 bearing、假設朝北或圓點作方向兜底。
- 點擊「我的位置」仍只把目前位置移入可見區域，不啟用相機持續跟隨；離開前台後停止位置與方向更新，不申請背景定位或保存軌跡。
- 收到首個有效朝向前不偽造方向；當方向供應者明確報告完全不可信時，提供一次可恢復的校準提示，方向恢復後自動清除提示。
- 增加方向事件新鮮度、環形角度、生命週期、錯誤與恢復測試，以及三語 runtime 文案。

非目標包括自行實作原始加速度計／磁力計融合、以特定廠商裝置為前提調參、背景方向追蹤，以及改變既有地圖相機或路線選擇語義。

## Capabilities

### New Capabilities

無。

### Modified Capabilities

- `route-detail-google-map`: 將目前位置由 Google 原生藍點改為 App 自有、以前台連續位置和設備朝向驅動的方向標記，並定義方向不可用、校準、生命週期與相機控制行為。

## Impact

- 受影響代碼集中於 `data/location` 的前台位置／方向協調、`ui/main` 的路線詳情地圖渲染與生命週期，以及三語字串資源。
- 修改既有 `route-detail-google-map` 對外契約；不改變查詢起終點、路線資料、相機不跟隨、權限請求時機或資料保存格式。
- 沿用目前 `play-services-location 21.3.0` 的 Fused Location 與 Fused Orientation Provider，不新增第三方依賴；Google Play services／方向供應者失敗須回報結構化狀態並可在前台恢復。
- 位置與方向只在詳情頁前台且權限／系統定位可用時訂閱；不記錄位置軌跡、方向樣本或其他敏感資料。
- 以純邏輯單元測試、依賴注入的生命週期／錯誤測試、地圖 instrumentation 測試、OpenSpec 嚴格驗證與 Android 完整構建驗證；未連接實體裝置時不宣稱完成物理羅盤精度量測。
