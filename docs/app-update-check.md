# 應用程式更新檢查

## 渠道規則

### 目前上架前行為

App 尚未正式上架 Google Play，`app/build.gradle.kts` 目前把 `FORCE_WEBSITE_UPDATE_CHECK` 設為 `true`。自動與手動檢查均忽略初始安裝渠道及 Google Play 可用性，直接使用網站 metadata；發現更新後只會開啟目前語言的網站下載頁。開關啟用期間不執行 Play 版本檢查、安裝狀態刷新或 flexible update。

### 上架後目標行為

完成 Google Play 上架及真實更新驗收後，把 `FORCE_WEBSITE_UPDATE_CHECK` 改為 `false`，即可恢復既有 Play 優先策略：更新檢查以 Google Play 對目前帳號、軌道、地區及裝置的結果為優先權威。只要官方 Play 可用，不論 App 最初由哪個渠道安裝，都不以網站全局版本覆蓋 Play 的資格判斷。只有初始為非 Play 安裝且目前沒有可用 Play 時，才使用網站 metadata；初始為 Play 的安裝在 Play 後來不可用時只顯示受控錯誤。

自動檢查在冷啟動首個主要畫面可用後執行，最多每 24 小時嘗試一次。網站的 `lastUpdated` 解析為香港時區當日零時，滿 72 小時才自動提醒；「稍後提醒」及「前往更新」均把同一 `versionCode` 延後 72 小時，「略過此版本」只停止同一版本的自動 Dialog。手動檢查不受上述節流及提醒抑制限制。24 小時與 72 小時門檻均以可注入 clock 的毫秒級測試驗證，不需真機等待。

網站渠道只在瀏覽器開啟目前 App 語言的下載區：

- 香港繁體：`https://www.busiscoming.com/zh-hant/#download`
- 簡體中文：`https://www.busiscoming.com/zh-hans/#download`
- 英文：`https://www.busiscoming.com/en/#download`

App 不直接下載或安裝 APK，亦不要求 `REQUEST_INSTALL_PACKAGES` 或 `QUERY_ALL_PACKAGES`。

## 網站 metadata 契約

固定 endpoint 為 `GET https://www.busiscoming.com/api/downloads/android/latest/metadata`，成功響應必須使用 `Cache-Control: no-store`，並提供：

```text
platform
status
versionName
versionCode
fileName
sizeBytes
lastUpdated
downloadUrl
```

`applicationId` 不屬於這個公開 runtime DTO。它是網站首頁與 App 共用的下載資訊契約，Android 只由固定官方 HTTPS endpoint 讀取，並驗證來源 URL 沒有 user info、非預期 port、query 或 fragment，以及 `platform=android`、`status=available`、正整數 `versionCode`、非空 `versionName`、正整數 `sizeBytes`、APK 檔名及 ISO 日期。版本只比較 `versionCode`；APK 的 application ID 與簽名則在發佈時從實際 APK 驗證。

`downloadUrl` 只接受以下兩種等價表示：

- 相對路徑：`/api/downloads/android/latest`
- 官方絕對 URL：`https://www.busiscoming.com/api/downloads/android/latest`

其他相對路徑、scheme-relative URL、HTTP、其他 host、非預期 port、query 或 fragment 一律拒絕。這個欄位只用於確認網站 metadata 與固定下載 endpoint 一致，不會直接用作 Intent 目標；用戶仍只會開啟三語網站的 `#download` 區域。

### 2026-07-24 跨倉庫與線上契約核對

網站 `feat/010-website-analytics` 建立的 `LatestAPKMetadata` DTO 按設計不公開 `applicationId`，網站契約測試亦防止意外加入該欄位。已部署的官方 endpoint 返回 HTTP 200、`Cache-Control: no-store`，公開欄位與上述契約一致，且 `downloadUrl` 使用相對路徑 `/api/downloads/android/latest`。

Android 回歸測試使用與已部署響應同形的 fixture，覆蓋 HTTP 狀態、`Cache-Control: no-store`、網絡失敗、無 `applicationId`、相對及官方絕對 `downloadUrl`、目前版本／較高版本、缺欄位、錯誤日期、非整數數值、被篡改 metadata 來源，以及其他 path／host／scheme／port／query／fragment。線上 endpoint 只作交付時只讀核對，不加入一般單元測試，避免外部網絡使 CI 不穩定。

## 網站 APK 發佈順序

1. 上傳 AAB，等待 Google Play 目標地區完成同版本 100% 發佈。
2. 從 Play Console 下載 Google Play app signing key 簽署的 signed universal APK。
3. 使用 `apksigner` 及 package metadata 驗證 application ID、versionName、versionCode 與 SHA-256 簽名憑證。
4. Google Play app signing key 的 SHA-256 必須為 `33:D0:0B:A0:B0:3A:EA:3F:38:2D:82:42:93:CE:03:5F:9D:8C:92:B3:A4:C1:E6:6E:AE:DF:F8:2D:BD:04:8D:58`；不得公開 upload key `AC:B1:8B:84:F0:67:E9:CE:4D:AD:EA:D5:B2:97:7C:1E:F4:06:2E:3D:DE:39:52:A6:E3:CC:36:8B:D5:D7:43:69` 簽署的候選包。
5. 從實際 APK 產生 `sizeBytes`、APK SHA-256 及 metadata，驗證下載響應與 APK bytes 一致後才公開網站版本。
