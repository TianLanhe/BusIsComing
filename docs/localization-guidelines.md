# 本地化指南

## 支援範圍

BusIsComing 的 App runtime 支援以下選擇：

| 選擇 | Android locale | 實際語言 |
| --- | --- | --- |
| 跟隨系統 | 空的 AppCompat locale list | 依系統 locale 解析 |
| 繁體中文 | `zh-Hant-HK` | 香港繁體中文 |
| 簡體中文 | `zh-Hans-CN` | 簡體中文 |
| English | `en` | 英文 |

跟隨系統時，任何 `en` 使用英文；`zh-Hant` 或 HK／MO／TW 中文使用繁體；`zh-Hans` 或 CN／SG 中文使用簡體；裸 `zh` 及其他不支援語言回退繁體。文件、OpenSpec 與程式註解中的中文一律使用繁體；這項規則不代表 App 只需繁體資源。

## 文案與術語

- 繁體使用自然、簡潔的香港實用書面語，避免直接套用台灣用語或逐字翻譯英文。
- 簡體應獨立審校，不以自動繁簡轉換代替翻譯；使用自然的內地通用介面表達，但保留香港專有名稱原文。
- 英文優先短句、主動語態與通勤場景常用詞，不照搬中文語序。
- `BusIsComing`、`Citybus`、`Google Maps`、`ETA`、`HK$`、路線號、email、版本、網址、API 名稱及檔案副檔名不翻譯。
- 用戶自訂行程名、已保存或匯入的地點名、第三方返回地址、路線號、站名、目的地與備註保持原文；不得機器翻譯或因切換語言改寫 SQLite／匯入資料。

行程與路線是兩個不同概念：

- **行程**是用戶命名並保存的起點與終點配置，例如「上班」「上學」或「回家」。行程不保存、不鎖定亦不代表某一條 Citybus 查詢結果。
- **路線**是按一個行程或臨時起終點查詢後返回的乘車方案，例如 `118`、`8X` 或 `85 → 106`。
- **乘車段**是一條路線中的單段巴士服務；`85 → 106` 是一條包含兩個乘車段的路線。此詞只在詳情或換乘結構需要時使用。
- 頁面標題、首次引導及脫離上下文的位置使用「常用行程」，上下文清楚時可使用「行程」；查詢操作、結果、路線卡、ETA、詳情和監控繼續使用「路線」。
- `RouteConfig`、resource key、SQLite `route_configs` 與 `.bicroutes` 是兼容保留的內部或協議名稱，不得據此把 runtime 的已保存配置直譯為「路線」。

常用術語基線：

| 語義 | 繁體 | 簡體 | English |
| --- | --- | --- | --- |
| saved journey | 常用行程／行程 | 常用行程／行程 | Regular journey / journey |
| queried route | 路線 | 路线 | Route |
| route leg | 乘車段 | 乘车段 | Leg |
| start / destination | 起點／終點 | 起点／终点 | Start / destination |
| live arrival | 候車時間／到站時間 | 候车时间／到站时间 | Live arrival |
| notification monitor | 通知欄監控 | 通知栏监控 | Notification monitoring |
| appearance | 外觀主題 | 外观主题 | Appearance |
| import / export | 匯入／匯出 | 导入／导出 | Import / export |

## Android 資源規則

- 繁體預設資源放在 `values/`，簡體放在 `values-b+zh+Hans/`，英文放在 `values-en/`。
- App 自有可見文字、Toast、錯誤、通知、TTS、分享文案及 `contentDescription` 必須使用資源；model／repository 回傳結構化狀態，不回傳可見中文句子。
- 三個目錄必須有相同 key，格式 placeholder 的索引及類型必須一致。數量文案優先使用不會產生單複數錯誤的自然表達；需要完整單複數時使用 `plurals`。
- XML 不得新增硬編碼文案；Kotlin source scan 只可豁免 parser 的第三方原始標籤、fixture 及有說明的品牌／技術常量。
- 切換語言使用 AppCompat per-app locale，不手動修改 `Resources` configuration，也不維護第二套 runtime 字典。

## 動態資料與回退

每個網路工作開始時捕獲不可變 `LanguageSnapshot`，cache key、in-flight 合併及 callback 都必須包含或核對語言版本。

| 實際語言 | Citybus `l` | Google | DATA.GOV.HK 欄位順序 | 網站路徑 |
| --- | --- | --- | --- | --- |
| 繁體 | `0` | `zh-Hant`, `HK` | `tc → sc → en` | `/zh-hant/` |
| 簡體 | `2` | `zh-Hans`, `HK` | `sc → tc → en` | `/zh-hans/` |
| English | `1` | `en`, `HK` | `en → tc → sc` | `/en/` |

