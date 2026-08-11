## 1. 狀態模型與測試基線

- [x] 1.1 閱讀現有位置、地圖 renderer、Activity runtime seam 與測試工具，建立只涵蓋本 change 的檔案範圍
- [x] 1.2 先加入會因功能缺失而失敗的純單元測試，覆蓋位置／方向組合、事件時間順序、generation、完全未知到恢復及每區間一次校準提示
- [x] 1.3 先加入 `359° → 1°` 等環形最短旋轉測試並確認以缺少實作的預期原因失敗

## 2. 前台位置與方向來源

- [x] 2.1 實作可注入的前台位置／方向來源接口與結構化狀態，使 1.2 測試通過
- [x] 2.2 以 Fused Location 連續更新位置，以 Fused Orientation Provider 作唯一手機朝向來源，處理請求失敗、事件新鮮度、取消與 generation
- [x] 2.3 加入來源生命週期測試，驗證 stop 後移除 callback、晚到結果失效且不保存位置或方向樣本

## 3. 地圖方向標記與頁面整合

- [x] 3.1 先加入 renderer／頁面測試，驗證自有實心方向標、精度範圍、首個 heading 前不偽造方向、清理冪等及相機不跟隨
- [x] 3.2 在 `GoogleRouteMapRenderer` 停用原生 My Location 呈現，實作 App 自有方向 marker、精度 circle 與最短環形旋轉更新
- [x] 3.3 在 `RouteDetailActivity` 接入前台 start／stop、最新位置點擊居中、過期 callback 防護、方向不可用及校準恢復狀態
- [x] 3.4 為校準與方向不可用狀態補齊香港繁體、獨立簡體及自然英文資源與可訪問語義
- [x] 3.5 加入 resumed／地圖可用性 gate、前台系統定位模式監聽、方向流活性逾時及 display-frame 合併，並把方向請求調整為 compass／navigation 建議頻率

## 4. 回歸與驗證

- [x] 4.1 補齊／更新 instrumentation 測試，覆蓋權限、系統定位、靜止 heading 驅動、前後台清理及點擊只居中一次
- [x] 4.2 運行受影響單元測試與 instrumentation 編譯；如有符合畫像且由本任務啟動的 API 36 Google 裝置，執行裝置測試後主動關閉
- [x] 4.3 運行 `openspec validate improve-current-location-heading --strict`、`./gradlew build`，檢查沒有原生 My Location 或行進 bearing 回退
- [x] 4.4 運行地圖不可用、pause 晚到工作及 resumed 期間系統定位切換的 instrumentation 回歸，重新完成 unit、lint、OpenSpec 與完整 build 驗證
- [x] 4.5 檢查 tasks、工作區、差異與 staged 範圍，記錄未執行的物理方向精度驗證並按倉庫規則提交

## 驗證記錄

- API 36.1、1080 × 2400、480dpi、Google Play 任務專用 AVD 上的 5 個方向／生命週期 instrumentation 測試已通過，測試完成後已關閉該 AVD。
- 自動化以注入的固定位置與 heading 驗證資料語義、旋轉、清理、恢復及相機所有權；未執行實體裝置的絕對方位角誤差量測，亦不據此宣稱物理羅盤精度已驗證。
