## Context

上一輪 `add-transit-code-experimental-launcher` 已在主頁加入「乘車碼」入口與底部彈層，並用 `TransitCodeLaunchTarget` 固定列出微信 3 條 scheme、支付寶 4 條候選。用戶實測後得到兩個結論：

- 微信 scheme 方案不可行：`jumpWxa` 沒有反應，另外兩條只能打開微信但不能到達目標頁。
- 支付寶已確認 `appId` scheme 與 render HTTPS 可行，實驗面板不需要繼續展示支付寶候選。

下一輪實驗需要改為：

- 微信走 Android OpenSDK 拉起小程序，並列出全部 `WXLaunchMiniProgram.Req.miniprogramType` 枚舉：
  - `MINIPTOGRAM_TYPE_RELEASE = 0`
  - `MINIPROGRAM_TYPE_TEST = 1`
  - `MINIPROGRAM_TYPE_PREVIEW = 2`
- 微信移動應用 AppID 使用 `wx0a914d80e5b75bfa`，小程序 `userName` 使用原始 ID `gh_a2de39e7aeb4`，首輪不填 `path`。
- AlipayHK 先實驗 scheme 與 HTTPS 兩條候選：
  - `alipayhk://platformapi/startApp?appId=85200098`
  - `https://render.alipay.hk/p/s/hkwallet/landing/easygo`

本 change 只更新實驗入口和診斷能力。後續正式改造時再實作單一用戶入口、自動探測安裝狀態和自動兜底；目前先記錄已確認策略：支付寶正式候選保留 `appId` scheme 與 render HTTPS，AlipayHK 待本輪實驗後確認。

## Goals / Non-Goals

**Goals:**

- 用微信 OpenSDK 代替上一輪微信 URI scheme，提供正式版、測試版、預覽版三個獨立實驗入口。
- 在微信 SDK 拉起前後輸出足夠診斷資訊，能區分未安裝、SDK 不支援、註冊失敗、`sendReq` 失敗、回調錯誤等本地可觀察狀態。
- 在實驗面板加入 AlipayHK scheme 與 HTTPS 入口，並保留獨立點擊、獨立診斷的測試方式。
- 移除支付寶實驗按鈕，避免已確認可行的入口佔用下一輪實驗面板。
- 保持主頁查詢、路線結果、排序、刷新、通知監控和本機資料不受影響。

**Non-Goals:**

- 不實作正式自動兜底策略。
- 不把支付寶或 AlipayHK 設為用戶偏好，不新增本機資料表。
- 不保證 App 能自動判斷外部 App 是否已經到達乘車碼頁；到達頁面仍由人工真機記錄。
- 不調整 Citybus、DATA.GOV.HK、ETA、站點詳情或通知監控流程。

## Decisions

### Decision 1: 將實驗候選抽象為不同啟動類型

`TransitCodeLaunchTarget` 不再只保存單一 `uri`。候選模型應能表達：

- provider：`WECHAT_SDK` / `ALIPAY_HK`
- title、description：面板展示文字
- launchType：微信 SDK 小程序或 Android `ACTION_VIEW`
- 微信 SDK 參數：AppID、`userName`、可選 path、`miniprogramType` 名稱和值
- URI/URL 參數：AlipayHK scheme 或 HTTPS

原因：

- 微信 SDK 拉起不是 `ACTION_VIEW` URI，沿用舊模型會把 SDK 參數硬塞進不合適的字串欄位。
- 候選清單仍需要被單元測試精確覆蓋。
- UI 層可以保持「按候選入口點擊」的互動，不需要知道底層是 SDK 還是 URI。

替代方案：

- 為微信和 AlipayHK 各自建立完全獨立列表：初期簡單，但會讓底部彈層重複分組與診斷展示邏輯。
- 直接在 `TransitCodeBottomSheet` 內硬編碼每個按鈕：實作最快，但不利於測試和後續正式策略收斂。

### Decision 2: 微信 SDK 啟動器集中處理註冊、預檢與回調

新增或重構 `TransitCodeLauncher` 使它依 `launchType` 分派：

- 微信 SDK：
  - 建立 `IWXAPI`
  - 使用 `registerApp("wx0a914d80e5b75bfa")`
  - 檢查微信安裝與 SDK 支援能力
  - 建立 `WXLaunchMiniProgram.Req`
  - 設定 `userName = "gh_a2de39e7aeb4"`、空 path、`miniprogramType`
  - 呼叫 `sendReq`
- AlipayHK：
  - 使用 `Intent.ACTION_VIEW` 打開 scheme 或 HTTPS
  - 捕獲 `ActivityNotFoundException`、`SecurityException` 與非預期異常

微信回調由標準包名路徑下的 `.wxapi.WXEntryActivity` 接收，轉交 SDK `handleIntent`，並將 `onResp` 中的 `errCode`、`errStr`、`extMsg`、transaction 等資訊寫入統一診斷記錄。

原因：

- 微信 OpenSDK 的錯誤來源包含本地註冊、支援能力、`sendReq` 返回值和回調，必須集中記錄才方便定位微信開放平台配置問題。
- Activity 不應承擔 SDK 細節；主頁只負責展示與點擊。

替代方案：

- 只在按鈕點擊處呼叫 `sendReq`，不接 `WXEntryActivity`：可以測 launch 是否發出，但無法看到微信回調錯誤，對定位缺少能力或簽名配置問題不夠。
- 接入微信 SDK 並立即做自動兜底：會混淆本輪「逐條實驗」結果。

