## Context

目前主頁頂部同時展示 `巴士查詢` 標題、文字樣式 `乘車碼` 入口與主要樣式 `管理路線` 入口。`乘車碼` 點擊會打開 `TransitCodeBottomSheet`，讓測試者逐條嘗試微信 SDK 與 AlipayHK 實驗候選。

真機實驗結果已將正式方向收斂：

- 微信 OpenSDK 可把請求送到微信，但微信返回 `has_no_permission`。依微信 Android 小程序拉起文檔，SDK 請求需要使用移動應用 AppID、目標小程序原始 ID 與 `miniprogramType`；當請求已到達微信但被拒絕時，更可能是目標小程序／開放平台關聯或能力授權不足。目標 `gh_a2de39e7aeb4` 屬騰訊乘車碼，不應作為當前正式方案依賴。
- AlipayHK scheme 與 HTTPS 實驗已確認能到達乘車碼頁。
- 支付寶內地版已確認可用候選為 `alipays://platformapi/startapp?appId=200011235` 與 `https://render.alipay.com/p/s/i?appId=200011235`。

本 change 的正式入口需要面向普通用戶，不再暴露平台選擇或實驗診斷面板；同時要保留實驗代碼，方便後續排查第三方入口變化。

## Goals / Non-Goals

**Goals:**

- 在主頁左上角提供正式 `乘車碼` 按鈕，右上角保留 `管理路線`，並移除 `巴士查詢` 標題文案。
- 點擊 `乘車碼` 後自動按安裝狀態嘗試 AlipayHK 與支付寶候選，優先 AlipayHK。
- 在只明確安裝一個錢包時，不跨錢包兜底，只做同平台 scheme -> HTTPS。
- 在兩個錢包都安裝時，AlipayHK 兩種方式都本地啟動失敗後再降級支付寶。
- 在兩個錢包都未安裝時直接打開 AlipayHK HTTPS，讓外部中轉頁承擔下載或打開提示。
- 將本地啟動失敗、啟動成功、候選順序和最終失敗提示做成可單元測試的純邏輯。
- 保持既有 Citybus 查詢、常用路線、臨時查詢、路線管理、通知監控和實驗面板狀態不變。

**Non-Goals:**

- 不把微信納入正式主備方案，不在主頁正式入口暴露微信實驗結果。
- 不判斷外部錢包打開後是否已到達乘車碼頁；`startActivity` 成功即視為本 App 的啟動責任完成。
- 不新增支付平台偏好、手動平台選擇、資料庫欄位或遠端配置。
- 不移除 `TransitCodeBottomSheet`、微信 SDK 接入、`.wxapi.WXEntryActivity` 或既有實驗測試；是否清理實驗能力留給後續獨立 change。
- 不改動 Citybus API、解析器、排序、ETA、站點對齊或通知欄監控。

## Decisions

### Decision 1: 新增正式拉起器，與實驗拉起器解耦

新增 `TransitCodePaymentLauncher` 或同等責任類，對外提供單一 `launchTransitCode()` 入口。`MainActivity` 僅負責在 `乘車碼` 按鈕點擊時調用它，並在全部本地候選失敗時展示 toast。

正式拉起器內部使用可注入依賴：

- package 檢測器：查詢 `hk.alipay.wallet` 與 `com.eg.android.AlipayGphone` 是否可見／已安裝。
- activity starter：封裝 `Intent.ACTION_VIEW`、`startActivity` 與 `ActivityNotFoundException`／`SecurityException`／其他啟動異常。
- logger：輸出候選名稱、URI／URL、安裝狀態與啟動結果，方便真機排查，但不展示診斷 UI。

原因：

- 實驗 `TransitCodeLauncher` 需要支持微信 SDK、回調診斷和逐條手動測試；正式流程只需要錢包候選鏈和自動兜底。混用會讓正式行為受實驗候選列表變化影響。
- 可注入 package 檢測與 activity starter 可以直接單測四種安裝狀態和失敗兜底，不依賴真機外部 App。

替代方案：

- 直接在 `MainActivity` 裡組裝 URI 並逐個 `startActivity`：實作短，但會把外部 App 啟動策略塞進 Activity，難以單測。
- 擴展既有 `TransitCodeLauncher`：可復用部分診斷模型，但會把微信 SDK 實驗、AlipayHK 實驗與正式支付寶兜底耦合在一起。

### Decision 2: 候選鏈由安裝狀態決定，兜底只處理本地啟動失敗

正式候選常量固定為：

- AlipayHK package：`hk.alipay.wallet`
- AlipayHK scheme：`alipayhk://platformapi/startApp?appId=85200098`
- AlipayHK HTTPS：`https://render.alipay.hk/p/s/hkwallet/landing/easygo`
- 支付寶 package：`com.eg.android.AlipayGphone`
- 支付寶 scheme：`alipays://platformapi/startapp?appId=200011235`
- 支付寶 HTTPS：`https://render.alipay.com/p/s/i?appId=200011235`

候選鏈：

```text
只安裝 AlipayHK: AlipayHK scheme -> AlipayHK HTTPS
只安裝支付寶: 支付寶 scheme -> 支付寶 HTTPS
兩者都安裝: AlipayHK scheme -> AlipayHK HTTPS -> 支付寶 scheme -> 支付寶 HTTPS
兩者都未安裝: AlipayHK HTTPS
```

