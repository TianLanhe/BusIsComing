# 實作與驗證記錄

## 範圍與基線

- 產品代碼基線：`c8143fe`；實作起點：`13005a2`，工作樹乾淨。
- 正常 GMS／Play 分支保留原 Fused 來源、Google Map renderer、自有方向圖層、相機所有權、Google HTTP、系統 TTS、通知監控、Play 結果 mapper 與提醒政策。
- 文字定位原 Fused 參數仍為 balanced／10 秒／20 米；方向來源原 high accuracy／1 秒、最短 500 ms 及方向政策未修改。
- 正常一次性 Fused 的 `lastLocation` 等候原本沒有獨立截止時間，取得新 fix 才啟動 3 秒逾時。本次按正常行為不變的約束保留，未藉後備改動擅自修改此既有偏差。無 GMS broker 則從請求開始計 3 秒，原地址 3 秒及整體 5 秒不變。
- `add-route-detail-current-position`、`improve-current-location-heading`、`align-settings-route-detail-polish` 的已實作行為聯集保留；未同步／歸檔主 spec，未回退原生藍點。

## 根因證據與發佈界線

使用者確認 Play 商店安裝／更新，網站亦分發 Play 簽署包；提供畫面顯示 installer check 開啟。倉庫沒有該英文啟動彈窗或主動 Integrity／licensing 校驗。這支持「Play 自動保護在受限制環境攔截啟動」的假設，但沒有故障版本 APK／logcat 或受影響手機對照，因此仍是待核實根因。

此次沒有更改 Play Console、簽名、application ID、versionCode 或網站產物，也沒有發佈。第 7.1–7.4 項需要實際 Console／正式候選簽署包／更新資格／超級省電手機證據，本地成功不能替代。

## 實作

- GMS 與 Play Store 獨立探測；未知／更新中不轉系統後備，Store `UNUSABLE`／探測例外不轉官網。
- 正常來源延遲建立；Play check、listener、resume、complete 和 action 都有能力及世代保護。明確缺失／停用商店才允許所有初始渠道走原網站流程。
- `SystemLocationRequestBroker` 合併需求、3 秒截止、30 秒快照、consumer 取消、前後台及舊 request 隔離。系統 listener 在完成／取消／逾時／離開前台時釋放；只有新增後備受 `onBackground()` 影響。
- `SystemForegroundLocationSource` 使用實際 network／GPS provider 及權限；持續來源按既有 20 秒新鮮度過濾快照，拒絕倒退位置，保留首 fix 提示。
- MapView 移至原樣屬性的獨立 layout，通過能力檢查才建立；無 GMS 不呼叫 MapView 或 heading factory。前台恢復在 lifecycle 交付前重判；失敗實例清理且每個前台／手動重試邊界最多重建一次。
- 沒有新增 runtime 文案；沿用既有三語提示及 attribution。沒有增加背景定位或安裝 APK 權限、資料庫遷移、假地名或第二個供應者。

## 契約覆蓋

| Delta／scenario 群組 | 自動化／來源證據 |
| --- | --- |
| 正常 Google、獨立能力、未知與暫時失敗 | `GoogleServiceCompatibilityTest`、`GoogleCapabilityDeviceInstrumentedTest`；正常工廠拒絕系統來源，商店三種初始渠道 × 四態 |
| 能力恢復、舊網站 callback、操作前重判 | `AppUpdateCoordinatorTest.restoredPlayRejectsLateWebsiteResultAndRechecksActionChannel`、guard listener 世代測試 |
| 一次性成功／快照／共用／逾時／取消／舊結果 | `SystemLocationRequestBrokerTest`、`SystemLocationFixFilterTest`；AOSP 真實 LocationManager 管線及 Android 測試 provider |
| 無 provider、權限、定位開關及無 fix | AOSP 直接 coordinator 測試、permission 裝置矩陣；既有 permission／current-place policy 單測 |
| 起點、候選距離、地址與保護原輸入 | Google reverse-geocoding、current-place／候選政策既有單測；`SearchDestinationInstrumentedTest` 的編輯／swap／重建回歸；生產 HTTP 未替換 |
| 無 GMS 冷啟動／重建／文字詳情 | `NoGoogleServicesInstrumentedTest`，Google factory 若建立即失敗；MapView 不存在、控件隱藏、FULL 詳情 |
| 正常方向／單次定位居中／前後台／文字位置 | `RouteDetailLocationHeadingInstrumentedTest`、`RouteDetailCurrentPositionInstrumentedTest`，正常 Google 回歸 13 項全部通過 |
| 地圖能力失去／恢復／建立例外 | `RouteDetailActivityTest` 新增兩項，包含單次建立失敗後再次前台恢復 |
| Play 原有資格與更新規則 | 更新 source／coordinator／policy 全套 unit；既有 `AppUpdateInstrumentedTest` 8 項；真實 flexible 更新資格留待 7.3 |
| TTS 與通知 | 系統 speech controller、TTS language／failure／monitor policy 單測；AOSP 確認沒有引擎，回傳既有 `NO_ENGINE` 且頁面可用。沒有以第三方引擎代替 |
| 發佈鏈、超級省電及同簽名升級 | 未完成；見 7.1–7.4 |