### Decision 3: 診斷結果同時輸出到 logcat 和面板

每次點擊實驗入口後，啟動器產出 `TransitCodeDiagnosticResult`，至少包含：

- 入口標題與 provider
- 觸發時間
- launch type
- 目標 URI/URL 或微信 SDK 參數
- 本地預檢結果：包名可見性、是否已安裝、SDK 是否支援、`registerApp` 與 `sendReq` 返回值
- 本地異常：exception class、message
- 微信回調結果：`errCode`、`errStr`、`extMsg`
- 使用者可讀狀態：成功送出、無可處理 App、SDK 不支援、回調失敗、非預期錯誤

logcat 使用穩定 tag，例如 `TransitCodeLauncher`。底部彈層在候選列表下方顯示最近一次診斷摘要，並在返回 App 後可看到最新回調結果。

原因：

- 用戶目前要判斷是否因微信開放平台能力、包名或簽名配置缺失導致失敗，toast 不足以支持排查。
- 面板摘要方便人工測試記錄，logcat 保留完整證據。

替代方案：

- 只寫 logcat：對真機手測不夠直觀。
- 只顯示 toast：資訊量不足，且 toast 不方便複製或追溯。

### Decision 4: AlipayHK 仍使用獨立點擊，不在實驗期自動兜底

AlipayHK 提供兩條獨立入口：

- `alipayhk://platformapi/startApp?appId=85200098`
- `https://render.alipay.hk/p/s/hkwallet/landing/easygo`

每次點擊只嘗試該入口，不自動轉到另一條，也不自動轉到支付寶。

原因：

- 本輪目的仍是判斷每條 AlipayHK 候選是否能到達乘車碼頁，自動兜底會混淆成功來源。
- 正式兜底策略已在討論中確定，但依賴 AlipayHK 實驗結果，應留到下一個正式改造 change。

替代方案：

- 實驗期就按正式策略自動兜底：更接近最終產品，但無法清晰記錄哪個入口成功。

### Decision 5: 支付寶實驗入口移除，但保留結論到驗證文檔

底部彈層不再展示支付寶 4 條候選。驗證文檔需要記錄：

- 已確認可行：`alipays://platformapi/startapp?appId=200011235`
- 已確認可行：`https://render.alipay.com/p/s/i?appId=200011235`
- 本輪不再測試：`saId` 與 `ds.alipay.com` 包裝

原因：

- 用戶已確認支付寶可行方案，實驗 UI 應讓位給 AlipayHK 和微信 SDK。
- 正式策略需要保留支付寶主備方案的來源，避免後續遺漏。

替代方案：

- 保留支付寶入口但標為已確認：面板會變成混合實驗與記錄工具，不利於快速測試。

## Risks / Trade-offs

- 微信 OpenSDK 依賴版本可能與 AGP 或 minSdk 存在相容性差異 → 使用 Maven Central 最新可用穩定版本 `6.8.34`，並以 `./gradlew build` 驗證。
- 微信拉起失敗可能來自開放平台 AppID、包名、簽名、Universal Link、能力開通或小程序可被拉起策略 → 診斷中記錄 AppID、包名、簽名摘要、`registerApp`、`sendReq` 和回調錯誤，人工結合微信開放平台配置核對。
- 微信 `MINIPTOGRAM_TYPE_RELEASE` 常量名稱在 SDK 中有拼寫歷史，直接手寫常量名可能出錯 → 實作時以實際 SDK API 為準，測試固定驗證 0、1、2 三個值與展示名稱。
- AlipayHK scheme 可能未註冊或只在部分版本支援 → HTTPS 入口作為同平台另一條實驗候選，並在診斷中區分無 handler 與啟動異常。
- HTTPS 中轉可能打開瀏覽器而不是 AlipayHK → 真機記錄需包含最終到達頁與是否回到錢包 App。
- 實驗面板診斷文字可能過長 → 面板只顯示摘要，完整診斷寫 logcat；摘要用等寬或短行文本避免擠壓候選列表。

## Migration Plan

1. 保留主頁「乘車碼」入口和底部彈層容器。
2. 重建實驗候選清單，移除上一輪微信 scheme 與支付寶候選，加入微信 SDK 3 條與 AlipayHK 2 條。
3. 新增微信 OpenSDK 依賴、manifest package visibility 與 `.wxapi.WXEntryActivity` 回調。
4. 實作統一診斷模型與 logcat/面板輸出。
5. 更新單元測試、instrumentation 測試與真機驗證記錄模板。
6. 執行 `./gradlew build`；若有可用 Android 設備，再逐條完成真機驗證。

Rollback 策略：

- 若微信 SDK 接入導致構建或啟動風險，可回退 SDK 依賴、`.wxapi.WXEntryActivity` 和微信 SDK 候選；主頁按鈕與底部彈層可以暫時只保留 AlipayHK 實驗入口。
- 本 change 不涉及 SQLite、資料遷移或 Citybus 查詢契約，回滾不需要資料修復。

## Open Questions

- AlipayHK 兩條入口是否能穩定到達「乘車碼」頁，需本輪真機實驗確認。
- 微信正式版、測試版、預覽版哪一個 `miniprogramType` 對 `gh_a2de39e7aeb4` 可用，需本輪真機實驗確認。
- 若微信 SDK 回傳缺少能力或簽名錯誤，需根據具體 `errCode` 與 logcat 再決定是調整微信開放平台配置還是修改 App manifest / 簽名。
