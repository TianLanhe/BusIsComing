## Context

`GoogleRouteMapRenderer` 目前只切換 Google Maps 原生 My Location 圖層，`RouteDetailActivity` 另以 `CurrentLocationCoordinator.getCurrentLocation()` 取得一次性位置供相機居中。原生圖層的可觀察契約是靜止時顯示圓點、移動時顯示 chevron；Android `Location.bearing` 則是行進方向而非設備朝向。倉庫沒有使用 `SensorManager`、rotation vector、heading 或其他設備姿態接口，因此現況不能滿足「手機頂部朝向」語義。

專案已依賴 `play-services-location 21.3.0`。該版本提供 `FusedOrientationProviderClient`，其 `DeviceOrientation` 直接回傳依螢幕旋轉解讀的手機頂部 heading、單調時間戳及方向誤差；這比 App 自行融合加速度計、陀螺儀和磁力計更符合本次範圍，亦能避免未經實機證明的廠商特定參數。

## Goals / Non-Goals

**Goals:**

- 目前位置方向標在使用者靜止及移動時均表示手機頂部朝向，而非行進 bearing。
- 位置、方向、精度範圍和校準狀態有單一、可測試的前台生命週期，過期 callback 不得恢復已停止的地圖狀態。
- 保持既有權限時機、相機只在點擊時居中、相機不持續跟隨、無背景定位及不保存軌跡等契約。
- 使用供應者回報的方向誤差驅動校準提示，不以錯誤語義的資料兜底。

**Non-Goals:**

- 不自行實作原始感測器融合或為 Xiaomi／其他廠商加入專用調參。
- 不保證在強磁場干擾或供應者宣告完全未知時仍產生物理正確方向。
- 不改變路線幾何、站點、ETA、選擇、相機全覽或查詢起終點。
- 不增加背景位置／方向權限、資料保存或遙測。

## Decisions

### 1. 以 Fused Orientation Provider 作為唯一方向來源

在 `data/location` 建立可注入來源的前台位置／方向協調器。生產方向來源透過 `FusedOrientationProviderClient.requestOrientationUpdates` 訂閱 `DeviceOrientation`；只接受有限且位於 `[0, 360)` 的 heading，並以 elapsed realtime 拒絕較舊事件。UI 不讀取 `Location.bearing`，也不自行讀取 `SensorManager`。

選擇理由是 Fused Orientation 的資料語義直接對應手機頂部朝向，且同時提供 heading error。被否決方案包括：保留原生 My Location（靜止時沒有箭頭且語義錯誤）、只提高 Location 更新頻率（仍是行進方向），以及自行融合原始感測器（需要裝置矩陣與長期實測，超出本次證據範圍）。

### 2. 以前台連續位置和方向組合成結構化狀態

位置來源使用 `FusedLocationProviderClient.requestLocationUpdates`，僅在詳情頁已 resumed、位置權限已授予、系統定位開啟且地圖 renderer 可用時啟動。Activity 在 resumed 期間監聽系統定位模式變更：關閉時立即停止並清除圖層，重新開啟時按相同 eligibility 重啟；地圖明確不可用時不維持高精度位置或方向訂閱。`onPause` 先撤銷 resumed eligibility 再停止，避免晚到的地圖 callback 在 pause 與 stop 之間重啟訂閱。

協調器分別保留最新位置與方向，輸出位置精度、heading、方向誤差／校準需要、事件時間及錯誤狀態。每次 start 建立 generation；stop 先遞增 generation，再移除兩種 callback，晚到結果不得更新 UI。方向訂閱啟動時即建立一次 2 秒 generation-bound 活性計時，每個有效方向事件再重設計時；若首個事件未到或供應者沒有回報顯式失敗但方向流停止，計時到期即清除舊箭頭並進入方向不可用狀態，下一個較新的有效事件可自動恢復。

方向可先於位置到達，反之亦然：只有有效位置才能繪製精度範圍；只有位置與首個有效 heading 同時存在才能繪製方向箭頭。這段短暫等待不偽造朝北方向，也不以圓點冒充方向。

既有一次性 `CurrentLocationCoordinator` 保留作為點擊居中的補充來源；若連續追蹤已有新鮮位置，Activity 直接使用該位置，否則才發起現有一次性請求。相機仍不訂閱後續位置。

