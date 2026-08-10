## Why

設定頁的產品介紹與分享文案已落後於目前能力，而路線詳情的圓形控件、全覽路線圖標及透明下車 marker 亦削弱辨識與視覺一致性。先前候選實作仍有文案過密及三個按鈕屬性重複問題，需要以最新確認的產品定位與 UI 合同重新收斂後再交付。

## What Changes

- 將「關於我們」改為三語兩段式精簡介紹：產品定位保持香港巴士通勤，實際查詢能力明確限定為 Citybus。
- 將分享內容改為一句核心價值，依目前 App 語言同時提供 Google Play 商品頁及官方網站 `#download` 頁，並沿用既有分享失敗提示。
- 讓返回、目前位置及全覽路線共用同一個 `48dp` 圓形地圖控件樣式，在 `24dp` 圖標盒內保持幾何居中。
- 以端點及相連路徑的 Lucide `Route` 取代掃描框語義，保留本地授權記錄及既有全覽相機行為。
- 將下車 marker 改為目前乘車段色的不透明實心圓、對比白色外框及白色 `log-out` 圖形，不改其他 marker、地圖資料或相機所有權。

## Capabilities

### New Capabilities

無。

### Modified Capabilities

- `app-settings-support`: 更新「關於我們」的產品介紹邊界，以及分享文案、Google Play 商品頁與本地化官網下載頁合同。
- `route-detail-google-map`: 更新下車 marker 的不透明角色表面、三個圓形控件的居中與尺寸合同，以及全覽路線圖標語義。

## Impact

- 受影響 UI／資源：`AboutActivity` 使用的三語字串、分享字串、`AppSupportActions`、路線詳情 layout／style、全覽路線 drawable、地圖 marker icon factory 及 Lucide 授權記錄。
- 不新增網絡請求、依賴、權限、本機資料或外部服務；Google Play 與官網 URL 沿用更新／評分能力的集中來源，分享失敗時保持設定頁可用。
- 不改 Citybus、ETA、CSDI、Google Maps 查詢、漸進載入、路線 bounds、目前位置、marker stable id 或 TalkBack 角色描述。
- 需要三語資源與 URL 合同測試、共用樣式／drawable／marker 測試、實際 inflate 後控件屬性 instrumentation、OpenSpec strict validation 及完整 Android build；依使用者最新指示不建立截圖產物。
