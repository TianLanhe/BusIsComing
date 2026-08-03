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

### Citybus 舊底圖坐標校正與 N118 高縮放回歸

使用者回報的 N118 畫面顯示 `getlinep2p.php` 路線相對 Google 道路整體偏向東南。Citybus mobile 網頁現行腳本以 `alat=-0.0001935197`、`alon=0.0000697374` 對齊自家舊底圖，而路線 endpoint 直接輸出該舊底圖坐標；因此不能只把經度任意上調。repository 現在於 parser 之後、端點驗證與成功快取之前反向套用：

```text
googleLatitude  = citybusLatitude  + 0.0001935197
googleLongitude = citybusLongitude - 0.0000697374
```

這相當於在香港約向北 21.5 米、向西 7.2 米，合計約 22.7 米。校正只限 `getlinep2p.php` 路線幾何；Citybus 站點、查詢端點、設備位置與 Google 資料維持原值，renderer 不含 provider-specific 位移。

- 新增 2026-08-04 真實最小請求 `N118-TOS-1`（5 → 9）57 點 fixture；parser 測試保留原始坐標，repository 測試固定首尾校正值、point id／次序、點數與 cache hit 不重複位移。
- `RouteDetailRealServiceInstrumentedTest#realN118GeometryAlignsWithGoogleRoadAtHighZoom` 直接使用生產 repository 與真實 Citybus／Google，在裝置端斷言首點為 `22.264897461791,114.24161529313`、末點為 `22.262470011791,114.23424341313`。
- 以 zoom 18.5 聚焦柴灣道／環翠道後保存 `route-map-real-n118-high-zoom.png`，SHA-256 `322d237f439b3be53496b7d91db9f83e7d694f0621bb4b7608b13181584cbd70`；目視確認巴士線沿對應行車帶連續繪製，不再出現原畫面的整體平移。
- 同一任務模擬器同輪重跑 N118、單段 `780-CEF-1` 與多段 `82X-ISR-1 → 102-MEF-1` 共 3 個真實案例，全部通過；各案例仍驗證 Google watermark、所有時間線站點、道路幾何與至少兩條示意步行。
- `RouteDetailActivityTest` 路線詳情核心套件通過，門控的 60 秒 ETA 案例另以完整 60 秒生產間隔單獨通過；`RouteDetailVisualMatrixInstrumentedTest` 亦分別以正確 runner 參數完成 font scale 1.0／1.3／2.0，每檔均覆蓋三語 × 明暗共 6 個畫面狀態。
- 首輪裝置測試因新啟動 AVD 停在鎖屏而令 Activity 進入 paused 狀態，診斷截圖與 lifecycle 證據確認後解鎖；未修改產品超時或放寬斷言，原樣重跑通過。

全量 `connectedDebugAndroidTest` 裸跑亦用作額外診斷，但不是本專案所有 instrumentation 的有效單一入口：三個視覺矩陣類別各自要求 runner 參數，無參數時會按設計在 `requireNotNull` 中失敗；Google Play AVD 亦不符合 `noPlayDevice...` 的無 Play 前置條件。另發現兩組與本次 geometry 無關的舊測試夾具：refresh fixture 未先更新 `routeQueryState`，search fixture 仍等待已由獨立 `RouteDetailActivity` 取代的舊詳情 Dialog。這些既有測試債沒有以放寬本次驗收或擴張坐標修正範圍處理；本次涉及的 JVM、route detail、真實服務及正確參數矩陣均獨立重跑通過。

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
- JVM 測試覆蓋 `getlinep2p.php` parser、Citybus 舊底圖坐標校正、最小 URL／header、端點距離、一天成功快取、相同請求去重、最多三路並發、局部失敗、取消與 generation 作廢。
- 最終以 `./gradlew build` 覆蓋 Kotlin 編譯、unit tests、lint 及 debug／release assemble；OpenSpec strict validation、git diff／secret 檢查亦須通過後才提交。

本 change 的裝置端與頁面驗收已由本任務完成，沒有保留需要使用者人工補測的項目。
