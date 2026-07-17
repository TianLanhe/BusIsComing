# App 三語支援設計

日期：2026-07-17

## 背景

BusIsComing 目前只有單一繁體中文資源目錄，部分 XML、Kotlin、通知、Toast、TTS、格式化器與領域模型仍直接包含繁體中文文字。Citybus 地點和路線查詢固定使用 `l=0`，Google 逆向地理編碼固定使用 `zh-Hant`，DATA.GOV.HK ETA 亦主要讀取繁體中文欄位。這會令 UI 語言、動態資料、通知與語音不能一致切換，並使英文及較大字體容易受到既有固定寬高限制。

本設計為 App 新增繁體中文、簡體中文及英文三語能力，並提供「跟隨系統」選項。它同時整理長期本地化規則、動態資料語言、TTS 診斷、Citybus 請求參數、版面驗證及專案文件，使後續功能不得再次退回單語或硬編碼模式。

翻譯語氣、術語及審校原則在本設計中自足定義；Android 端採用原生資源及 AppCompat 語言架構，不依賴外部字典或本機倉庫路徑。

## 目標

- 支援「跟隨系統」、「繁體中文」、「簡體中文」及 `English` 四個語言選項。
- 將所有 App 自有可見文字、通知、Toast、TTS、無障礙文字、分享及意見回饋內容完整適配三語。
- 令 Citybus、DATA.GOV.HK 及 Google 動態資料跟隨 App 實際語言，並避免切換過程出現混合語言。
- 保留用戶自訂及既有保存資料的原始文字，不進行隱式翻譯或資料遷移。
- 以自然、符合各語言表達習慣的翻譯取代字形轉換或直譯。
- 令 UI 在三語、約 360dp 闊度及大字體下保持可讀、可操作及可滾動。
- 詳細區分 TTS 不可用原因，在不停止監測的前提下提供可排查的 Toast 與診斷日誌。
- 審核 Citybus 請求，只在充分證明不影響正確結果時移除參數，不加入無必要的瀏覽器型 header。
- 將本地化、外部資料語言及驗證要求沉澱到專案長期文件與全局 OpenSpec 設定。

## 非目標

- 不修改任何外部網站或後端。
- 不翻譯用戶輸入的路線名稱、既有保存地點名稱或匯入檔案內容。
- 不對 Citybus、DATA.GOV.HK 或 Google 內容進行機器翻譯。
- 不控制 Android 權限對話框、系統文件選擇器、分享面板、TTS 引擎設定、支付工具或瀏覽器等第三方畫面的語言。
- 不新增「系統語音設定」入口，也不恢復語音預覽。
- 不為了精簡而刪除尚未證明無作用的 Citybus 參數。
- 不修改常用路線資料庫 schema、路線匯入匯出協議或使用次數語義。

## 技術方案

### 採用方案：Android 原生資源與集中語言策略

App 以 Android `strings`、`plurals`、locale resource qualifier、AppCompat per-app locale 及 Android `localeConfig` 作為語言基礎。新增集中式 `AppLanguageRepository`，將用戶選擇解析為不可變的語言快照，再供 Activity、Repository、Service、Notification、TTS 及外部連結共同使用。

選擇此方案的原因：

- 可由 Android Lint 檢查遺漏翻譯、硬編碼文字及不安全的文字拼接。
- 原生支援 XML、複數、格式化參數、configuration change 及 Android 13 以上的系統 App 語言設定。
- 不需要維護一套繞過 Android resource resolution 的自製字典。
- 能把外部資料語言及快取版本集中管理，降低畫面與背景服務語言分歧。

### 不採用方案

1. **仿照網站建立 Kotlin 三語字典**：會繞過 XML 資源、複數、Lint、系統 App 語言及 Android fallback，維護成本較高。
2. **資源與模型分散混用**：把部分顯示文字留在 model 或 formatter 會令 Activity、Service 與通知各自判斷語言，容易出現混合語言和遺漏翻譯。

## 語言模型與選擇

### 支援選項

