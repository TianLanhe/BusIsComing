## 1. 正式乘車碼拉起模型與啟動器

- [x] 1.1 新增正式乘車碼候選常量與模型，包含 AlipayHK／支付寶 package、scheme、HTTPS URL，並與既有實驗 `TransitCodeLaunchTargets` 保持隔離。
- [x] 1.2 新增可測試的 package 檢測抽象，能判斷 `hk.alipay.wallet` 與 `com.eg.android.AlipayGphone` 是否已安裝，並在查詢異常時安全降級為未安裝。
- [x] 1.3 新增可測試的 activity 啟動抽象，統一封裝 `Intent.ACTION_VIEW`、`startActivity` 成功、無 handler、`ActivityNotFoundException`、`SecurityException` 和其他可捕獲啟動異常。
- [x] 1.4 實作正式拉起器候選鏈決策：只安裝 AlipayHK、只安裝支付寶、兩者都安裝、兩者都未安裝四種狀態均符合規格順序。
- [x] 1.5 實作正式拉起器兜底規則：僅本地啟動失敗時嘗試下一候選，任何候選成功被 Android 接受後立即停止並不顯示失敗提示。
- [x] 1.6 增加正式失敗 toast 文案 `未能開啟乘車碼，請確認已安裝 AlipayHK 或支付寶。`，並確保全部候選本地啟動失敗時才展示。

## 2. 主頁 UI 與正式入口整合

- [x] 2.1 調整 `activity_main.xml` 頂部佈局，移除 `巴士查詢` 文案，將 `乘車碼` 放在左側、`管理路線` 放在右側。
- [x] 2.2 將 `乘車碼` 與 `管理路線` 調整為一致的主要按鈕視覺，保持可辨識間距、可點擊高度與窄屏／大字體下不重疊。
- [x] 2.3 將 `MainActivity` 的 `乘車碼` 點擊綁定改為正式拉起器，不再打開 `TransitCodeBottomSheet`。
- [x] 2.4 保留既有實驗面板、微信 SDK 回調和診斷代碼，但將主頁正式入口與實驗面板生命週期依賴解耦。
- [x] 2.5 檢查 `AndroidManifest.xml` 的 package visibility，確認 `hk.alipay.wallet` 與 `com.eg.android.AlipayGphone` 查詢聲明仍存在。

## 3. 自動化測試

- [x] 3.1 新增正式候選常量與安裝狀態候選鏈單元測試，覆蓋四種安裝組合與 AlipayHK 優先順序。
- [x] 3.2 新增正式拉起器單元測試，覆蓋 scheme 失敗後 HTTPS 兜底、兩錢包安裝時 AlipayHK 失敗後支付寶兜底、成功即停止、全部失敗 toast 結果。
- [x] 3.3 新增 package 檢測與 activity 啟動異常單元測試，覆蓋無 handler、`ActivityNotFoundException`、`SecurityException`、未知啟動異常和 package 查詢異常。
- [x] 3.4 更新既有乘車碼實驗測試，使底部彈層仍可被直接測試，但不再期待主頁 `乘車碼` 點擊會打開實驗面板。
- [x] 3.5 更新主頁佈局／instrumentation 測試，驗證頂部不顯示 `巴士查詢`，顯示 `乘車碼` 和 `管理路線`，且 `管理路線` 點擊仍打開路線管理頁。
- [x] 3.6 補充主頁點擊 `乘車碼` 的測試注入或測試替身，驗證會調用正式拉起器、不顯示實驗底部彈層，且不改變選中路線、臨時查詢上下文、排序和查詢結果。

## 4. 驗證與人工驗收

- [x] 4.1 執行相關單元測試，至少包含正式乘車碼拉起器測試與既有乘車碼實驗測試。
- [x] 4.2 執行 `./gradlew build`，確認編譯、單元測試、lint 與 assemble 通過。
- [x] 4.3 在模擬器或真機檢查主頁頂部佈局，確認兩個頂部按鈕不重疊、與 `常用路線` 或空狀態不黏連。
- [ ] 4.4 在真機驗收外部錢包跳轉；至少覆蓋 AlipayHK 與支付寶都安裝時優先打開 AlipayHK 並到達乘車碼頁，其他安裝組合按設備條件補充記錄。
  - 本次環境僅有 `Pixel_8_API_36` 模擬器，未安裝 `hk.alipay.wallet` 或 `com.eg.android.AlipayGphone`，外部錢包到頁需真機補驗。
- [x] 4.5 實作完成後更新本 `tasks.md` 勾選狀態，檢查 `git status --short` 與變更範圍，按專案規則提交本 change 實作。
