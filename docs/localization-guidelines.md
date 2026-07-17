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
- 用戶自訂路線名、已保存或匯入的地點名、第三方返回地址、站名、目的地與備註保持原文；不得機器翻譯或因切換語言改寫 SQLite／匯入資料。

常用術語基線：

| 語義 | 繁體 | 簡體 | English |
| --- | --- | --- | --- |
| saved journey | 常用路線 | 常用路线 | Regular journey |
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
- `bsearch_p3.php` 必須保留 `q`、`limit=100`、`timestamp` 及空顯式 header；路線查詢保留座標、香港時間、`ws=1.3`、`leg=2`、`m1` 與 `l`。
- Citybus mobile 不附加 Cookie、Referer、User-Agent、X-Requested-With 等瀏覽器 header。刪除其他 query 參數前必須以三語、多樣本、語義等價的 A/B 證據證明安全。

## 切換生命週期

- 語言與外觀使用獨立偏好，Application 在首個 Activity 前套用兩者。
- locale recreation 保存目前頂層 destination、常用 route id、搜尋起終點與未提交文字、兩個 owner 的排序／滾動及是否曾提交有效查詢。
- 舊語言結果不寫入新畫面；曾提交的有效常用／搜尋上下文以原座標自動重查，未提交表單不查詢。自動重查不增加常用路線使用次數。
- 進行中的監控保留 session、排程及 channel id，立即以新語言更新通知、改用新 Citybus 語言並停止舊 utterance。

## TTS

- 繁體只接受粵語、香港／澳門／台灣中文或明確 `Hant` Voice；簡體只接受普通話或明確 `Hans` Voice；英文只接受 `en` 並優先 HK／GB。
- 模糊 `zh` 不可用，繁簡不得互相 fallback。
- no engine、初始化失敗／逾時、missing data、unsupported locale、無相容 Voice、audio focus、speak rejected、播放錯誤／逾時及 released 都是穩定失敗原因。
- 能力失敗立即提示；runtime 原因在同一監控 session 最多提示一次。提示必須說明具體原因及「監控會繼續但不播報」，不得只寫「目前語言的語音不可用」。
- 不提供系統語音設定或試聽入口。日誌可記錄 engine、候選 locale／Voice、setLanguage、focus、speak 與 callback 結果，不記錄 API key、完整用戶路線名或 utterance。

## 版面與無障礙

- 三語均須在淺色／深色、約 360dp portrait 與 font scale 1.0／1.3／2.0 可操作。
- 長英文與大字體使用 `wrap_content + minHeight`、換行、彈性 weight、水平／垂直滾動；不得靠縮字、核心文字單行裁切或重疊處理。
- 核心 action 的觸控目標至少 48dp。Dialog 與 Bottom Sheet 的內容和 action 必須可達。
- 快捷卡可對用戶路線名與站名作有限行數的 compact 展示，但詳情及 `contentDescription` 必須保留完整文字。
- 深色模式使用語意色 token；只有 launcher／品牌圖像與經對比驗證的路線識別色可保持固定色。

## 驗證門檻

- `LocaleResourceContractTest`、硬編碼掃描、provider mapping、parser、cache／stale callback、TTS Voice policy 與 formatter 測試必須通過。
- `./gradlew build` 必須通過，包含 unit test、lint 與 debug／release assemble。
- 模擬器／實機按 `docs/localization-validation-matrix.md` 驗證三語、明暗、字體及無障礙。
- Citybus、DATA.GOV.HK 及 Google 必須做真實三語驗證；Google mock／fixture 成功或網絡逾時不能替代真實服務硬門檻。