| 用戶選項 | App locale tag | 說明 |
| --- | --- | --- |
| 跟隨系統 | 空的 AppCompat locale list | 使用系統語言；不支援時回退繁體中文 |
| 繁體中文 | `zh-Hant-HK` | 香港實用書面語 |
| 简体中文 | `zh-Hans-CN` | 自然簡體中文 |
| English | `en` | 自然、簡潔的英文產品語言 |

新安裝及從舊版升級的用戶都沒有既有語言偏好，因此首次進入新版時使用「跟隨系統」。用戶選擇明確語言後持續保存，直到再次選擇「跟隨系統」。系統語言不受支援時必須以繁體中文顯示，不由裝置任意選擇其他資源。

跟隨系統時先按 language、script 及 region 解析實際語言：英文的任何地區使用英文；`zh-Hant` 或香港、澳門、台灣中文使用繁體中文；`zh-Hans` 或中國大陸、新加坡中文使用簡體中文。無法判斷文字體系的裸 `zh` 以及其他不支援語言均回退繁體中文。解析結果必須由集中語言策略提供，不由各畫面自行比較 `Locale`。

### 資源目錄

- `values/`：繁體中文，同時作安全預設資源。
- `values-b+zh+Hans/`：簡體中文。
- `values-en/`：英文。
- `xml/locales_config.xml`：宣告 `zh-Hant-HK`、`zh-Hans-CN` 及 `en`。

明確語言選項在任何 UI 語言下都使用自稱：`繁體中文 / 简体中文 / English`。「跟隨系統」本身使用目前 UI 語言，設定頁副標題同時顯示實際生效語言，例如「跟隨系統（目前：繁體中文）」。選擇後立即保存及套用，不提供額外儲存按鈕。

### 集中語言上下文

`AppLanguageRepository` 提供：

- 用戶選擇及解析後的實際語言。
- 資源 locale tag。
- Citybus `l`。
- Google `languageCode` 與 `regionCode`。
- DATA.GOV.HK 欄位選擇順序。
- TTS 允許的語音體系與候選順序。
- 官方網站首頁、私隱政策等語言路徑。
- 單調遞增的語言版本號。

每次網絡工作捕獲不可變 `LanguageSnapshot`。回調更新 UI 或快取前必須核對語言版本；切換前發出的舊語言結果即使稍後返回也會被丟棄。

## 語言切換流程

切換語言時：

1. 保存新選項並透過 AppCompat 套用 locale。
2. 重建目前 Activity，刷新設定頁及返回棧中的 App 自有畫面。
3. 取消或標記作廢舊語言的查詢、候選地點、路線詳情及地址解析。
4. 清除或隔離所有語言相關快取。
5. 保留目前常用路線 id，或臨時查詢的起終點及座標。
6. 以新語言自動重新查詢，該次重查不增加常用路線使用次數。
7. 立即重建活動中的通知；停止尚未完成的舊語言播報，後續 TTS 使用新語言。

Activity 因 locale 重建時還需保存排序方式、滾動位置及尚未提交的表單輸入。舊語言查詢結果本身不保存或恢復。

若自動重查失敗，顯示新語言錯誤，不回退展示舊語言結果。Citybus 整個請求或解析失敗亦不得靜默改用繁體中文重試。

## App 自有文案與資源治理

### 覆蓋範圍

三語資源覆蓋：

- 所有 layout、Activity、Adapter、Bottom Sheet 及 Dialog。
- Toast、空狀態、載入狀態、錯誤及限制提示。
- 通知 channel、標題、正文、狀態與 action。
- TTS 播報語句。
- TalkBack `contentDescription`、組合控件描述及狀態語義。
- 分享內容、意見回饋 email 主旨與正文、私隱政策及網站連結說明。
- 路線、車費、耗時、步行距離、ETA、日期與數量格式。

Activity、Adapter、Service 及 model 不得直接拼接用戶可見文字。資料層返回結構化狀態或穩定錯誤類型，UI 與通知層再從目前 locale 取得資源。數量使用 `plurals`；動態內容使用具備一致 placeholder 類型的格式化資源。

