## Context

### 已確認事實與待驗證根因

基線為 `c8143fe`。使用者從 Google Play 安裝／更新 App，網站也使用 Play 簽署包；使用者提供的 Play Console 畫面顯示「自動保護 → 安裝程式檢查」已開啟。超級省電時的英文彈窗在倉庫來源中未找到，亦未找到 App 主動接入 Integrity／licensing 的代碼。

自動保護是首要假設，並非已完成真機驗證的根因。三星文件記錄了相同彈窗及 Play 商店驗證不可用的原因；Google 文件說明檢查由發佈服務加入 APK，並支援保留 Play 簽名的無保護產物。此次嘗試只讀網站公開 metadata 得到 HTTP 403，未取得線上 APK；沒有手機 logcat，不把假設寫成已定位崩潰。

- [Google 自動保護與無保護簽署產物](https://support.google.com/googleplay/android-developer/answer/10183279?hl=en)
- [Samsung 相同啟動錯誤說明](https://docs.samsungknox.com/admin/knox-platform-for-enterprise/knox-service-plugin/kbas/kba-1889-unable-to-use-certain-apps-in-the-separated-apps-folder/)
- [Google Maps Android SDK 的 Play services 要求](https://developers.google.com/maps/documentation/android-sdk/config)
- [Android 系統定位供應者](https://developer.android.com/reference/android/location/LocationManager)

### 目前代碼與契約

- `BusIsComingApplication` 初始化 `AppUpdateRuntime`，後者直接建立 `GooglePlayUpdateSource`，其預設參數建立 Play manager；release coordinator 會註冊 listener，且有額外的前台 install-status 讀取入口，需全部審計。
- `CurrentLocationCoordinator` 與兩條詳情定位來源使用 Fused client。一次性目前位置起點依生效 spec 採定位 3 秒、地址 3 秒、整體 5 秒；無 GMS 後備的 timeout 必須涵蓋 `lastLocation` 等待；若審計發現正常分支與既有短時限契約有偏差，應記錄並另行裁決，不藉此次相容改動擅自調整正常分支。
- `RouteDetailActivity.setupMap` 在 GMS checker 前呼叫 `MapView.onCreate`，XML 與生命週期亦持有地圖 View；失敗時已有全屏文字詳情降級。
- 地址解析為 Google HTTP Geocoding，與 GMS APK 是不同依賴。Citybus 地點搜尋、巴士資料、ETA、CSDI 和系統 TTS 不需要以 GMS 為整體啟動門檻。
- `UpdateChannelResolver` 目前禁止初始 Play 安裝轉網站；本次只放寬「Play 商店明確缺失／停用」分支。

`add-route-detail-current-position`、`improve-current-location-heading`、`align-settings-route-detail-polish` 均已實作但未歸檔。前兩個 change 對相同地圖 requirements 有相互覆蓋風險，主 spec 仍寫原生藍點，實際代碼已是 App 自有方向圖層與獨立行程位置 controller。正常分支以目前代碼和三個 active changes 的有效行為聯集為基線：保留方向圖層、一次定位居中、權限恢復、文字行程位置及 UI 微調，不因複製舊主 spec 而回退。此次不修改或歸檔那些 changes；後續同步本 delta 時須做三方合併並保留它們的已實現行為。

## Goals / Non-Goals

**Goals:**
- 無 GMS／Play 商店、被停用及超級省電限制時，App 可安裝啟動並繼續核心巴士功能。
- **正常 GMS + Play 分支的可觀察行為保持現狀，是驗收硬門檻。** 本 change 不改地圖外觀、方向、相機、Fused 定位語義、權限時機、Play 更新判定、提醒、TTS 或資料請求。
- 明確能力邊界及可恢復降級；短時間內失敗，不延長一次性定位等待。
- 使用相容 Play 簽名發佈新包，核對實際產物而非只靠 debug 成功。

**Non-Goals:**
- 不替換正常裝置的 Google SDK，不拆第二個產品／APK，不新增第二個地圖、地理編碼或語音供應商。
- 不承諾 Google HTTP 在任何網絡均可達，不繞過 Android 的省電、權限或背景限制。
- 不改通知監控算法、資料庫、查詢 parser、排序、未授權的 Google Play 評分入口政策。
- 不把詳情前台持續定位的既有約 10 秒首 fix 提示及後續訂閱改成一次性 3 秒流程；使用者選擇的短等待適用於目前位置起點／候選等一次性請求。

## Decisions

### 1. 分別探測能力，正常路徑原樣委派

在 data 層提供可注入、無 UI 的能力判斷，GMS 與 Play 商店分開表達。GMS 以可用、明確不可用及未能判定區分；商店保留 `AVAILABLE`、`DISABLED`、`MISSING`、`UNUSABLE`，不能把目前回傳 Boolean 的所有 false 都當作允許網站後備。

| 能力狀態 | 定位／地圖 | 更新 |
| --- | --- | --- |
| GMS 可用、Play 可用 | 既有 Fused、方向與 Google Map；不建立系統定位訂閱 | 既有 Play 優先、資格、metadata 補名、flexible update |
| GMS 可用、Play 缺失／停用 | Google 能力檢查成功時沿用現有來源 | 官方網站，與初始來源無關 |
| GMS 明確不可用、Play 可用 | 系統定位後備＋文字詳情 | 仍依 Play 自身結果；不因 GMS 標記擅自改網站 |
| GMS 明確不可用、Play 缺失／停用 | 系統定位後備＋文字詳情 | 官方網站 |
| 探測錯誤、Play `UNUSABLE`、GMS 更新中／結果未知 | 保留對應受控失敗與重試，核心頁面仍可用 | 不把不確定性當網站後備授權 |

GMS 套件缺失、明確停用或 SDK 明確回報環境不支援，才使用無 GMS 路徑。API 任務暫時失敗、GPS 關閉、權限拒絕、HTTP 逾時、定位沒有 fix、底圖網絡失敗都不是全域「無 GMS」訊號。正常地圖既有失敗降級仍然有效。

探測放在相關能力入口與前台恢復邊界，使用一次操作／訂閱的能力快照，不新增永久「無 GMS」偏好。正常狀態不額外訪問網站、不並行 system GPS、不彈啟用 Google 的啟動阻擋。

否決「所有裝置都改用系統定位」及「任意 Google 失敗都切後備」，因其違反正常環境行為不變。

### 2. 隔離可選 SDK 建立與所有生命週期入口

Play source 由 data/update factory 按商店能力延後建立；檢查、listener、前台恢復、完成更新及 UI action 都經同一門檻。正常分支保留原結果 mapper、節流、提示與下載狀態；非正常分支將可預期同步建立／呼叫例外及非同步失敗轉成結構化結果。不能依賴只在 `Application.onCreate` 外包全域 catch。

Google Map 使用通過能力判斷後才建立／inflate 的 holder，包含 `MapView` 構造、`onCreate`、`getMapAsync` 與其後各生命週期；無 GMS 不建立 MapView、不呼叫地圖方向來源。Google 正常分支沿用現有 renderer、布局、相機、方向更新及 state restoration，防止將「延後建立」變成新的可見載入節奏或版面跳動。

### 3. 系統定位後備沿用資料模型及短時限

在 `data/location` 為一次性位置與 `ForegroundLocationSource` 提供 Android `LocationManager` 實作；factory 在 GMS 可用時仍委派現有 Fused 實作。按權限和實際 provider 選用 network／GNSS，不假定每台無 GMS 裝置有網絡定位；使用 Android API 25 起可行的 API 或相容封裝，不提升 minSdk。

無 GMS 一次性請求由開始取得位置時計 3 秒，涵蓋讀取 last fix、等候新 fix 和任何 provider 切換，不為 fallback 重設計時；名稱解析 3 秒且整體受 5 秒截止時間限制。沿用 30 秒快照新鮮度、合併同時請求、座標精度及 elapsed-time 語義。自動失敗保留手動輸入路徑，手動失敗保留原起點及既有提示。

系統來源在成功、逾時、取消、View owner 失效或離開前台時移除 listener；每輪用 generation 拒絕晚到結果。同一共享請求的 consumer 取消只解除自身，有其他有效 consumer 時不提前中止。與舊 request 共用 callback 容器的情況必須以 request identity 隔離，避免前次逾時的結果完成新請求。

詳情文字行程位置以後備 `ForegroundLocationSource` 向現有 controller 提供相同結構的 fix；維持其約 10 秒／20 米請求語義、精度／新鮮度門檻、匹配規則及前台生命週期。無底圖不訂閱高精度地圖 heading，不新增原生感測器方向後備。

### 4. 文字詳情及恢復只影響缺失能力

無 GMS 直接進入全屏文字詳情，保留摘要、站點、轉乘、步行來源、ETA、可用行程位置指示及返回。隱藏地圖定位／全覽 controls，按既有樣式顯示非阻擋提示，提供失敗區域重試；不留下必須拉起 Bottom Sheet 才能讀到資料的大塊空白。

從設定返回或 App 再次進入前台時重新判斷能力：恢復 GMS 後重新允許 Google 路徑；狀態未變不重建成功內容。切換前取消舊來源、遞增 generation；保留查詢及使用者探索上下文，避免兩組定位訂閱和背景地圖 callback 重啟工作。正常環境無能力轉換時不額外重置畫面或相機。

Google HTTP 地址、語言、cache key、attribution 不變。無 GMS 不代表 Google HTTP 不可達，也不授權改用假地名或另一語言。系統 TTS 原有可用引擎與各類失敗提示規則不變，缺失引擎僅停播而保留通知監控。

### 5. 官網更新只增加明確缺失／停用分支

將目前更新渠道的 Boolean probe 提升為可表達缺失、停用及無法判定的結果，沿用既有 package detector。初始渠道持久化保留作來源資訊，但不再阻止明確缺失／停用 Play 時使用官網。`UNUSABLE`／探測例外是受控失敗，不靜默改渠道。

Play 可用分支完整保留：有／無更新、`ERROR_APP_NOT_OWNED` 的網站正向證據及 Play 操作渠道、版本補名、temporarily failed、24 小時自動節流、72 小時提醒、Debug 短路與 flexible flow。恢復 Play 後下次有效檢查回到原優先規則，舊網站 callback 不覆蓋新能力世代，操作時再檢查目標能力以避免開啟已停用商店。

官網沿用既有 HTTPS metadata 驗證、versionCode 比較及三語下載頁，由使用者主動開瀏覽器；不直接下載／安裝，不新增 `REQUEST_INSTALL_PACKAGES`。既有 snapshot 不因暫時錯誤清空。

### 6. 發佈修復與代碼相容分開驗證

保留 Play app signing key 及 application ID；關閉會在啟動時要求 Play 的 installer check，對新發佈確認保護設定，導出同簽名無該檢查的 universal APK。網站仍在 Play 目標地區同版本 100% 發佈後才公開。正常 Google 功能不變的約束不要求保留此已同意移除的發佈檢查。

以實際故障版本和新候選包記錄版本、來源、簽名匹配結果、商店／GMS 狀態、App 啟動邊界及相關 logcat，在相同手機重現超級省電並對照。帳號、key、token、憑證私鑰或未必要識別符不得進入 artifacts／日誌。若新包仍失敗，維持根因待核實，按日誌繼續定位；不能因 console 開關已關或本機 build 成功就勾選修復完成。

## Risks / Trade-offs

- [真正無 GMS 裝置的 GPS 在 3 秒內沒有 fix] → 接受快速失敗，保留原起點、手動搜尋及重試；不加長等待。
- [Google 正常分支被共用重構意外改變] → 窄 factory／guard 接入，對 baseline 的畫面、來源呼叫、權限、更新與網絡分支作回歸，確認系統後備呼叫數為零。
- [SDK 藏在 XML inflate／listener／resume 路徑] → 檢查所有建立與生命週期入口，無 GMS 的冷啟動和重建都必須驗證。
- [active spec 與主 spec 有既有衝突] → 本 delta 明列正常路徑不變，後續同步以前述 active changes 聯集合併，不以舊原生藍點文字覆蓋目前方向圖層。
- [省電模式同時限制網絡或背景服務] → 分來源呈現既有失敗及停止／恢復狀態，核心頁面可進入；不宣稱被 OS 停止後仍持續監控。
- [尚未能取得真機／Play 發佈產物] → 工程可先驗證；對應 release 驗收任務保持未完成並說明缺口。

## Validation Strategy

1. 純單元測試：GMS × 商店狀態路由表、未知／暫時錯誤、初始安裝來源交叉、deadline、cache、新舊 generation、取消與單次交付。正常 Play source 與更新 policy 原測試保留並補充回歸。
2. Instrumentation：故意使無 GMS 測試分支的 Google factory 一被呼叫就失敗；冷啟動、兩個查詢入口、編輯、文字詳情、重建、TTS、更新及前後台均可操作。正常分支以後備 factory 禁止呼叫的斷言驗證，並檢查真實 UI／SDK 結果。
3. 裝置畫像：真正無 GMS 用 AOSP API 36 手機 360dp 級直向；正常路徑用 Google Play API 36 同尺寸，另覆蓋 API 25 相容性。三語、淺深色、font scale 1.0／2.0、拒絕／近似／精確位置、定位開關及無 fix。AOSP 畫像不得用「有 GMS 但關閉商店」替代。
4. release 驗收：真實 Play 安裝資格與 flexible update、網站同簽名升級及資料保留；受影響實機一般／超級省電／恢復一般模式；獨立測試只停用商店、只停用 GMS 和兩者缺失。
5. 所有 AVD 遵守適配及所有權門檻，任務完成後關閉自啟 AVD；裝置或產物缺失時記錄未驗證，不勾選。實作最終運行 `./gradlew build`。

## Migration Plan

先鎖定正常環境回歸基線與診斷證據，再接入能力 guard／定位後備／文字詳情／更新分支；驗證後準備新發佈包。無資料庫遷移、不重置行程、置頂、語言或初始渠道。更新包需簽名相容且 versionCode 可升級，無需卸載使用者資料。

若需回退代碼，使用新的較高 versionCode 發佈相容回退版本，保留資料；回退到包含 installer check 的產物會重新引入無 Play 啟動風險，不能當作此相容需求已滿足。此階段只交付 OpenSpec artifacts；外部設定、發佈、真機操作及實作另按 tasks 執行。
