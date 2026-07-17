## Context

BusIsComing 目前只有 `values/strings.xml`，沒有 locale resource directory；XML、Activity、Fragment、Adapter、Bottom Sheet、通知、Toast、TTS、formatter 及部分 model 仍包含直接顯示的繁體中文。三模組導航合併後，`MainActivity` 承載 `FrequentRoutesFragment`、`SearchFragment`、`SettingsFragment` 與底部導航；常用與搜尋各自持有 `RouteQueryState`／`RouteQueryCoordinator`，設定頁的語言 item 仍只顯示「暫不支援」。Citybus 地點及路線請求固定 `l=0`，Google reverse geocoding 固定 `languageCode=zh-Hant`，DATA.GOV.HK ETA 保留 `dest_tc`／`rmk_tc`。因此即使單獨替換畫面文字，兩個查詢 owner、動態資料、cache、活動通知及語音仍會出現語言不一致。

現有 App 使用 XML + AppCompat + Material Components、輕量 Repository 分層、callback／generation 過期控制、Foreground Service 及 TextToSpeech。本設計須沿用這些邊界，不在 Activity 或 Fragment 分散 HTTP、解析、SQLite 或語言判斷，也不引入另一套執行時字典架構。

本 change 自行定義並保存 `zh-Hant`、`zh-Hans`、`en` 的語氣、術語與審校規則，不依賴外部倉庫或本機路徑作為後續實作前提。暗色主題 change 同時存在，因此語言與外觀偏好、AppCompat 重建及資源限定符必須可組合；Google 三語真實 instrumentation 使用有效 key、package／certificate identity 及可連接 `geocode.googleapis.com` 的網絡作完成硬門檻。

## Goals / Non-Goals

**Goals:**

- 以 Android 原生資源支援跟隨系統、繁體中文、簡體中文及英文。
- 令所有 App 自有 UI、錯誤、通知、TTS、無障礙、分享與外部連結使用同一實際語言。
- 令常用、搜尋、設定三個頂層 destination、底部導航與次級頁在語言切換後保持正確任務與安全狀態。
- 令 Citybus、DATA.GOV.HK 及 Google 動態資料跟隨語言，並防止舊請求污染新語言畫面。
- 保留用戶自訂及既有保存資料原文，明確區分單欄位官方回退與整體請求失敗。
- 令三語 UI 在淺色及深色模式、窄屏、大字體、Bottom Sheet、Dialog 及通知中保持可讀及可操作。
- 將 TTS 不可用拆成可行動的用戶提示與可排查的非敏感診斷。
- 只在真實證據證明安全時精簡 Citybus 參數，並沉澱長期文件與自動化防回退規則。

**Non-Goals:**

- 不修改任何外部網站或後端、SQLite schema、路線匯入匯出協議、排序或使用次數語義。
- 不翻譯用戶路線名稱、既有／匯入地點名稱或第三方原始 fixture。
- 不機器翻譯第三方資料，不在 Citybus 整體失敗時改用繁體中文重試。
- 不控制權限 Dialog、文件選擇器、分享面板、TTS engine 設定、支付或瀏覽器等系統／第三方畫面。
- 不新增系統語音設定入口或語音試聽。
- 不為此 change 進行無關架構重寫或新增另一套字典／後端服務。

## Decisions

### 1. 使用 Android 原生 resource 與 AppCompat per-app locale

繁體中文放在預設 `values/`，簡體中文放在 `values-b+zh+Hans/`，英文放在 `values-en/`；`locales_config.xml` 宣告 `zh-Hant-HK`、`zh-Hans-CN`、`en`。透過 AppCompat 套用明確語言；空 locale list 表示跟隨系統。

跟隨系統按 language、script、region 解析：任何英文使用英文；`zh-Hant` 或 HK／MO／TW 中文使用繁體；`zh-Hans` 或 CN／SG 中文使用簡體；裸 `zh` 及其他不支援語言回退繁體。新安裝與升級用戶首次都跟隨系統；明確選擇持久保存。

**原因**：Android 原生支援 XML、plural、placeholder、configuration change、Android 13+ App language 及 Lint。

**否決替代**：不採用 Kotlin 三語字典，因它會繞過 resource resolution、plural、XML、系統語言與 MissingTranslation；也不在各 UI／model 分散判斷 `Locale`。

### 2. 集中語言策略與不可變 LanguageSnapshot

新增單一語言策略／repository，提供用戶選擇、實際 locale、Citybus `l`、Google `languageCode`、DATA.GOV.HK 欄位順序、TTS 語音體系、官方網站路徑及單調遞增版本。Activity、Fragment、Repository、Service 及 formatter 只消費該策略。