### 不翻譯內容

- 用戶輸入的路線名稱。
- 已保存或匯入的起點、終點名稱。
- Citybus、DATA.GOV.HK 及 Google 單欄位回退時的官方原始值。
- 品牌名、email、版本號、裝置資料、路線編號及其他真正語言無關的值。
- Parser 的上游標籤、regex、原始 fixture 與第三方規格樣例。

只有真正語言無關的資源才可使用 `translatable="false"`。第三方原始錯誤不得直接顯示給用戶，必須先映射為穩定錯誤類型，再以目前語言產生自然及可行動的提示。

### 翻譯原則

- 繁體中文採用自然的香港實用書面語，例如「儲存」、「檔案」、「私隱政策」、「車費」及「巴士」。
- 簡體中文獨立撰寫，不以繁簡字形轉換代替翻譯及審校。
- 英文採用簡潔、自然及克制的產品語言，不保留中文語序。
- Citybus、DATA.GOV.HK、ETA、HKD 等品牌與領域術語保持準確。
- 避免過度口語、官樣文字、技術接口術語及逐字直譯。
- 每種語言獨立檢查語氣、標點、plural、placeholder 順序及版面長度。

## 動態資料語言

| App 實際語言 | Citybus `l` | Google `languageCode` | DATA.GOV.HK 主要欄位 | 官方網站首頁 | 私隱政策 |
| --- | ---: | --- | --- | --- | --- |
| 繁體中文 | `0` | `zh-Hant` | `name_tc`、`dest_tc`、`rmk_tc` | `/zh-hant/` | `/zh-hant/privacy/` |
| 簡體中文 | `2` | `zh-Hans` | `name_sc`、`dest_sc`、`rmk_sc` | `/zh-hans/` | `/zh-hans/privacy/` |
| English | `1` | `en` | `name_en`、`dest_en`、`rmk_en` | `/en/` | `/en/privacy/` |

Google 保持 `regionCode=HK`。Citybus 地點、路線、停站及詳情請求均使用同一語言快照；相關 parser 必須接受三語標籤及結構，不得繼續假設只有繁體中文文字。

DATA.GOV.HK 的單一官方欄位缺失時可按下列順序回退：

- 繁體：`tc -> sc -> en`
- 簡體：`sc -> tc -> en`
- 英文：`en -> tc -> sc`

回退只適用於站名、ETA 目的地、備註等單欄位。每次回退記錄資料源、欄位及實際語言，但不機器翻譯、不寫回保存路線，也不把整個請求改成另一種語言。

Google 地址快取鍵繼續包含座標與語言。逆向地理編碼失敗時不以其他語言重試，不保存錯誤、舊語言或其他語言地址，並沿用目前本地化失敗流程。新選擇或重新選擇的地點使用當時語言；既有保存名稱保持原樣。

## Citybus 請求參數審核

本變更以「正確結果優先」審核 Android Citybus 請求，不複製瀏覽器或其他服務端的請求模板。

### 保留參數

| 接口 | 保留參數 | 決策 |
| --- | --- | --- |
| `bsearch_p3.php` | `l, q, limit, timestamp` | 全部保留，不列作本次精簡對象 |
| `ppsearch_p3.php` | `slat, slon, elat, elon, t, ws, leg, m1, l` | 全部保留 |
| `showstops2.php` | `r, l` | 全部保留 |
| `getp2pstopinroute.php` | `info, ginfo, lid, l` | 預設全部保留；只在個別參數無作用獲充分證明後才移除 |

`ppsearch_p3.php` 中 `t` 會影響是否返回結果，`ws` 會影響行程時間，`m1` 會影響候選集合並承載 `T/F/W` 搜尋模式，`l` 決定語言。`leg` 即使單次抽樣未顯示差異，仍具備轉乘段語義，因此保留。

