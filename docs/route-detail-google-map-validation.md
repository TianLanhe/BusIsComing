# 路線詳情 Google 地圖驗證記錄

## 驗證環境

- 日期：2026-08-04
- 任務專用裝置：`Pixel_8` AVD，Android 17（API 37.1）、Google Play 映像；驗證前確認沒有接管其他任務的裝置，驗證完成後關閉。
- 畫面基準：1080 × 2400、480 dpi（約 360dp 寬），font scale 1.0／1.3／2.0。
- 網絡：模擬器使用任務本機代理連接 Google 與 Citybus；代理只屬驗證環境，不進入 App 配置或提交內容。
- Maps key：`GOOGLE_MAPS_API_KEY` 只從未追蹤的 `local.properties` 注入 packaged manifest；記錄、截圖、測試輸出及 git diff 均不包含 key 值。

## 真實 Google 底圖與 Citybus 路線

門控 instrumentation 測試 `RouteDetailRealServiceInstrumentedTest` 直接使用生產 repository 及無 Cookie／session 的最小 Citybus 請求，沒有以 fixture 取代網絡資料：

- 單段 `780-CEF-1`（6 → 17）成功載入 Google 香港底圖、1 條道路幾何、起終點、每個上下車／途經站 marker 及至少 2 條灰色步行示意線。
- 多段 `82X-ISR-1`（6 → 9）接 `102-MEF-1`（12 → 15）成功載入 Google 底圖、2 條道路幾何、轉乘點、兩段所有上下車／途經站 marker 及至少 2 條步行示意線。
- 測試等待 Maps SDK 的 `OnMapLoadedCallback`，並要求 Google watermark 可見；每條巴士 polyline 均須有多於 2 個道路坐標，marker 的 `timelineStopIds` 去重後須與文字詳情的全部站點數完全相等。
- 真實多段端點曾在一輪驗證窗口短暫沒有產生幾何；同一最小 URL 隨即分別返回 11,427 與 1,240 bytes 有效坐標，原樣重跑及最終雙案例重跑均通過，未放寬斷言或改用 fixture。

最終測試保存並逐張目視檢查：

- `route-map-real-single.png`：SHA-256 `cd0a8783d0203616c2439045434f873875baa51e85c3f5dc4248a4a3f1f4049f`
- `route-map-real-multi.png`：SHA-256 `5b4663aac1b35aa4e8376583e1d3711e53d85d07899789bbfebc283957149ffc`

兩張圖均確認真實 Google 道路／地名瓦片、Google attribution、巴士道路線、站點、轉乘與摘要半屏沒有互相遮擋。App 對 Google Play Services 不可用、Map 初始化失敗或底圖 15 秒仍未完成載入的全屏文字詳情降級亦由獨立 instrumentation 測試覆蓋。

## 位置、ETA、局部重試與生命週期

- `RouteDetailLocationPermissionInstrumentedTest` 透過真實 Android PermissionController 驗證頁面不會自動索取位置；點擊定位後可授予精確位置，注入 `22.3193, 114.1694` 的 GPS 位置後相機及藍點定位成功。
- 同一測試套件驗證首次拒絕、再次／永久拒絕後前往設定、已授權但系統定位關閉時的恢復入口；所有情況都保留可用的路線詳情頁。測試後恢復系統定位狀態。
- `RouteDetailActivityTest#etaRefreshesAgainAfterSixtySecondsWhilePageStaysInForeground` 在不縮短生產間隔的情況下保持頁面前台超過 60 秒，確認 ETA 正好執行下一輪刷新。
- 既有及新增 Activity instrumentation 覆蓋進入後台停止 ETA、返回前台按資料年齡刷新、Activity 重建、舊 generation 作廢、任何半屏檔位直接返回，以及 MapView `onStart`／`onResume`／`onPause`／`onStop`／`onDestroy` 轉發。
- `partialGeometryRetryKeepsSuccessfulSegmentAndReloadsTheFailedSegment` 驗證兩段路線只重試失敗的 `102-MEF-1`，成功的 `82X-ISR-1` stable line 保留且不重抓；重試後恢復兩條巴士道路線。

## 三語、主題、字體與 TalkBack

- `RouteDetailVisualMatrixInstrumentedTest` 逐一通過香港繁體／簡體／英文 × 淺色／深色 × font scale 1.0／1.3／2.0；每個組合均檢查摘要及全屏，共 36 個頁面狀態。
- 每個狀態驗證 360dp 寬、48dp 觸控目標、長方向／站名／目的地沒有 ellipsis、Google watermark 位於摘要半屏上方，定位與全覽控件不與圖例重疊。
- 目視檢查在 font scale 2.0 發現圖例會遮擋地圖控件；修正為只有實際相交時才把控件移到圖例下方，加入矩形不相交斷言後重新跑完 1.0／1.3／2.0 矩陣。修正後另保存 12 張 2.0 截圖並檢查英文淺色摘要／全屏與繁體深色摘要。
- 實際啟用 `com.google.android.marvin.talkback/.TalkBackService` 後，`RouteDetailTalkBackInstrumentedTest` 驗證返回、半屏把手、定位、全覽均可聚焦並提供 click action；地圖有說明，摘要不依賴地圖，無障礙把手可展開全屏且文字時間線可讀出上／下車站。驗證後停用 TalkBack 並確認 touch exploration 與 enabled service 已恢復。

## 自動化結果與結論

- `RouteDetailActivityTest` 常規套件通過；60 秒門控案例另以真實時間單獨通過。
- 真實 Citybus／Google：單段與多段 2 個案例最終同輪通過。
- 位置權限 3 個真實系統流程、TalkBack 1 個流程、三組完整視覺矩陣均通過。
- JVM 測試覆蓋 `getlinep2p.php` parser、最小 URL／header、端點距離、一天成功快取、相同請求去重、最多三路並發、局部失敗、取消與 generation 作廢。
- 最終以 `./gradlew build` 覆蓋 Kotlin 編譯、unit tests、lint 及 debug／release assemble；OpenSpec strict validation、git diff／secret 檢查亦須通過後才提交。

本 change 的裝置端與頁面驗收已由本任務完成，沒有保留需要使用者人工補測的項目。
