## 1. 啟動目標與錯誤處理

- [x] 1.1 新增乘車碼候選入口模型，包含 provider、標題、說明與 URI/URL，並固定定義微信 3 條與支付寶 4 條入口。
- [x] 1.2 實作支付寶 `ds.alipay.com` 包裝入口的 URL encode 構造，確保內層 `alipays://platformapi/startapp?appId=200011235` 被正確編碼。
- [x] 1.3 新增輕量啟動器封裝 `Intent.ACTION_VIEW` 與 `startActivity`，將成功、`ActivityNotFoundException`、`SecurityException` 和非預期異常轉為可測試結果。
- [x] 1.4 定義微信、支付寶與通用失敗 toast 文案，確保外部入口無法打開時 App 不崩潰。

## 2. 主頁入口與底部彈層

- [x] 2.1 在 `activity_main.xml` 主頁頂部標題區新增低干擾「乘車碼」入口，保留既有「巴士查詢」標題與「管理路線」入口。
- [x] 2.2 在 `MainActivity.setupActions()` 綁定「乘車碼」點擊，顯示底部彈層且不改變目前路線選擇、臨時查詢上下文、排序狀態或結果列表。
- [x] 2.3 實作底部彈層微信/支付寶分組列表，展示每條候選入口的標題與短說明。
- [x] 2.4 綁定每條候選入口點擊行為，每次只嘗試當前入口，不自動 fallback，失敗時保留或可立即返回彈層以便嘗試其他入口。

## 3. Android manifest 相容性

- [x] 3.1 在既有 `<queries>` 節點中合併 `com.tencent.mm` package query，不建立重複 `<queries>` 節點。
- [x] 3.2 在既有 `<queries>` 節點中合併 `com.eg.android.AlipayGphone` package query，不影響既有 TTS service query。

## 4. 自動化測試

- [x] 4.1 新增 URI/URL 構造單元測試，覆蓋微信 3 條、支付寶 4 條與支付寶 ds 包裝 URL encode。
- [x] 4.2 新增啟動器單元測試或可測試注入點，覆蓋啟動成功、`ActivityNotFoundException`、`SecurityException` 與通用異常結果。
- [x] 4.3 新增或更新 instrumentation/UI 測試，驗證點擊主頁「乘車碼」會顯示底部彈層。
- [x] 4.4 新增或更新 instrumentation/UI 測試，驗證底部彈層包含微信 3 條與支付寶 4 條入口。
- [x] 4.5 驗證打開和關閉乘車碼彈層不會改變既有常用路線選擇、臨時查詢上下文、排序狀態或已展示結果。

## 5. 人工真機驗證與收斂

- [x] 5.1 準備人工真機驗證記錄，逐條記錄入口名稱、設備/Android 版本、微信/支付寶版本、結果、到達頁面與備註。
- [ ] 5.2 在可安裝微信的 Android 設備上驗證 3 條微信候選入口，記錄是否打開微信、小程序首頁、乘車碼頁、無反應或報錯。
- [ ] 5.3 在可安裝支付寶的 Android 設備上驗證 4 條支付寶候選入口，記錄是否打開支付寶、小程序/乘車碼頁、H5 中轉、無反應或報錯。
- [ ] 5.4 檢查主頁入口和底部彈層在常見螢幕寬度、字體縮放和無障礙讀取下不遮擋、不截斷且觸控目標可用。

## 6. 最終驗證

- [x] 6.1 執行相關單元測試與 instrumentation 測試，確認新增入口、URI 構造、彈層展示與失敗處理通過。
- [x] 6.2 執行 `./gradlew build`。
- [x] 6.3 檢查 `git status --short`、測試結果、人工驗證結果與任務勾選狀態，確認提交範圍只包含本 change。