## 裝置與結果

本任務開始時 `adb devices` 為空；以下均由本任務啟動。使用者另行批准新增並下載 AOSP 映像。

| 裝置 | 畫像 | 證據 |
| --- | --- | --- |
| `BIC_Main_API36_1_Play_360` | API 36.1、Google Play、1080×2400 @480dpi、360dp、直向 | 正常 Google 13 項；地圖／列表邊界 8 項；Store-only、GMS-only、both-off 與恢復的實際 package probe 均通過；詳情三語 × 明暗 × font 1.0／2.0 矩陣通過 |
| `BIC_Task_AOSP36_360` | API 36、default AOSP arm64、360dp、直向 | `pm list packages` 確認 GMS／Store 皆不存在；最初 10 項冷啟動／定位截止／更新通過；核心搜尋到儲存、Tab／重建、置頂 repository、輸入取消回歸；三語 × 明暗 × font 1.0／2.0 通過 |
| `BIC_Min_API25_NoPlay_Compact` | API 25、Google APIs、360dp、直向；測試時停用 GMS | 10 項相容路徑通過，結束前恢復 GMS 並關閉。此機不充當真正無 GMS 證據 |

定位成功案例使用 Android shell 測試 GPS provider，把 fix 經系統 LocationManager 交給未替換的生產來源；測試後移除 provider、還原 AppOps。模擬器 GNSS 在 3 秒內未取得新 fix 的案例確實逾時，沒有延長產品 deadline。

AOSP 初期 Espresso 失敗的 logcat 為 `Invalid DOWN event - pointers already down`；清理輸入並重啟本任務裝置後，核心流程、Tab／重建、無 GMS 冷啟動三項分別通過。此問題不以修改生產點擊邏輯規避。

## 測試修正與可復現命令

- 已刪除要求已移除 helper 名稱存在的 source-grep 測試，改用 broker／實際 RecyclerView 行為測試。
- 舊搜尋測試仍等待已被獨立 Activity 取代的 `routeDetailScroll` BottomSheet；按 `c8143fe` 的 Navigator 實際行為改驗 `routeDetailList`，沒有修改產品導航。
- 既有方向測試曾以 `0.0` 容差要求 SDK 相機座標完全相等，實際差異約 0.000000066 度；改為 0.000001 度容差，仍能檢出跟隨新 fix 的百米級移動。
- 詳情視覺矩陣曾因 Espresso 預設選到失去焦點的 Activity 而無法驗證 CSDI 對話框；改用 dialog root 及目前 Activity 的語言資源取得標題，產品對話框不變。兩種字體矩陣重跑均通過。

```bash
./gradlew build :app:assembleDebugAndroidTest
openspec validate support-devices-without-google-services --strict
adb -s <task-owned-device> shell am instrument -r -w \
  -e runRouteDetailLocationHeading true \
  -e class com.golink.busiscoming.RouteDetailLocationHeadingInstrumentedTest \
  com.golink.busiscoming.test/com.golink.busiscoming.BusIsComingTestRunner
```

最終 `./gradlew build`：1 分 38 秒通過；unit 840 項，0 failure／0 error／1 既有 skipped。最後加入的三項拒絕／近似／定位開關裝置案例全部通過；最終版無 GMS 冷啟動（斷言 MapView factory 呼叫次數 0）、3 秒截止及無 TTS 引擎三項通過，系統 provider 成功案例亦通過。

無障礙回歸為 `RoutePinAccessibilityInstrumentedTest` 與啟用 `runRouteDetailTalkBack=true` 的 `RouteDetailTalkBackInstrumentedTest`，2 項均通過，覆蓋節點語義、聚焦與操作；這不是聆聽真人 TalkBack 朗讀的聲音驗收。正常 Google 詳情的三語、明暗、字體 1.0／2.0 矩陣各一輪通過，涵蓋長內容、Google／CSDI attribution 與對話框、控件及 FULL 詳情；另抽查繁中淺色摘要、英文深色大字體完整詳情的截圖。

AOSP 測試 provider／AppOps、API 25 的 GMS、Google Play 裝置的 GMS／商店均已還原。本任務啟動的三台 AVD 全部已關閉，最終 `adb devices` 為空。所有未取得的正式產物／真機證據仍保留未勾選。
