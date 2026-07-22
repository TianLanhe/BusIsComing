## Why

BusIsComing 目前只有繁體中文介面，部分文字仍硬編碼於 XML、Kotlin、通知、TTS 與 formatter，Citybus、DATA.GOV.HK 及 Google 的動態資料亦固定或偏向繁體中文。這令語言切換、動態資料一致性、TTS 排錯及英文／大字體版面都無法得到可靠保障，因此需要建立完整且可持續執行的三語能力。

## What Changes

- 新增「跟隨系統」、「繁體中文」、「简体中文」及 `English` App 語言選擇；新安裝與升級用戶預設跟隨系統，不支援的系統語言回退繁體中文。
- 將常用、搜尋、設定三個頂層 destination、底部導航、次級頁、Toast、Dialog、通知、TTS、無障礙描述、分享／意見回饋內容及外部連結適配繁體中文、簡體中文與英文，並建立自足的自然翻譯、術語及資源完整性規則。
- 讓 Citybus 地點／路線／停站／詳情、DATA.GOV.HK ETA 與 Google 地址跟隨目前 App 語言；切換語言時取消或忽略舊語言工作、隔離快取並自動重查，且不增加路線使用次數。
- 語言切換須保留目前頂層 destination、常用路線及搜尋起終點等安全狀態；語言與外觀模式分開持久化，切換其中一項不得重設另一項，三語 UI 須同時在淺色與深色模式驗證。
- 保留用戶輸入、既有保存及匯入的路線／地點名稱原文；只允許單個缺失官方欄位按明確順序回退，不進行機器翻譯或整體繁體重試。
- 令活動通知立即跟隨語言切換，限制 TTS 只能使用相容語言體系，並以原因明確的 Toast 與非敏感診斷區分 engine、資料、語言、audio focus、播放及 timeout 失敗；監測本身繼續運作。
- 調整三語及大字體下的高風險版面，確保約 360dp portrait、API 36.1／37、font scale 1.0／1.3／2.0 下核心內容和操作可讀、可點擊及可滾動。
- 以真實三語 A/B 證據審核 Citybus 請求；`bsearch_p3.php` 保留 `limit`、`timestamp`，`getp2pstopinroute.php` 的 `ginfo`、`lid` 在確認無作用前保留，不為精簡而犧牲正確性。
- 新增長期本地化指南，更新 `AGENTS.md`，以全專案視角翻新 `openspec/config.yaml`，並在實作階段依 `create-readme` skill 翻新 `README.md`。
- 不修改任何外部網站或後端、SQLite schema、路線匯入匯出格式、既有排序或使用次數語義，也不新增系統語音設定入口或語音預覽。

## Capabilities

### New Capabilities

- `app-localization`: 定義 App 語言選擇、三語資源與翻譯品質、語言切換生命週期、動態資料一致性、既有資料邊界、版面適配及三語驗收門檻。

### Modified Capabilities

- `app-settings-support`: 在頂層設定 destination 將語言入口由暫不支援改為立即生效的單選對話框，並讓分享、意見回饋、官方網站與私隱政策跟隨語言；保持外觀設定在語言之前且兩者互不重設。
- `app-ui-style-system`: 加入常用／搜尋／設定三個頂層 destination、底部導航、三語、淺／深色、窄屏、大字體及無障礙下的自適應版面要求。
- `place-search-api`: 將固定 `l=0` 改為目前語言參數，保留 `limit`／`timestamp` 並要求三語解析與真實驗證。
- `citybus-route-query-api`: 將固定 `l=0` 改為目前語言參數，擴充三語 route parser 及語言版本過期結果處理。
- `bus-route-query`: 加入常用與搜尋 destination 在語言切換後各自保留查詢上下文、自動重查及不增加使用次數的查詢行為。
- `google-reverse-geocoding-resolver`: 將固定繁體地址改為三語 `languageCode`，保持 `regionCode=HK`、語言快取隔離及真實三語驗證。
- `citybus-first-leg-eta`: 按目前語言選擇 ETA 目的地、備註與站名欄位，並定義單欄位官方原文回退。
- `citybus-eta-arrivals-sheet`: 讓方向、備註、班次及更新時間文案使用目前語言並容納較長文字。
- `notification-bar-monitoring`: 讓通知 channel、內容、action 與播報文案跟隨語言，並在活動監測期間安全更新。
- `monitor-voice-playback-diagnostics`: 以語言體系限制 Voice fallback，細分 TTS 失敗原因、Toast 頻率及診斷資料。

## Impact

- **Android 程式與資源**：影響 `app/src/main/res/`、`MainActivity`、`FrequentRoutesFragment`、`SearchFragment`、`SettingsFragment`、`RouteQueryState`／`RouteQueryCoordinator`、所有次級 App UI、`data/location`、`data/model`、`data/repository`、`service` 及其 JVM／instrumentation 測試；新增集中語言策略、三語資源與 locale config，但不新增資料庫 migration。
- **外部資料源**：Citybus `bsearch_p3.php`、`ppsearch_p3.php`、`showstops2.php`、`getp2pstopinroute.php`、DATA.GOV.HK Citybus ETA 與 Google Geocoding API v4 都需三語真實驗證；保留脫敏請求證據、三語 fixture、parser 回歸與安全 A/B 比較。
- **生命週期與相容性**：Activity locale 重建須保留目前頂層 destination、常用路線、搜尋起終點、各自排序、滾動及未提交輸入；舊語言結果作廢，具有有效查詢上下文的 destination 各自自動重查，活動通知立即更新，TTS 失敗不得中斷監測。語言偏好與外觀偏好互相獨立；既有路線名稱、地點名稱、匯入資料、通知 channel 設定及使用統計保持相容。
- **文件與規則**：新增 `docs/localization-guidelines.md`，更新 `AGENTS.md`、全局 `openspec/config.yaml` 及 `README.md`；OpenSpec 與文件中文仍使用繁體中文，App runtime 文案改為三語完整覆蓋。
- **驗證**：要求 resource／placeholder／plural 完整性、無硬編碼檢查、Lint、三語 parser、TTS 與 cache 單元測試、API 36.1／37 UI instrumentation、三語 × 淺／深色交叉檢查、大字體及 TalkBack 檢查、`./gradlew build`，以及不可由 mock 取代的 Citybus 與 Google 三語真實驗證。Google 驗證需要有效 key、package／certificate 限制及可連接 `geocode.googleapis.com` 的網絡。