每個候選使用 `ACTION_VIEW` 發起。若啟動器拋出本地異常或系統找不到可處理 Activity，繼續下一個候選；一旦 `startActivity` 返回成功，立即停止，不再嘗試後續候選。

原因：

- App 只能可靠感知本地啟動是否成功，無法可靠得知外部錢包或 HTTPS 中轉頁最終是否到達乘車碼頁。
- 用戶已確認「只安裝一個錢包」時不跨平台兜底，避免打開非用戶已安裝或已選擇的錢包。
- 兩者都未安裝時優先 AlipayHK HTTPS，符合香港場景與已確認的 AlipayHK 可用性。

替代方案：

- 無條件按四個候選遍歷：會在只安裝一個錢包時跨平台跳轉，與已確認交互不符。
- scheme 失敗後直接去另一個錢包，再回到 HTTPS：可能跳過同平台更兼容的 HTTPS 中轉頁，降低可預期性。
- 成功打開後延遲回到 App 再繼續兜底：Android 無法穩定判斷外部頁面語義，且會造成用戶被多個外部頁面打斷。

### Decision 3: 主頁頂部採用雙主要按鈕，移除標題

`activity_main.xml` 的頂部橫向區域改為左側 `乘車碼`、右側 `管理路線`。兩個按鈕使用相同主要按鈕視覺，遵循既有深青綠主色、6dp 左右圓角和可點擊高度；按鈕間保留清楚間距，頂部到 `常用路線` 區塊仍維持現有緊湊舒適間距。

原因：

- `巴士查詢` 標題對高頻使用主頁幫助有限，刪除後第一屏更聚焦操作。
- `乘車碼` 與 `管理路線` 都是主頁級入口，用同級視覺能減少「乘車碼只是實驗文字連結」的感知。
- 繼續使用 XML + Material Components，符合現有 UI 技術棧和 `docs/ui-style-guide.md`。

替代方案：

- 保留標題並壓縮兩個按鈕：窄屏和字體放大時更容易擠壓。
- 使用圖示按鈕：`乘車碼` 的語義在當前 App 裡更依賴文字，單圖示可發現性不足。
- 做成底部導航或首頁卡片：超出本 change，且會佔用查詢結果空間。

### Decision 4: 實驗入口保留但不從主頁正式路徑進入

本 change 不刪除 `TransitCodeBottomSheet`、`TransitCodeLaunchTargets`、`TransitCodeLauncher`、微信 SDK 依賴或 `.wxapi.WXEntryActivity`。後續若仍需內部調試，可以通過測試或臨時開發入口使用；普通用戶點擊主頁 `乘車碼` 時不會看到實驗面板。

原因：

- 微信 `has_no_permission` 仍可能需要結合微信開放平台配置排查，保留診斷代碼能降低後續重新接入成本。
- 直接清理實驗能力會擴大改動面，並可能影響上一輪實驗 change 的回歸測試。

替代方案：

- 本次同步刪除所有實驗代碼：代碼更乾淨，但風險和審查面更大。
- 在正式入口失敗後打開實驗面板：會讓普通用戶暴露在工程化候選列表中，與「自動兜底、不感知兩個按鈕」要求衝突。

## Risks / Trade-offs

- [Risk] AlipayHK 或支付寶未來修改 scheme／HTTPS 中轉能力 → Mitigation: 候選常量集中管理，保留 logcat 記錄候選和本地啟動結果，後續可用小 change 更新常量。
- [Risk] `PackageManager` 在 Android 11+ 看不到已安裝錢包 → Mitigation: 確保 manifest `<queries>` 包含 `hk.alipay.wallet` 與 `com.eg.android.AlipayGphone`，並補充單元測試與真機驗證。
- [Risk] HTTPS 中轉頁可能打開瀏覽器而非錢包 → Mitigation: 這仍屬成功交給外部系統；人工驗收需記錄最終是否回到錢包和是否到達乘車碼頁。
- [Risk] `startActivity` 成功但外部頁面顯示錯誤 → Mitigation: App 不做語義判斷；本輪基於已完成真機實驗確定候選可到達目標頁，後續入口漂移需重新實驗。
- [Risk] 頂部雙主要按鈕在窄屏或大字體下擁擠 → Mitigation: 使用短文案、穩定高度和橫向間距，補充 XML 斷言與模擬器／真機截圖驗證。
- [Risk] 保留實驗代碼造成測試語義混淆 → Mitigation: 新增正式 launcher 測試與主頁點擊測試，既有實驗測試按保留能力調整或限定為直接測試底部彈層，不再期待主頁點擊打開實驗面板。

## Migration Plan

1. 新增正式候選模型和正式拉起器，保持實驗類不動。
2. 將主頁 `乘車碼` 按鈕從實驗底部彈層切換到正式拉起器。
3. 調整主頁頂部 XML：刪除 `巴士查詢`，統一 `乘車碼` 與 `管理路線` 視覺。
4. 更新／新增單元測試與 instrumentation 測試。
5. 執行 `./gradlew build`，並在可用真機上做外部錢包人工驗收。

回滾時可將 `MainActivity` 點擊綁定改回實驗底部彈層，並恢復頂部 XML；本 change 不涉及資料遷移或 schema 回滾。

## Open Questions

- 無待裁決問題；AlipayHK 與支付寶正式候選、優先級、兜底邊界、主頁佈局和微信排除策略均已確認。