每次網絡工作捕獲不可變 snapshot；回調、cache 寫入及 UI 更新前核對版本。切換語言時保存選擇、重建 Activity、取消或作廢常用與搜尋 owner 的舊工作、隔離 cache，並保留目前頂層 destination、常用路線 id、搜尋起終點、各自排序／滾動及未提交輸入。每個曾發起有效查詢的 owner 以原座標自動重查；從未提交的搜尋表單不發出查詢。自動重查不得增加使用次數；失敗只顯示新語言錯誤。

`MainActivity` 負責把語言版本變更交給兩個查詢 owner；`RouteQueryCoordinator` 的有效性同時由 owner generation 與 `LanguageSnapshot.version` 決定。`SearchFragment.onDestinationHidden()` 等 destination 切換只處理 owner 可見性，不得把舊語言結果重新視為有效。語言切換與普通底部導航切換必須使用不同事件語義。

**原因**：現有查詢已使用 generation 避免舊 ETA 更新，語言版本可沿用同一模式，無需引入新的 reactive framework。

**否決替代**：不只依賴 Activity recreation；Foreground Service、cache 與晚到 callback 不會因 UI 重建自動變正確。亦不讓各 Fragment 自行猜測 locale，或保留舊語言結果作「暫時 fallback」，避免交叉 owner 污染與混合語言。

### 3. App 自有文字全部資源化，model 返回結構化語義

按功能拆分 common、main、route、settings、monitor、error、accessibility 等 string／plural 資源。資料層返回穩定狀態或錯誤類型，UI／Notification 層才取得 locale resource。真正語言無關的品牌、email、版本、路線號才可 `translatable=false`。

翻譯獨立審校：繁體採香港實用書面語；簡體不是字形轉換；英文不保留中文句式。固定術語及外部品牌寫入 `docs/localization-guidelines.md`。資源測試比較三語 key、placeholder 類型及 plural；Lint 與窄範圍 source scan 阻止新的硬編碼文案。Parser label、regex、fixture 以明確白名單排除。

**原因**：把錯誤文字留在 Repository 或 model 會令 Service、Toast 與頁面各自形成語言分支，也難以測試 placeholder 完整性。

**否決替代**：不以繁體 default fallback 掩蓋缺失簡體／英文 key；除真正 non-translatable 資源外，三語必須完整。

### 4. 動態資料使用明確 provider mapping

| 實際語言 | Citybus `l` | Google `languageCode` | DATA.GOV.HK 主要欄位 | 官方網站首頁 | 私隱政策 |
| --- | ---: | --- | --- | --- | --- |
| 繁體 | `0` | `zh-Hant` | `name_tc`、`dest_tc`、`rmk_tc` | `/zh-hant/` | `/zh-hant/privacy/` |
| 簡體 | `2` | `zh-Hans` | `name_sc`、`dest_sc`、`rmk_sc` | `/zh-hans/` | `/zh-hans/privacy/` |
| 英文 | `1` | `en` | `name_en`、`dest_en`、`rmk_en` | `/en/` | `/en/privacy/` |

Google 保持 `regionCode=HK`，cache key 包含語言。Citybus 地點、路線、stop map、詳情及 ETA 使用同一 snapshot，parser 支援三語上游標籤。DATA.GOV.HK 單欄位缺失時按 `tc→sc→en`、`sc→tc→en`、`en→tc→sc` 回退，並記錄 source／field／actual language；不寫回保存資料。

**原因**：各 provider 的語言契約不同，集中 mapping 可防止 `l`、JSON 欄位、Google 與 TTS 各自漂移。

**否決替代**：不對整個 Citybus 或 Google 失敗改用另一語言；這會隱藏 parser／接口問題並產生錯誤保存資料。不機器翻譯站名或地址。

### 5. Citybus 參數採安全優先的 A/B 審核

- `bsearch_p3.php` 保留 `l,q,limit,timestamp`。
- `ppsearch_p3.php` 保留 `slat,slon,elat,elon,t,ws,leg,m1,l`。
- `showstops2.php` 保留 `r,l`。
- `getp2pstopinroute.php` 預設保留 `info,ginfo,lid,l`。

Citybus mobile 繼續不顯式加入 Cookie、Referer、User-Agent、X-Requested-With 或其他瀏覽器模擬 header。`ginfo` 已知改變 raw HTML 的時間摘要，`lid` 現有樣本不足；只有逐項三語真實 A/B 證明 HTTP、parse 及完整 RouteDetail 語義正確時才可移除，否則保留。比較須覆蓋多關鍵詞、單程／轉乘、日間／夜間，保存脫敏請求及 fixture。

