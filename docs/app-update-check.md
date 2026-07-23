# 應用程式更新檢查

## 渠道規則

### 目前上架前行為

App 尚未正式上架 Google Play，`app/build.gradle.kts` 目前把 `FORCE_WEBSITE_UPDATE_CHECK` 設為 `true`。自動與手動檢查均忽略初始安裝渠道及 Google Play 可用性，直接使用網站 metadata；發現更新後只會開啟目前語言的網站下載頁。開關啟用期間不執行 Play 版本檢查、安裝狀態刷新或 flexible update。

### 上架後目標行為

完成 Google Play 上架及真實更新驗收後，把 `FORCE_WEBSITE_UPDATE_CHECK` 改為 `false`，即可恢復既有 Play 優先策略：更新檢查以 Google Play 對目前帳號、軌道、地區及裝置的結果為優先權威。只要官方 Play 可用，不論 App 最初由哪個渠道安裝，都不以網站全局版本覆蓋 Play 的資格判斷。只有初始為非 Play 安裝且目前沒有可用 Play 時，才使用網站 metadata；初始為 Play 的安裝在 Play 後來不可用時只顯示受控錯誤。

自動檢查在冷啟動首個主要畫面可用後執行，最多每 24 小時嘗試一次。更新可用滿 3 天才自動提醒；「稍後提醒」及「前往更新」均把同一 `versionCode` 延後 3 天，「略過此版本」只停止同一版本的自動 Dialog。手動檢查不受上述節流及提醒抑制限制。

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
applicationId
versionName
versionCode
fileName
sizeBytes
lastUpdated
downloadUrl
```

Android 端只接受 HTTPS 官方 host、`platform=android`、`status=available`、`applicationId=com.golink.busiscoming`、正整數 `versionCode`、非空 `versionName`、ISO 日期及 HTTPS 官方下載 URL。版本只比較 `versionCode`，`downloadUrl` 不會直接用作 Intent 目標。

### 2026-07-23 跨倉庫核對證據

網站倉庫 `feat/010-website-analytics` 的 commit `bc892be0a4c88d5412f373394bcb877f91c92b07` 已註冊 metadata route 並測試 `Cache-Control: no-store`，但 `LatestAPKMetadata` DTO 沒有 `applicationId`，而 `metadata_handler_test.go` 明確把 `applicationId` 列為禁止公開欄位。因此目前網站響應不符合 App 的白名單契約；網站渠道真實驗收保持阻塞，Android 端不得以硬編碼包名或放寬驗證繞過。

網站上線前必須先在網站倉庫補充 DTO、OpenAPI、後端及前端契約測試，部署後再以真實 HTTPS 響應完成驗收。

## 網站 APK 發佈順序

1. 上傳 AAB，等待 Google Play 目標地區完成同版本 100% 發佈。
2. 從 Play Console 下載 Google Play app signing key 簽署的 signed universal APK。
3. 使用 `apksigner` 及 package metadata 驗證 application ID、versionName、versionCode 與 SHA-256 簽名憑證。
4. Google Play app signing key 的 SHA-256 必須為 `33:D0:0B:A0:B0:3A:EA:3F:38:2D:82:42:93:CE:03:5F:9D:8C:92:B3:A4:C1:E6:6E:AE:DF:F8:2D:BD:04:8D:58`；不得公開 upload key `AC:B1:8B:84:F0:67:E9:CE:4D:AD:EA:D5:B2:97:7C:1E:F4:06:2E:3D:DE:39:52:A6:E3:CC:36:8B:D5:D7:43:69` 簽署的候選包。
5. 從實際 APK 產生 `sizeBytes`、APK SHA-256 及 metadata，驗證下載響應與 APK bytes 一致後才公開網站版本。