`getp2pstopinroute.php` 的 `ginfo` 已知會改變原始 HTML 的時間摘要，目前不能認定為沒有作用；`lid` 的既有樣本亦不足以證明可刪除。兩者均預設保留。實作期間只可在三語真實 A/B 對照證明刪除後仍穩定返回、成功解析，而且完整路線段、方向、上下車站及途經站點正確時，逐項移除；若任一案例不同即保留並記錄證據。本變更允許最終沒有參數可安全刪除。

### Header 原則

- Citybus mobile 請求繼續不顯式加入 `Cookie`、`Referer`、`User-Agent`、`X-Requested-With` 或其他瀏覽器模擬 header。
- 不因其他客戶端或服務端使用某 header 而複製到 Android。
- 只有真實 A/B 證據證明接口必需時才新增 header。
- DATA.GOV.HK 的 JSON `Accept` 及 Google 的 API key、Android identity、FieldMask 不屬於 Citybus 精簡範圍。

### A/B 驗證

對相同輸入比較現狀與候選精簡請求，覆蓋三語、多組地點、單程、轉乘、日間與夜間案例。比較解析後的地點 token、名稱、座標、路線集合、車費、耗時、步行距離、rawInfo、路線段、方向、上下車站及完整站序，而不只比較 HTTP status 或站點數量。可重現請求資訊及回應 fixture 必須脫敏保存。

## 通知監測與 TTS

通知 channel 名稱、說明、標題、正文、狀態與 action 都使用目前語言。語言切換時沿用現有 channel id 重新建立本地化 metadata，保留用戶的 channel 設定，並立即更新活動通知。

TTS 不得跨語言體系回退：

- 繁體中文只接受粵語、香港中文或明確標記繁體中文的 Voice。
- 簡體中文只接受普通話或明確標記簡體中文的 Voice。
- 英文只接受 English Voice，優先香港或英國地區，其次其他英文地區。
- 不接受語言資訊不明確的通用 `zh` Voice，也不允許繁簡中文互相回退。

TTS 失敗建模為穩定類型：

- 沒有 TTS engine。
- 初始化失敗或初始化 timeout。
- 語音資料缺失。
- engine 不支援目前語言。
- 沒有符合語言體系的 Voice。
- audio focus 被拒絕。
- `speak()` 被拒絕。
- 播放 callback error。
- 播報沒有開始或完成而 timeout。
- TTS 已 release 或監測已停止。

啟動監測時發現能力不可用，立即 Toast 一次；運行期間每個失敗類型在同一 monitor session 最多提示一次。Toast 必須說明具體原因及「監測會繼續但不會播放語音」等影響，不只顯示通用「目前語言的語音不可用」。不提供系統語音設定入口。

診斷日誌記錄穩定錯誤類型、engine package、請求 locale、候選 locale/Voice、`isLanguageAvailable`、`setLanguage`、`speak`、audio focus、callback 原始結果、session id 與失敗階段。不得記錄 API key、完整自訂路線名稱或完整播報文本。TTS 失敗不停止 ETA 刷新、通知更新或 monitor session。

## 版面與無障礙

### 調整原則

- 不透過縮小英文字號解決空間不足。
- 固定高度改為 `wrap_content` 配合 `minHeight`，主要觸控目標至少 48dp。
- 主要操作、錯誤及狀態允許換行，不作無提示截斷。
- 窄屏無法並排的重要操作改為縱向排列；輔助 chip 或排序控件可橫向滾動。
- 路線卡內站名等長動態內容可受控省略，但完整內容要在詳情及無障礙描述中可取得。
- 保留短文字穩定策略，不強制兩端對齊、異常字距或自動縮小。

高風險畫面包括主頁標題與快捷卡、臨時查詢、排序控件、路線結果卡固定寬度區、固定高度指標列、地點候選、ETA sheet、路線詳情、監測設定、設定頁 item、Dialog、Toast、Notification 及 TalkBack 描述。

TalkBack 必須讀出完整操作、狀態及組合控件語義。所有支援語言均為 LTR，但既有 `supportsRtl` 不需移除。系統或第三方畫面只驗證流程能繼續，不要求服從 App locale。

### 視覺驗證矩陣