### 3. 由 App 繪製實心方向標及位置精度範圍

停用 Google 原生 My Location 圖層，避免同一位置出現兩套互相矛盾的符號。`GoogleRouteMapRenderer` 管理一個扁平、以地圖北方為零度的實心方向 marker，以及一個不可點擊的位置精度 circle。位置變更移動 marker／circle，heading 變更沿最短環形角度更新旋轉；只做短時視覺插值，不對供應者資料再作統計濾波。

位置或權限失效、系統定位關閉、頁面離開前台、地圖不可用或 renderer 清理時，立即移除自有位置圖層。其餘路線 marker、polyline、選擇和相機狀態不受影響。

方向來源採用 `DeviceOrientationRequest.OUTPUT_PERIOD_DEFAULT`；依賴版本的官方 Javadoc 將其定義為 50Hz／20ms 並以 compass 或 navigation app 為例，不使用面向 AR 微調的 200Hz `OUTPUT_PERIOD_FAST`。Activity 把可能更快到達的狀態合併到下一個 display frame，renderer 重用單一旋轉 animator，避免每個供應者事件都建立新 animator，同時不對 heading 作統計濾波。

### 4. 校準是恢復路徑，不是方向兜底

有效 heading 到達後，箭頭持續由最新設備 heading 驅動。當供應者的保守方向誤差不可用或為 `180°`（完全未知）時，每個連續低可信區間最多顯示一次可本地化校準提示；供應者重新回報可信誤差後自動清除提示並重置下一區間的提示資格。供應者請求失敗則呈現方向不可用狀態，不改用行進 bearing、固定北向或 Google 原生圓點。

提示使用既有頁面訊息／Snackbar 入口，三語資源獨立表達；不在 log、偏好或持久化資料中記錄位置與方向樣本。

### 5. 測試按資料語義和生命週期分層

- 純單元測試驗證 heading 範圍、`359° → 1°` 最短角度、較舊事件、位置／方向組合、完全未知到恢復，以及每段低可信區間只提示一次。
- 協調器測試以 fake source 驗證 start／stop、generation、訂閱失敗、權限或系統定位關閉後停止。
- 地圖／Activity instrumentation 測試透過既有 runtime 注入 seam 驗證自有 marker、精度範圍、點擊只居中一次、前後台清理及三語提示；自動化不宣稱量測物理羅盤精度。
- 最終運行定向測試、`openspec validate --strict` 與 `./gradlew build`。未連接任務自有合適裝置時，明確標註未完成真機物理方向驗證。

## Risks / Trade-offs

- [Fused Orientation 仍受局部磁場或未校準磁力計影響] → 使用供應者誤差模型識別完全未知狀態並引導校準，不以另一個錯誤語義的來源掩蓋。
- [自繪位置圖層失去 Google 原生藍點的內建視覺] → 同時繪製位置精度範圍並沿用 Material／Google Maps 可辨識的藍色實心方向語言。
- [同時訂閱位置與方向增加前台耗電] → 僅在詳情頁前台訂閱，採用有界更新週期並在 pause／destroy 立即移除。
- [方向供應者可能不回報中途失敗] → 以每個有效事件續期的 2 秒活性計時清除過期箭頭；恢復後仍只接受新的 Fused Orientation heading。
- [位置與方向 callback 競態導致頁面退出後重現 marker] → 使用 generation 和 elapsed realtime 雙重防護，renderer 清理保持冪等。
- [沒有實體裝置便無法證明絕對角度誤差] → 自動化只驗證語義、資料流與狀態機；不把未做的物理精度量測寫成已通過。

## Migration Plan

1. 先加入純模型、來源接口與失敗測試，再實作 Fused 來源。
2. 加入自有方向 marker／精度 circle，切換 Activity 生命週期並停用原生 My Location。
3. 補齊三語資源、instrumentation 與回歸測試，完成完整構建。
4. 回退時可移除自有協調器與 marker，恢復單一 `isMyLocationEnabled` 呼叫；沒有資料 schema 或使用者資料需要遷移。

## Open Questions

無；方向來源、校準行為、前台生命週期及不使用行進方向兜底已與使用者確認。