**原因**：Android 現有請求已不攜帶瀏覽器模擬 header；為追求參數數量而刪除具業務語義的 query 會增加上游風險。

**否決替代**：不複製任何瀏覽器或其他服務端請求模板；不以單次 HTTP 200、body 大小或站點數量當作語義等價證據。

### 6. 通知及 TTS 共享語言，但 Voice 嚴格限制語言體系

活動監測沿用 session 及 channel id。語言切換重新建立 channel 名稱／說明及通知內容，保留用戶 channel 設定；停止舊語言 utterance，下一次播報使用新 snapshot。

繁體只接受粵語、香港中文或明確繁體 Voice；簡體只接受普通話或明確簡體 Voice；英文只接受 English Voice。模糊通用 `zh` 不接受，繁簡不得互相 fallback。

TTS failure 使用結構化類型：no engine、init failure／timeout、missing data、unsupported locale、no compatible Voice、audio focus denied、speak rejected、playback callback error、utterance timeout、released／stopped。啟動時能力失敗立即 Toast 一次；runtime 每個原因每 session 最多一次。監測、ETA 與通知繼續運作。

日誌記錄 engine package、requested/candidate locale/Voice、availability、setLanguage、speak、focus、callback、session／stage，不記錄 key、完整自訂路線名稱或 utterance。

**原因**：既有繁體→簡體 fallback 可能播出錯誤語系，而通用「語音不可用」不能定位常見裝置差異。

**否決替代**：不跨語系求「總能播」，也不增加系統語音設定或試聽入口；用戶需要的是可靠監測與明確原因。

### 7. 以彈性版面而非縮字處理三語長度

固定高度改為 `wrap_content + minHeight`，重要操作至少 48dp；主要狀態及 action 可換行，窄屏下重要並排 action 改縱向，輔助 chip 可橫向滾動。站名可在 compact card 受控省略，但詳情與 contentDescription 必須完整。不得以 autosize 把英文縮至不可讀。

高風險範圍包括底部導航、常用快捷卡與首次引導、搜尋表單／候選／摘要、兩套排序與結果卡、設定中的外觀／語言／路線資料列、ETA／詳情／監測 Bottom Sheet、Dialog、Toast 及 Notification。驗證覆蓋 API 36.1／37、約 360dp portrait；全部畫面 font 1.0、高風險 font 1.3、關鍵流程 font 2.0，並以三語 × 淺／深色交叉矩陣確認 locale resource 不會繞過 `values-night` 語意色。

**原因**：現有多個固定 40–64dp 行高及固定寬度在 English／大字體會裁切；自然換行及 scroll 比縮字更可讀。

**否決替代**：不要求所有卡片無限增高；次要動態站名可受控 ellipsis，但核心 action／error 不可截斷。

### 8. 長期規則分層保存

- `AGENTS.md` 保存「文件／OpenSpec 中文用繁體、App runtime 文案三語完整」等硬約束。
- `docs/localization-guidelines.md` 保存語氣、術語、provider、fallback、TTS、layout、test 詳細規則。
- `openspec/config.yaml` 以全專案視角更新架構、現有功能、動態語言與外部請求證據規則，移除固定 `l=0` 及單語過時內容，不放本 change 的一次性參數候選或 Dialog 細節。
- `README.md` 在真正修改時載入 `create-readme` skill，重新審查 workspace，更新功能、架構、配置、資料源、驗證及 OpenSpec 入口；詳細內規以連結避免重複。

**原因**：只把規則放在 change archive 後不容易被後續 agent 主動發現；全部塞入 AGENTS 或 README 又會造成冗長及重複。

**否決替代**：不把 `openspec/config.yaml` 改成只服務本 change，也不在 propose 階段提前修改 README。

### 9. 語言與外觀使用獨立偏好及可組合重建

語言選擇與外觀模式使用不同的穩定儲存鍵及 domain model。若暗色主題 change 已建立 `BusIsComingApplication`，本 change 擴充同一 Application／啟動協調，而不建立第二個 Application。啟動時兩個偏好都在首個 Activity inflate 前套用；修改語言只呼叫 per-app locale API，修改外觀只呼叫 night mode API，各自不得清除或覆寫另一偏好。

`values-b+zh+Hans`／`values-en` 只承載 locale 相關資源，明暗色繼續由 `values`／`values-night` 同名語意 token 提供。設定 Fragment 中 `外觀主題` 保持位於 `語言` 前；任何一次選擇只觸發對應 AppCompat 變更，不再額外手動 `recreate()`，避免重複重建或循環。重建後仍回到 `設定` destination，並同時顯示正確語言摘要與外觀摘要。

