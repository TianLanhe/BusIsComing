# 驗證記錄

日期：2026-07-23

## 自動化驗證

- `:app:testDebugUnitTest` 通過。
- `androidTest` 在 `Pixel_9_API_36_1`（Android 16／API 36，fingerprint `google/sdk_gphone64_arm64/emu64a:16/BE4B.251210.005/14574095:user/release-keys`）通過 58 項；2 項真實 Google API 驗收因未開啟 `runGoogleApiAcceptance` 而按既有 assumption 跳過，不屬於失敗。
- 已覆蓋行程編輯器歷史幾何與返回鍵、搜尋頁非阻塞定位和雙欄位候選距離、Xiaomi 品牌與設定 Intent 狀態機、shortcut 成功／失敗中繼、無 `MainActivity` 啟動及無殘留 relay task。
- 最終審查後把 Xiaomi 設定返回改為 Activity Result 生命週期，不再持久化本次權限導航 pending；另以未被占用的 `Pixel_9_API_36` 專用實例重跑 17 項相關 instrumentation，修正後的 relay target、行程 helper、過期位置 callback 及 Xiaomi navigator 均通過。整組曾有一次既有下拉刷新手勢逾時，單項立即重跑通過。

## Pixel 標準流程

- 首次請求會顯示 Pixel Launcher 固定面板；取消後可再次請求。
- 確認後 `transit_code` 只有一份 pinned copy，其 intent 指向 `TransitCodeShortcutActivity`。
- 桌面點擊先啟動 relay，未安裝支付 App 時直接進入既有 AlipayHK HTTPS 降級目標；Activity 啟動日誌沒有 `MainActivity`。
- 從桌面移除後 pinned flag 消失，設定頁恢復「從桌面一按開啟」狀態，可再次新增。

## UI 狀態

- 在 360dp 寬度手動檢查英文深色／font scale 2.0 的行程、搜尋與設定頁，以及簡體中文淺色／font scale 1.3 的搜尋頁；頁面可滾動，輸入、圖示、按鈕及底部導航沒有重疊。
- 完整 instrumentation 另覆蓋繁體、簡體、英文的明暗切換，以及底部導航在 font scale 1.0／1.3／2.0 的安全間距。
- 搜尋頁拒絕定位權限後，起終點仍保持 enabled，候選僅省略距離。

## Xiaomi 14／HyperOS 真機流程

- 透過無線偵錯在 Xiaomi 14（`23127PN0CC`／`houji`）完成驗收；系統為 Android 16／API 36、HyperOS `OS3.0.303.0.WNCCNXM`。
- 「桌面快捷方式」權限初始為關閉；`miui.intent.action.APP_PERM_EDITOR` 搭配 `com.miui.securitycenter` 及 `extra_pkgname` 可解析至 `com.miui.permcenter.permissions.PermissionsEditorActivity`，並可由「其他權限」進入「桌面快捷方式」設定。不可解析與啟動失敗時的通用 App Details 回退另由 instrumentation 通過。
- 把權限改為「始終允許」並返回後，ActivityTaskManager 只記錄一次 `CONFIRM_PIN_SHORTCUT`；HyperOS 不再顯示額外確認框而直接固定，`transit_code` 由 manifest 狀態變為 `ImManPinIc`，intent 指向 `TransitCodeShortcutActivity`。
- 桌面點擊只啟動無界面 relay；真機未安裝 AlipayHK、已安裝支付寶，因此既有候選選中 `Alipay Scheme`，前台直接進入 `com.eg.android.AlipayGphone/com.alipay.mobile.quinox.SchemeLauncherActivity`，點擊鏈路沒有啟動 `MainActivity`。
- 真機觀察到支付寶冷啟動及乘車碼內容載入可能需要約 5 至 10 秒；BusIsComing relay 約 20ms 即交出 scheme，支付寶約 1 至 1.5 秒進入其內部 `CSGAPushActivity`，其後等待屬外部 App 內容載入。HTTPS 會轉至小米瀏覽器，`alipayqr` 與預先啟動支付寶均未縮短流程，因此維持既有 scheme／HTTPS 候選順序。
- 驗收使用獨立 `com.golink.busiscoming.codexqa` 測試包，完成後已移除測試包及其 pinned shortcut；正式 `com.golink.busiscoming` 與用戶資料未被覆蓋。