- DATA.GOV.HK 只可對單一空欄位採用上述官方欄位回退，並保留實際欄位語言；不得把回退值寫回保存資料。
- Citybus 或 Google 的整體請求失敗不得改用另一語言重試，不得顯示 mock 名稱或舊語言 cache。
- 上表的 Google 語言只約束 App 主動送出的 Geocoding 等請求。Google Maps 底圖內建道路、地名、商戶及 attribution 由 Maps SDK／Google／裝置環境決定，可能不完全跟隨 App locale；App 自有的詳情摘要、marker title、錯誤、定位／全覽控件及 `contentDescription` 仍必須使用目前 App 語言資源或同語言 Citybus 資料。
- Citybus 地點、路線、站序與 ETA 的參數及解析契約由 `citybus-route-query-and-eta.md` 維護；本文件只約束它們必須使用同一 `LanguageSnapshot`，且不得以跨語言重試掩蓋失敗。
- 更新檢查打開網站時依實際語言選擇上述路徑；由 Play 確認更新後，網站 `versionName` 只有在 `versionCode` 精確一致時才可展示。完整渠道契約見 `app-update-check.md`。

## 切換生命週期

- 語言與外觀使用獨立偏好，Application 在首個 Activity 前套用兩者。
- locale recreation 保存目前頂層 destination、常用行程 id（內部仍為 route id）、搜尋起終點與未提交文字、兩個 owner 的排序／滾動及是否曾提交有效查詢。
- 舊語言結果不寫入新畫面；曾提交的有效常用／搜尋上下文以原座標自動重查，未提交表單不查詢。自動重查不增加常用行程使用次數。
- 進行中的監控保留 session、排程及 channel id，立即以新語言更新通知、改用新 Citybus 語言並停止舊 utterance。

## TTS

- 繁體只接受粵語、香港／澳門／台灣中文或明確 `Hant` Voice；簡體只接受普通話或明確 `Hans` Voice；英文只接受 `en` 並優先 HK／GB。
- 模糊 `zh` 不可用，繁簡不得互相 fallback。
- no engine、初始化失敗／逾時、missing data、unsupported locale、無相容 Voice、audio focus、speak rejected、播放錯誤／逾時及 released 都是穩定失敗原因。
- 能力失敗立即提示；同一監控 session 內，每一種穩定 runtime 失敗原因最多提示一次，不同原因可以各提示一次。提示必須說明具體原因及「監控會繼續但不播報」，不得只寫「目前語言的語音不可用」。
- 不提供系統語音設定或試聽入口。日誌可記錄 engine、候選 locale／Voice、setLanguage、focus、speak 與 callback 結果，不記錄 API key、完整用戶行程名或 utterance。

## 版面與無障礙

- 三語均須在淺色／深色、約 360dp portrait 與 font scale 1.0／1.3／2.0 可操作。
- 長英文與大字體使用 `wrap_content + minHeight`、換行、彈性 weight、水平／垂直滾動；不得靠縮字、核心文字單行裁切或重疊處理。
- 核心 action 的觸控目標至少 48dp。Dialog 與 Bottom Sheet 的內容和 action 必須可達。
- 快捷卡可對用戶行程名與站名作有限行數的 compact 展示，但詳情及 `contentDescription` 必須保留完整文字。
- 路線詳情的文字時間線是地圖的等價可讀內容；站點、轉乘、geometry 或位置狀態不得只靠顏色、marker 或圖例傳達，地圖不可用時仍須保留目前語言的完整操作與錯誤說明。
- 監控通知健康、系統設定 fallback、精確鬧鐘及電池最佳化說明均屬 App 自有文案，三語資源必須表達阻斷、警告、未知與可降級繼續的差異。
- 深色模式使用語意色 token；只有 launcher／品牌圖像與經對比驗證的路線識別色可保持固定色。

## 驗證門檻

- `LocaleResourceContractTest`、`JourneyRouteTerminologyContractTest`、硬編碼掃描、provider mapping、parser、cache／stale callback、TTS Voice policy 與 formatter 測試必須通過。
- `./gradlew build` 必須通過，包含 unit test、lint 與 debug／release assemble。
- 模擬器／實機按 `docs/localization-validation-matrix.md` 驗證三語、明暗、字體及無障礙。
- Citybus、DATA.GOV.HK 及 Google 必須做真實三語驗證；Google mock／fixture 成功或網絡逾時不能替代真實服務硬門檻。
