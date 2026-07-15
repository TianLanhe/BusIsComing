## 1. 正式包名完整遷移

- [x] 1.1 將 `app/build.gradle.kts` 的 `applicationId` 和 `namespace` 改為 `com.golink.busiscoming`，不改動 `versionCode` 或 `versionName`。
- [x] 1.2 將 `app/src/main/java/com/example/busiscoming` 遷移到 `app/src/main/java/com/golink/busiscoming`，同步更新所有 main source 的 `package` 與 `import`。
- [x] 1.3 將 `app/src/test/java/com/example/busiscoming` 與 `app/src/androidTest/java/com/example/busiscoming` 遷移到 `com/golink/busiscoming`，同步更新測試 package、import、instrumentation class name 與 package 期望。
- [x] 1.4 更新仍在使用的腳本、測試命令、`APP_ID`、`pm grant`、`run-as`、`dumpsys` 等引用，讓當前可執行路徑使用 `com.golink.busiscoming`。
- [x] 1.5 更新 Google reverse geocoding request identity 測試，期望 `X-Android-Package` 為 `com.golink.busiscoming`；不提交或修改 API key。
- [x] 1.6 全倉庫搜尋 `com.example.busiscoming`，確認 current code、tests、scripts、docs 中不再殘留會被執行或誤導的舊包名；archive 歷史和明確歷史記錄可保留。

## 2. 移除微信小程序實驗能力

- [x] 2.1 移除 `wechat-sdk-android` 依賴與 version catalog 條目，確認構建不再引入 `com.tencent.mm.opensdk`。
- [x] 2.2 從 manifest 移除 `com.tencent.mm` package query 與 `.wxapi.WXEntryActivity`；保留 `hk.alipay.wallet`、`com.eg.android.AlipayGphone` 與 TTS service query。
- [x] 2.3 刪除微信 OpenSDK client、微信小程序參數、微信 callback diagnostic、微信實驗 launcher 與 `.wxapi` source。
- [x] 2.4 刪除不再需要的 `TransitCodeBottomSheet` 實驗 UI 與實驗診斷代碼；保留正式 `TransitCodePaymentLauncher` 和 `TransitCodePaymentTargets`。
- [x] 2.5 移除或重寫微信實驗相關單元測試與 instrumentation 測試，保留並加強正式 AlipayHK／支付寶 fallback 測試。
- [x] 2.6 搜尋 `微信`、`WeChat`、`wechat`、`WXEntry`、`com.tencent.mm`、`weixin://`、微信 AppID/userName，確認 current code/tests/specs/docs 不再描述當前可用微信實驗能力；archive 歷史除外。

## 3. Citybus 請求最小化

- [x] 3.1 將 `CitybusBusRouteRepository.requestHeaders()` 改為空 map，刪除靜態 `CITYBUS_COOKIE`。
- [x] 3.2 將 `CitybusPlaceSearchRepository.requestHeaders()` 改為空 map，刪除靜態 `CITYBUS_COOKIE`。
- [x] 3.3 將 `CitybusP2pStopMapResolver.requestHeaders()` 改為空 map。
- [x] 3.4 將 `CitybusRouteDetailRepository.requestHeaders()` 改為空 map。
- [x] 3.5 更新相關單測，斷言不再設置 `Cookie`、`User-Agent`、`Referer`、`Sec-Fetch-*`、`sec-ch-ua*`、`X-Requested-With`、`Connection` 或 `Accept-Language`。
- [x] 3.6 保留或補強解析回歸測試，確保 fixture 解析、路線候選、地點搜尋、stop map 與路線詳情行為不依賴 header/cookie。

## 4. 路線請求日誌脫敏

- [x] 4.1 將 `logRouteCurl` 調整為 debug-only 脫敏摘要，不再輸出完整 cURL、完整 URL、完整 query string、headers 或 cookies。
- [x] 4.2 摘要最多保留 endpoint、searchMode、header count 和粗粒度/存在性資訊；不得輸出完整 `slat`、`slon`、`elat`、`elon`、`rawInfo` 或時間 query。
- [x] 4.3 更新 `buildCurlCommand`/logger 相關測試，改為驗證脫敏摘要；明確斷言輸出不包含 `curl`、`Cookie`、`User-Agent`、`slat=`、`slon=`、`elat=`、`elon=` 或完整 Citybus URL。

## 5. 倉庫知識同步

- [x] 5.1 更新 `openspec/specs/transit-code-experimental-launcher/spec.md` 對應的 current spec 變更，移除實驗面板、微信 SDK 候選、微信診斷與微信 package visibility 要求。
- [x] 5.2 更新 `openspec/specs/transit-code-quick-launcher/spec.md` 對應的 current spec 變更，確認正式入口只保留 AlipayHK／支付寶 fallback，不依賴微信或實驗面板。
- [x] 5.3 更新 Citybus request 相關 current specs，使路線查詢、地點搜尋、stop map、路線詳情均描述為無顯式瀏覽器 header/cookie 的請求。
- [x] 5.4 更新 `docs/transit-code-experimental-launcher-validation.md`，在頂部標記為歷史實驗記錄，說明微信 SDK 實驗已廢棄，當前生產入口只保留正式 AlipayHK／支付寶 fallback。
- [x] 5.5 不批量重寫 `openspec/changes/archive/**` 或 `docs/superpowers/plans/**` 的歷史內容，除非其中內容仍被當前腳本或測試執行。

## 6. 驗證

- [x] 6.1 運行 `./gradlew build`，確認編譯、單測、lint、debug/release assemble 全部通過。
- [x] 6.2 檢查生成產物或 instrumentation，上報運行時 package name 為 `com.golink.busiscoming`，launcher Activity 可啟動。
- [x] 6.3 驗證正式 `乘車碼` 點擊仍走 `TransitCodePaymentLauncher`，不打開實驗 bottom sheet，不嘗試微信。
- [x] 6.4 使用真實網路對 `ppsearch_p3.php` 跑 10 個有效請求，對比刪除 header/cookie 後 HTTP 200、parser 標記、路線卡片語義和 `showroutep2p(...)` 參數一致；不要求完整 body hash 一致。
- [x] 6.5 使用真實網路對 `bsearch_p3.php`、`showstops2.php`、`getp2pstopinroute.php` 各跑 10 個有效請求，確認 HTTP 200、業務標記有效且 body hash 或業務簽名一致。
- [x] 6.6 運行 `openspec validate prepare-google-play-release --strict`。
- [x] 6.7 搜尋確認權限聲明沒有被本變更誤改：不新增 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`，不移除現有 `SCHEDULE_EXACT_ALARM`、`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_DATA_SYNC`、`WAKE_LOCK`。
