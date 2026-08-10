## 1. 建立可證明的測試基線

- [x] 1.1 將先前未提交的候選實作與主 spec 修改移出工作樹，保留本 change 制品及已提交的對齊設計，確認定向測試回到生效合同基線
- [x] 1.2 先修改 `AppSettingsSupportContractTest`／必要 instrumentation，固定三語兩段「關於我們」、一句分享價值、Play 在前與本地化官網下載頁，運行定向測試並確認因舊行為而失敗
- [x] 1.3 先修改路線詳情 layout／drawable／marker 測試，固定共用 `48dp`／`24dp` 居中 style、Lucide Route 語義及不透明下車 marker，運行定向測試並確認因舊行為而失敗

## 2. 設定與分享實作

- [x] 2.1 更新香港繁體、獨立簡體及自然英文的 `about_description` 與 `share_copy`，保留一個關於頁文字入口並使用空行分段
- [x] 2.2 讓 `AppSupportActions` 重用 `AppUpdateLinks.PLAY_HTTPS_URL` 與 `websiteDownloadPage(effectiveLanguage)`，維持現有分享 Intent 及失敗 Toast
- [x] 2.3 運行設定支援定向測試，確認三語文案、兩個完整 HTTPS URL、locale mapping、格式參數順序及失敗路徑通過

## 3. 路線詳情視覺實作

- [x] 3.1 建立共用路線詳情地圖控件 style，讓返回、目前位置及全覽路線只保留個別屬性且在 `48dp` 圓形內幾何居中 `24dp` 圖標
- [x] 3.2 使用 Lucide `Route` vector 及授權記錄取代掃描框，保持「全覽路線」content description 與既有相機行為
- [x] 3.3 將下車 marker 繪製為不透明乘車段色實心圓、對比白色外框及白色 `log-out` 圖形，不改其他 marker 身份、轉乘或相機合同
- [x] 3.4 運行路線詳情定向測試，確認共用 style、實際控件屬性、Route 語義、marker alpha 及其他既有地圖合同通過

## 4. OpenSpec 與整體驗證

- [x] 4.1 確認生效主 spec 未包含本 change 未歸檔合同，對 `align-settings-route-detail-polish` 及全倉執行 OpenSpec strict validation
- [x] 4.2 運行全部受影響測試與 `./gradlew build`，核對沒有資源、編譯、單元或 instrumentation 回歸；依使用者最新指示不建立截圖產物

## 5. 完成與提交

- [x] 5.1 逐項核對 proposal、design、delta specs、tasks、實作與驗證證據一致，更新所有已完成 checkbox
- [ ] 5.2 檢查 `git status --short`、暫存範圍與 diff，使用簡潔英文 conventional commit 提交本 change 制品、實作、測試及必要設計計劃
