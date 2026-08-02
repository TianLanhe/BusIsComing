# 路線詳情 Google 地圖驗證記錄

## 驗證環境

- 日期：2026-08-03
- 任務專用裝置：`Pixel_9_API_36_1`，Android 16（API 36.1）
- 畫面基準：360dp 寬，font scale 1.0／1.3／2.0
- Maps key：只驗證 `GOOGLE_MAPS_API_KEY` 已從未追蹤的 `local.properties` 注入 packaged manifest；記錄與測試輸出均不包含 key 值。

## 自動化與真實資料證據

- JVM 測試覆蓋 `getlinep2p.php` parser、最小 URL／header、端點距離、一天成功快取、相同請求去重、最多三路並發、局部失敗、取消與 generation 作廢。
- Instrumentation 覆蓋摘要／半屏／全屏、摘要上滑直達全屏、任何檔位直接返回、重建恢復、marker／時間線聯動、底圖不可用降級、ETA 前後台刷新及純文字詳情回歸。
- 視覺矩陣逐一覆蓋香港繁體／簡體／英文與淺色／深色，font scale 1.0／1.3／2.0 均通過，並檢查長站名與核心文字沒有 ellipsis。1.3 首次執行發現英文幾何失敗 Snackbar 被單行省略，改為最多三行後重跑通過。
- 真實 Citybus 最小請求已驗證 `780-CEF-1` 單段 fixture 可重複解析；門控 smoke test 以 `82X-ISR-1` 接 `102-MEF-1` 驗證兩段真實詳情與道路幾何能組成兩條巴士 polyline presentation。

## 本機 Google 底圖限制

此任務專用模擬器可載入 Google Maps SDK surface 與 attribution，packaged manifest 亦含非空 key，但目前執行環境把 Google 網域解析到不可連線位址，Google Play Services 無法下載底圖 tile。因此本機截圖只可證明 MapView、Google attribution、Citybus marker／polyline 展示模型與 renderer 輸入已建立，不能作為「真實底圖像素已顯示」的驗收證據。

發佈前應在可正常連線 Google、帶 Google Play Services 且 key 已限制到正確 package／SHA-1 的實機或模擬器補做：

- 淺色／深色真實底圖、站點 marker、分段道路 polyline、示意步行線與全覽相機。
- 未授權、一般拒絕、永久拒絕、系統定位關閉及已授權藍點。
- 前台 60 秒 ETA、返回前台刷新、局部重試與生命週期。
- TalkBack 閱讀順序、把手操作、地圖控件語義與 Google attribution 不被遮擋。

頁面已對 Google Play Services 不可用、Map 初始化失敗，以及底圖 15 秒仍未完成載入提供全屏文字詳情降級。
