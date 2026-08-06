## 1. 前置規格與現況門禁

- [ ] 1.1 確認 `add-app-update-check` 已完成剩餘真實 Play 驗證、同步及歸檔，並核對主 `app-settings-support` 已保留實際檢查更新契約；前置條件未滿足時不得開始本 change 實作
- [ ] 1.2 重新檢查 `SettingsFragment`、`AndroidPlayPackageProbe`、`AppUpdateExternalActions`、Play links、Manifest `<queries>` 與既有更新測試，記錄評分可共享與必須隔離的代碼邊界

## 2. Google Play 可用性分類

- [ ] 2.1 先新增純 JVM 測試，覆蓋 Play 可用、已停用、缺失、商品 Intent 不可解析及 package／security 例外收斂為不可用，並驗證非 Play 安裝來源不影響評分資格
- [ ] 2.2 實作結構化 `PlayStoreAvailabilityDetector` 與四態 model，集中 package、enabled 及 resolver 判斷，不把 `PackageManager` 細節散落到 Fragment
- [ ] 2.3 讓既有 `PlayPackageProbe.isPlayAvailable()` 對新 detector 作相容投影，運行更新渠道測試確認 Play／網站 fallback 語義未改變

## 3. 評分與恢復外部導航

- [ ] 3.1 先新增 navigator 單元測試，驗證商品頁只使用 `com.android.vending`、不使用無 package HTTPS fallback，並覆蓋 Play 應用詳情、BusIsComing 應用詳情及三語 Google 官方說明 URL
- [ ] 3.2 實作 `GooglePlayRatingNavigator` 與集中 link provider，沿用正式 application ID／Play 商品 link，並捕獲不可解析、`ActivityNotFoundException`、`SecurityException` 及已確認可恢復例外
- [ ] 3.3 驗證 Google 官方說明仍為 `support.google.com/googleplay/answer/190860`，語言 mapping 為 `zh-HK`、`zh-CN`、`en`，且任何失敗不串接第三方商店、APK 或其他 fallback

## 4. 設定頁狀態矩陣與本地化

- [ ] 4.1 在繁體、獨立簡體及自然英文資源加入 Play 停用、缺失、不可用、外部啟動失敗、`前往啟用`、`查看安裝說明`、`應用設定` 與取消文案，避免宣稱已完成評分
- [ ] 4.2 把 `SettingsFragment` 的 `unsupported_rate_app` action 改為呼叫評分流程；可用時直接打開商品頁，其他三態各顯示唯一對應的 Material 對話框及恢復操作
- [ ] 4.3 確保從 Google Play、系統設定或官方說明返回，以及 configuration change／Fragment 重建時不保存或重播 pending 外部導航；只有再次點擊才重新探測
- [ ] 4.4 新增 Fragment instrumentation 測試，覆蓋四種 Play 狀態、取消、每個恢復 action、啟動失敗、返回不自動續辦與無瀏覽器 fallback

## 5. UI、無障礙與真實裝置驗收

- [ ] 5.1 在香港繁體／簡體／英文、明暗主題、360dp 與大型字體檢查設定列及三種對話框，確認無裁切、按鈕可理解且 TalkBack 依序讀取狀態與操作
- [ ] 5.2 只使用本任務啟動且具 Play Store 的適配模擬器或測試裝置，確認一次點擊可打開 BusIsComing 官方商品詳情頁；只記錄「商品頁已打開」，不得宣稱評分已提交
- [ ] 5.3 只使用本任務啟動的無 Play／可停用 Play 測試設備驗證缺失、停用、重新啟用後再次點擊及不可用分支，完成後關閉本任務啟動的全部模擬器

## 6. 回歸與完成檢查

- [ ] 6.1 運行評分 detector／navigator／Settings instrumentation 定向測試及既有 app update 渠道、手動檢查與外部 action 回歸測試
- [ ] 6.2 運行 `./gradlew build` 與 `openspec validate --all --strict --no-interactive`，如實記錄任何無法完成的真實 Play 限制
- [ ] 6.3 檢查 `git status --short`、變更 diff 與 tasks 勾選，確認沒有 In-App Review、安裝權限、第三方 APK、更新流程改寫或無關重構後再按倉庫規則提交