| 範圍 | 裝置與字體 |
| --- | --- |
| 所有 App 畫面、Bottom Sheet、Dialog、Toast、Notification | API 36.1 與 37、約 360dp portrait、font scale 1.0 |
| 主頁、路線卡、地點選擇、設定、詳情及監測 | 同上、font scale 1.3 |
| 查詢、編輯、詳情、監測、語言切換、匯入匯出等關鍵流程 | 同上、font scale 2.0 |

font scale 2.0 可增加頁面高度、換行及滾動，但不得控件重疊、核心按鈕消失、文字裁去一半、內容無法捲動到、Dialog action 越界或混合新舊語言。自動化檢查三語關鍵畫面的非白名單 ellipsis、view 越界及不可點擊 action，並保留截圖供人工審查。

## 資料與升級相容性

- 不修改 SQLite schema 或既有 `RouteConfig`。
- 不翻譯或重寫已有路線名稱及地點名稱。
- 新選擇或編輯的 Citybus/Google 地點使用當時 App 語言。
- 匯入匯出資料保持原文及既有協議，不附加語言欄位，也不重寫內容。
- 自動語言重查不增加使用次數；在未切換其他路線前的既有使用次數去重語義保持不變。
- 語言相關 cache 以語言作 key 或在切換時失效。
- 通知 channel 沿用 id，避免重置用戶設定。

## 專案長期文件

### `docs/localization-guidelines.md`

新增完整本地化指南，記錄：

- locale tag、fallback 與語言切換規則。
- 三語語氣、術語表、品牌與官方名稱。
- Android resource、placeholder、plural 及不可翻譯值規則。
- Citybus、DATA.GOV.HK、Google、TTS、官方網站路徑映射。
- 單欄位回退、快取、錯誤與診斷原則。
- UI 大字體、無障礙及測試清單。

### `AGENTS.md`

更新專案長期約束，清楚區分：

- 文件及 OpenSpec 的中文人類可讀內容繼續使用繁體中文。
- App 自有可見文字必須同時提供繁體、簡體及英文。
- 不得新增硬編碼用戶文案；新動態資料源必須說明語言與回退。

### `openspec/config.yaml`

以全專案視角翻新，不加入只屬於本 change 的 Dialog、候選參數或測試個案：

- 更新專案摘要、目前功能、module、目錄及外部資料源。
- 補充 location、transfer、settings、Google Geocoding、匯入匯出及定位排序等現況。
- 移除固定 Citybus `l=0` 及「所有 App 文案只使用繁體中文」等過時內容。
- 加入三語完整性、自然翻譯、動態資料語言、版面與真實外部服務驗證等長期規則。
- 加入「外部請求參數只有在證據證明無必要時才移除」的通用原則。
- 保留並精簡 proposal、spec、design、tasks 的全局品質要求，刪除重複或 change-specific 內容。

### `README.md`

README 翻新列入實作範圍，但只在真正修改時載入並使用用戶指定的 `create-readme` skill。屆時先重新審查完整 workspace，再以繁體中文及 GFM：

- 保留 App icon header 及官方網站。
- 更新目前功能、三語能力、架構、module、資料源及 API key 設定。
- 說明構建、驗證、OpenSpec 工作流及重要專案約束。
- 使用 GitHub admonition 呈現重要外部資料與安全注意事項。
- 連結詳細 `AGENTS.md` 及本地化指南，避免 README 重複全部內部規則。
- 不加入 LICENSE、CONTRIBUTING、CHANGELOG 等應由獨立文件承載的章節，不濫用 emoji。

## 測試與完成門檻

### JVM 與靜態測試

- 語言選擇、系統 fallback、升級預設及 AppCompat locale mapping。
- 三語 resource key、placeholder 類型及 plural 結構完全一致。
- `MissingTranslation`、`HardcodedText`、`SetTextI18n` 等 Lint 通過。
- 掃描 Toast、Dialog、Notification、無障礙文字及字串拼接；parser、fixture 等例外採窄範圍白名單並記錄原因。
- Citybus `l`、Google `languageCode`、DATA.GOV.HK 欄位與官方網站 path mapping。
- 三語 Citybus 地點、路線、詳情、停站與 ETA fixture/parser。
- 語言版本過期結果丟棄、cache 隔離及切換後自動重查。
- DATA.GOV.HK 單欄位 fallback 順序與日誌。
- TTS Voice 相容性、禁止跨繁簡回退、所有失敗類型及 session 內 Toast 去重。

