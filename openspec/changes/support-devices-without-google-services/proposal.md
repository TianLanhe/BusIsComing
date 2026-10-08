## Why

使用者從 Google Play 安裝的 App 在超級省電模式下出現要求啟用 Google Play 的啟動攔截，且確認 Play Console 的安裝程式檢查已開啟。App 需要支援完全沒有 GMS 的裝置，同時嚴格保留 GMS 與 Google Play 正常可用時的既有行為；自動保護是首要根因假設，仍須實際發佈包對照驗證。

## What Changes

- **硬性相容約束**：GMS 與 Google Play 正常可用時，沿用目前 Google 地圖、Fused 定位、方向、權限、三語、Play 優先更新與 flexible update 行為，不切換至後備來源、不增加降級提示或額外請求。
- 單一 APK 分別處理 Google Play 商店、GMS 及系統定位能力；缺失／停用不得成為 App 啟動與核心巴士功能的前置條件。
- 僅在 GMS 明確不可用時使用 Android 系統定位後備；一次性定位最多 3 秒、地址解析最多 3 秒、目前位置起點整體最多 5 秒，維持快速失敗與原輸入保護。
- 無 GMS 時直接展示全屏文字詳情、站點、換乘、步行資料與 ETA；不引入第二個地圖供應者。Google HTTP 地址解析與系統 TTS 維持既有來源及受控失敗語義。
- Google Play 商店明確缺失或停用時，不論初始安裝來源均可使用官網更新；商店仍可用時的暫時錯誤、帳號未擁有 App 及 flexible update 規則保持現狀。
- 正式發佈包移除「安裝程式檢查」，保留 Play app signing key、application ID 及既有資料；網站發佈同簽名、無該啟動檢查的 APK。此發佈設定變化不替換正常 Google 裝置上的功能路徑。

## Capabilities

### New Capabilities

- `google-service-compatibility`: 無 GMS 啟動、按能力選擇後備、正常 Google 環境行為不變、生命週期恢復及核心功能相容性。

### Modified Capabilities

- `route-place-selection`: 明確目前位置後備僅由 GMS 能力缺失觸發，保留既有短時限、Google HTTP 地址與使用者輸入語義。
- `route-detail-google-map`: 對無 GMS 的文字詳情、可選地圖生命週期及獨立定位來源作明確約束，保留目前 Google 地圖路徑。
- `app-update-check`: 商店缺失／停用時開放所有安裝來源使用網站更新，並補充保留 Play 簽名但不含安裝程式檢查的發佈契約。

## Impact

- 主要實作範圍：`BusIsComingApplication`／`AppUpdateRuntime`、Google 能力探測、`CurrentLocationCoordinator`／`ForegroundLocationSource`、`RouteDetailActivity` 的地圖建立與生命週期、`GooglePlayUpdateSource`／`AppUpdateCoordinator`／渠道 policy，及必要三語資源。
- 不更換 Citybus、DATA.GOV.HK、KMB／LWB、CSDI、Google Geocoding 接口，不變更本機資料格式、簽名身份、定位權限範圍、正常 TTS 或背景監控排程；不加入其他地圖、15 秒定位、模擬資料或新的語音引擎。
- Play Console 設定及正式產物驗證屬外部發佈工作；關閉開關不等同已安裝舊包已修復。本次 propose 不修改控制台、不上傳或發佈 App。
- 既有地圖／目前位置／方向 changes 已完成但未歸檔，實作與 spec 同步需保留其現有行為，詳見 design 的衝突裁決。
- 驗證需同時覆蓋真正無 GMS 的 Android 裝置、任務持有的 Play 裝置、受影響手機超級省電模式，以及 Play 簽署的 release 產物；正常 Google 分支回歸是完成門檻。