**原因**：locale 與 uiMode 都會透過 AppCompat 觸發 configuration recreation；集中啟動與獨立偏好可讓兩個 change 以任意實作順序合併，而不出現最後套用者覆寫另一維度的情況。

**否決替代**：不把語言與外觀合併為單一 enum 或單一設定值；兩者是正交選擇，合併會產生不必要組合、遷移風險及設定互相重設。

## Risks / Trade-offs

- **[三語遷移範圍大，容易漏掉 Service／formatter／無障礙字串]** → 建立資源 key／placeholder／plural 完整性測試、Lint、窄範圍 source scan 及畫面 inventory；按模組遷移。
- **[AppCompat locale 重建丟失查詢或表單狀態]** → 在切換前保存目前 destination、常用 route id、搜尋起終點、兩個 owner 的排序／scroll 及 form state；instrumentation 覆蓋 Activity recreation。
- **[三模組各自持有查詢狀態，語言切換只清理其中一個 owner]** → 由宿主廣播明確的語言版本變更，兩個 `RouteQueryCoordinator` 同時使舊 generation 失效，並對各自有效上下文重查。
- **[舊語言並發結果寫入新畫面或 cache]** → snapshot 版本同時保護 callback、cache put、ETA incremental update 及詳情更新；切換時取消可取消工作。
- **[Citybus 三語 HTML 標籤或結構不同]** → 保留三語 fixture、真實 endpoint 驗證與 parser regression；整體 parse 失敗不 fallback 繁體。
- **[DATA.GOV.HK 單欄位缺失造成混合語言]** → 只允許明確官方 fallback 並記錄；不把 fallback 寫入保存資料。混合單欄位是可用性與一致性的已知取捨。
- **[TTS engine 對 script／region 回報不一致]** → 以 Voice locale 與明確允許集合雙重判斷，記錄所有原始結果；不接受模糊 `zh`。
- **[通知 channel metadata 更新受 OEM 行為影響]** → 沿用 id 保留設定，重新 create channel 並更新 active notification；在 API 36.1／37 及至少一個真機／等效環境驗證。
- **[font scale 2.0 使畫面顯著變長]** → 接受 wrapping／scrolling 作取捨，但禁止重疊、不可達 action 或核心裁切。
- **[locale 與 night mode 連續變更導致雙重重建或偏好互相覆寫]** → 使用獨立 store、同一 Application 啟動協調及單一維度 delegate 呼叫；加入三語 × 明暗與連續切換測試。
- **[Google 網絡、API 權限或 Android application restriction 不可用]** → 以不包含 key／回應正文的安全 reason 分類排查，mock／fixture 只作回歸；在 Google 可達網絡執行三語真實 instrumentation，未通過不得完成 change。
- **[全局 config／README 翻新引入不相關內容]** → 只記錄已由 code/spec 證實的目前能力與長期規則，README 使用指定 skill 並審查 diff，不新增 License／Contributing／Changelog 章節。

## Migration Plan

1. 先新增語言 domain、locale config、偏好／升級預設及 unit tests，並與既有或待合併的外觀 store／Application 啟動路徑整合，不改資料庫。
2. 建立三語 resource skeleton 與完整性測試，再按 common、settings、main／route、monitor／notification、errors／accessibility 遷移文案。
3. 將 provider mapping 注入地點、路線、stop map、詳情、ETA 及 Google；加入三語 fixture、cache／snapshot 及 stale result tests。
4. 實作頂層設定 Fragment 的語言對話框、三個 destination state 恢復，以及常用／搜尋 owner 切換後各自自動重查。
5. 改造 Notification／TTS 語言與診斷，保持舊 channel id 及 monitor session。
6. 調整高風險 XML，完成三語 × 淺／深色、大字體、TalkBack instrumentation 與截圖審查。
7. 執行 Citybus 真實三語及參數 A/B；只有證據通過才考慮逐項刪除 `ginfo`／`lid`。
8. 更新長期文件、全局 OpenSpec config；修改 README 時載入 `create-readme` skill。
9. 執行 `./gradlew build`、connected tests、OpenSpec strict validation、Citybus 及 Google 真實硬門檻後提交。

回滾不需要資料 migration：移除新 locale preference 或回退程式後，既有 SQLite、匯入檔案及 channel id 仍可使用。若 provider 三語解析出現回歸，可回退該 provider mapping／parser 而不改寫用戶資料；不得以保存錯誤語言資料作臨時補救。

## Open Questions

無。Google 網絡可達性是實作完成門檻，不是未裁決的產品行為。