### Instrumentation 與 UI

- 設定頁語言對話框、立即套用、跟隨系統及 Android 13+ App language 相容性。
- Activity 重建後的路線、臨時起終點、排序、滾動及表單狀態。
- 語言切換清除舊結果、自動重查且不增加使用次數。
- 活動通知即時改語言、TTS 重新選擇及 TTS 失敗不停止監測。
- 三語 layout matrix、font scale 1.0/1.3/2.0、截圖及 TalkBack 語義。

### 真實外部服務硬門檻

- Citybus：三語真實請求驗證地點、路線、停站、詳情及 ETA；fixture 不可替代。
- Google：對同一香港座標分別以 `zh-Hant`、`zh-Hans`、`en` 發出真實 Geocoding v4 請求，檢查地址含義、文字體系及必要欄位；mock 不可替代。
- Google 驗證需要 `GOOGLE_GEOCODING_API_KEY`、正確 package/certificate 限制及可連接 `geocode.googleapis.com` 的網絡。

目前 API key 已可注入 instrumentation build，但最近一次真實測試因主機及 emulator 均無法連接 `geocode.googleapis.com` 而 timeout。這是已知環境風險，不降低驗收標準；實作完成後必須在可連接 Google 的網絡重新通過，否則不得宣告 change 完成。

最終還需執行相關 connected tests、OpenSpec strict validation 及：

```bash
./gradlew build
```

## OpenSpec 影響

後續 proposal 建議使用 change id `add-app-multilingual-support`，新增 `app-localization` 能力，並修改與設定、UI、地點、路線、Google、ETA、通知及 TTS 有關的既有能力，包括：

- `app-settings-support`
- `app-ui-style-system`
- `place-search-api`
- `citybus-route-query-api`
- `bus-route-query`
- `google-reverse-geocoding-resolver`
- `citybus-first-leg-eta`
- `citybus-eta-arrivals-sheet`
- `notification-bar-monitoring`
- `monitor-voice-playback-diagnostics`

已完成但尚未 archive 的 `add-saved-route-import-export`、`rank-saved-routes-by-location` 及 `stabilize-short-text-layout` 不合併到新 change。新 change 只定義其畫面與流程必須符合三語及版面要求，不重複或改寫原功能語義。

## 已確認決策

- 採用 Android 原生 resource + AppCompat locale + 集中語言策略。
- 提供跟隨系統、繁體中文、簡體中文及英文。
- 新裝與升級用戶都預設跟隨系統；不支援的系統語言回退繁體中文。
- 語言選擇立即套用，明確語言選項使用自稱。
- 動態資料、通知、TTS、分享、意見回饋及外部連結跟隨 App 語言。
- 切換語言保留查詢輸入並自動重查，不增加路線使用次數，不保留舊語言結果。
- 既有保存及匯入的路線和地點名稱保持原文。
- Citybus 整體請求失敗不改用繁體中文；單個官方欄位缺失可按既定順序回退。
- TTS 不跨繁簡或語言體系回退；失敗 Toast 必須說明具體原因。
- 不提供系統語音設定入口或語音預覽。
- `bsearch_p3.php` 保留 `limit` 及 `timestamp`。
- `getp2pstopinroute.php` 的 `ginfo`、`lid` 在確認無作用前保留。
- UI 必須通過三語、360dp、API 36.1/37 及大字體驗證。
- Google 及 Citybus 三語真實驗證是完成門檻。
- `AGENTS.md`、本地化指南、全局 `openspec/config.yaml` 及 README 都納入本次 change。
- README 只在真正修改時使用 `create-readme` skill。

## 未決問題

無。Google 網絡可達性是執行環境門檻，不是待決產品設計。
