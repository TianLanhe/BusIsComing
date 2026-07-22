## 1. 基線、清單與測試護欄

- [x] 1.1 檢查 `git status --short`、目前裝置與現有測試基線，執行相關 JVM 測試並記錄任何非本 change 造成的既有失敗。
- [x] 1.2 盤點 `app/src/main` 全部 XML／Kotlin／Manifest 中的 App 自有文字、通知、Toast、TTS、無障礙內容與格式化器，建立可核對的遷移清單及 parser／fixture 窄範圍白名單。
- [x] 1.3 先新增語言解析、provider mapping、資源 key／placeholder／plural 完整性與硬編碼掃描的失敗測試，覆蓋繁體、簡體、英文及不可翻譯例外。
- [x] 1.4 建立三語 UI／動態資料驗收矩陣，列出常用／搜尋／設定三個頂層 Fragment、底部導航、所有次級 Activity、Bottom Sheet、Dialog、Toast、Notification、淺／深色、關鍵流程、API level、360dp 闊度及 font scale 1.0／1.3／2.0 覆蓋點。

## 2. 語言核心與 Android locale 接入

- [x] 2.1 在獨立語言 domain／repository 中實作跟隨系統、繁體、簡體、英文選擇、實際 locale 解析、持久偏好與單調語言版本，並讓裸 `zh`／不支援語言回退繁體。
- [x] 2.2 實作集中 `LanguageSnapshot`，提供 Citybus `l`、Google `languageCode`／`regionCode`、DATA.GOV.HK 欄位順序、TTS 語言體系及官方網站首頁／私隱政策路徑。
- [x] 2.3 新增 `locales_config.xml`、Manifest locale config 與 AppCompat per-app locale 接入；驗證新安裝及升級無偏好時都跟隨系統，明確選擇跨啟動保持。
- [x] 2.4 讓 Activity、Fragment、Repository 與 Foreground Service 從同一語言來源取得 snapshot；由 `MainActivity` 將語言版本變更分派給常用與搜尋 query owner，而不在各 component 分散比較 `Locale`。
- [x] 2.5 與外觀主題共用同一 Application／啟動協調，將語言與外觀保存為獨立偏好；驗證修改任一設定不覆寫另一設定，且每次操作不以額外手動 `recreate()` 造成雙重重建。
- [x] 2.6 完成語言核心 JVM／instrumentation 測試，覆蓋 en 任意地區、Hant/HK/MO/TW、Hans/CN/SG、裸 zh、其他語言、明確選擇、返回跟隨系統及與三種外觀偏好的組合。

## 3. 三語資源、翻譯與硬編碼清理

- [x] 3.1 在 change 與 `docs/localization-guidelines.md` 中建立自足的三語術語與語氣基線，確認繁體香港實用書面語、獨立簡體翻譯、自然英文及品牌／技術常量範圍，不依賴外部倉庫或本機路徑。
- [x] 3.2 建立 `values/` 繁體預設、`values-b+zh+Hans/` 簡體及 `values-en/` 英文資源骨架，按 common、errors、accessibility 與 plurals 分類並保持 placeholder 類型一致。
- [x] 3.3 遷移底部導航、常用 Fragment 的首次引導／快捷路線／排序／查詢狀態、搜尋 Fragment 的表單／摘要／排序／結果卡及使用次數相關 App 自有文字，保留用戶路線／地點原文。
- [x] 3.4 遷移新增／編輯／複製／管理路線、地點候選、目前位置、路線匯入匯出及相關 Dialog／Toast／無障礙文字。
- [x] 3.5 遷移路線詳情、ETA 班次、監測設定、通知 channel／內容／action、TTS 播報及所有原因明確的語音錯誤提示。
- [x] 3.6 將 model／repository 中會直接顯示的文字改為結構化狀態或穩定錯誤類型，讓 UI／Notification 層使用目前 locale 資源格式化。
- [x] 3.7 修正繁體既有文案為自然香港用語，對簡體及英文逐項獨立審校，並執行資源完整性、Lint 與硬編碼掃描直到只剩有理由的白名單。

## 4. 設定頁與對外內容

- [x] 4.1 在 `SettingsFragment` 的語言 item 顯示目前選擇副標題；跟隨系統時同時顯示實際語言，保持 `外觀主題`、`語言`、`路線資料` 的既有順序，並讓 item 在不同字體／語言下可增高。
- [x] 4.2 實作立即生效的單選 Dialog，固定顯示 `繁體中文 / 简体中文 / English` 自稱、以目前語言顯示跟隨系統、標記目前選項且不提供儲存按鈕。
- [x] 4.3 語言切換重建宿主後仍選中設定 destination，移除舊「暫不支援語言切換」行為，同時保持應用評分與檢查更新仍為目前語言的暫不支援提示，且外觀摘要與偏好不變。
- [x] 4.4 將分享文案與官方網站首頁、意見回饋主旨／正文、關於我們簡介及私隱政策 URL 按三語 mapping 輸出；品牌、email、版本及設備值保持原值。
- [x] 4.5 新增設定 UI 與 Intent contract 測試，覆蓋四個選項、立即生效、subtitle、三語 URL／文案及外部 Intent 失敗提示。

## 5. 語言切換生命週期與查詢狀態

- [x] 5.1 在 locale recreation 前後保存並恢復目前頂層 destination、常用 route id、搜尋起終點、兩個 owner 各自的排序／scroll 及未提交表單輸入，明確排除舊語言結果資料。
- [x] 5.2 將語言版本加入 `RouteQueryCoordinator`、地點、路線、詳情、站點預覽、ETA、Google 地址的 callback／cache put／in-flight 合併邊界；同時驗證 owner generation 與語言版本，取消可取消工作並忽略晚到舊結果。
- [x] 5.3 語言切換後清除常用與搜尋 owner 的舊候選與結果；對每個曾發起有效查詢的上下文以原座標自動重查，未提交搜尋表單不查詢，重查失敗只顯示新語言錯誤且保留起終點供重試。
- [x] 5.4 接入既有路線使用去重 policy，確保語言自動重查不增加使用次數，也不改變未切換其他路線前的 session 去重狀態。
- [x] 5.5 新增 recreation、普通 destination 切換、連續語言／外觀切換、兩個 owner 舊 callback 晚到、cache 污染、常用／搜尋重查及使用次數不變的單元與 instrumentation 回歸測試。

## 6. Citybus 地點、路線、停站與詳情三語化

- [x] 6.1 讓 `CitybusPlaceSearchRepository` 使用 snapshot 的 `l=0/2/1`，明確保留 `q`、`limit=100`、`timestamp` 及空顯式 header，並更新 URL／狀態測試。
- [x] 6.2 為 `bsearch_p3.php` 補充繁體、簡體、英文正常／無結果／錯誤 fixture 與 parser 測試，驗證名稱、token／座標語義及整體失敗不改用繁體。
- [x] 6.3 讓 `CitybusBusRouteRepository` 使用 snapshot 語言並保留 `slat/slon/elat/elon/t/ws=1.3/leg=2/m1/l`；維持 T/F/W 聚合、香港時間與脫敏日誌。
- [x] 6.4 擴充 `CitybusRouteParser` 的車費、預計時間、分鐘及步行距離標籤以解析三語 HTML，新增單程／轉乘三語 fixture 並驗證 route、HKD、耗時、步行、rawInfo 與 generalInfo。
- [x] 6.5 讓 `showstops2.php`、stop map cache、站點預覽及 `getp2pstopinroute.php` 全部使用同一 `l`；cache key 包含語言且詳情 parser 覆蓋三語方向、上下車站與完整站序。
- [x] 6.6 保持 Citybus mobile 無顯式 Cookie、Referer、User-Agent、X-Requested-With 等瀏覽器 header，新增 contract 測試防止後續回加及防止完整座標／URL／rawInfo 日誌洩漏。

## 7. DATA.GOV.HK ETA 與 Google 地址三語化

- [x] 7.1 將 DATA.GOV.HK stop name、ETA destination／remark 解析改為結構化三語欄位，按 tc→sc→en、sc→tc→en、en→tc→sc 選取單欄位官方原文並記錄 source／field／actual language。
- [x] 7.2 更新首程 ETA model、route card、ETA arrivals sheet、通知及 formatter 使用已選語言欄位；目前語言欄位全空時保持既有不可用／站點預覽 fallback，而不機器翻譯或寫回保存資料。
- [x] 7.3 將 Google reverse geocoding mapping 改為 `zh-Hant/zh-Hans/en + regionCode=HK`，保持 API key／FieldMask／Android identity 契約，並以語言版本保護 cache 與 in-flight callback。
- [x] 7.4 保持 Google 非目標／混合地址原文；timeout、HTTP/API、網絡、解析或空結果不得改用其他語言、mock 名稱或舊語言 cache。
- [x] 7.5 新增 DATA.GOV.HK 三語欄位／fallback／日誌測試及 Google 三語 request、cache 隔離、晚到結果、失敗不重試測試。

## 8. 通知監控與 TTS 診斷

- [x] 8.1 將監控狀態與播報內容建模為結構化語義，以目前 locale 產生通知 channel、標題、正文、action、時間／分鐘及 TTS 文案，保留 route／用戶名稱原文。
- [x] 8.2 語言切換時沿用 channel id、monitor session、步行設定與刷新週期，重新建立 channel metadata、立即更新活動通知、停止舊 utterance 並讓後續播報使用新語言。
- [x] 8.3 實作 Voice 相容 policy：繁體只允許粵語／香港／明確 Hant，簡體只允許普通話／明確 Hans，英文只允許 en 並優先 HK／GB；拒絕模糊 zh 及繁簡互相 fallback。
- [x] 8.4 將 no engine、init failure／timeout、missing data、unsupported locale、no compatible Voice、audio focus denied、speak rejected、callback error、utterance timeout、released／stopped 建模為穩定失敗類型。
- [x] 8.5 在啟動能力失敗時立即 Toast 一次，runtime 每原因每 monitor session 最多一次；Toast 使用目前語言說明具體原因與「監控繼續但不播報」，且不提供系統語音設定或試聽入口。
- [x] 8.6 擴充非敏感診斷，記錄 engine package、requested/candidate locale／Voice、availability、setLanguage、audio focus、speak、callback 原始結果、session／stage，排除 key、完整自訂名稱及 utterance。
- [x] 8.7 新增 Voice 選擇、語言切換、全部失敗類型、Toast 去重、audio focus 釋放、舊 utterance 停止及 TTS 失敗不影響 ETA／通知的 JVM／instrumentation 測試。

## 9. 三語版面與無障礙適配

- [x] 9.1 調整底部導航、常用 Fragment 的 header／快捷卡／排序／結果卡及搜尋 Fragment 的起終點表單／摘要／排序／結果卡固定寬高區，使用 `wrap_content + minHeight`、彈性 constraint／可滾動輔助控件，保持核心 action 至少 48dp。
- [x] 9.2 調整路線新增／編輯／管理、地點候選、設定、關於及路線匯入匯出畫面，確保英文／大字體下輸入、subtitle、列表與操作可增高或滾動。
- [x] 9.3 調整 ETA、路線詳情、監測設定 Bottom Sheet 及所有 Dialog／錯誤區，移除會裁切核心方向、備註、狀態或按鈕的固定寬高與單行限制。
- [x] 9.4 對允許省略的 compact 站名建立明確白名單，確保詳情及 `contentDescription` 保留完整文字；補齊三語 TalkBack 描述、狀態與讀取順序。
- [x] 9.5 新增或擴充 UI contract／instrumentation 檢查非白名單 ellipsis、view 越界、不可點擊 action、Dialog／Bottom Sheet 可滾動及語言切換後無混合文字。

## 10. 長期文件與專案上下文

- [x] 10.1 新增 `docs/localization-guidelines.md`，完整記錄 locale、自然翻譯、術語、Android resource、provider mapping、單欄位 fallback、TTS、版面、無障礙及測試規則。
- [x] 10.2 更新 `AGENTS.md`，區分「文件／OpenSpec 中文用繁體」與「App runtime 文案三語完整」，加入禁止硬編碼文案及新動態資料源須定義語言／fallback 的長期約束。
- [x] 10.3 以全專案視角翻新 `openspec/config.yaml`：更新三模組導航、兩個查詢 owner、location／transfer／settings、外觀模式、Google、資料源及目錄；移除固定 `l=0`／單語過時內容，保留精簡且非 change-specific 的 artifacts 與驗證規則。
- [x] 10.4 真正修改 `README.md` 前載入並遵循 `create-readme` skill，重新審查完整 workspace；保留 icon header，以繁體 GFM 更新三模組功能、三語、外觀、架構、配置、資料源、驗證與 OpenSpec，使用必要 admonition 且不加入 License／Contributing／Changelog。
- [x] 10.5 交叉檢查 README、AGENTS、本地化指南、OpenSpec config、change artifacts 與實際 code／test，刪除重複、過時或只適用單次 change 的全局敘述。

## 11. 自動化、模擬器與無障礙驗證

- [x] 11.1 執行全部本地單元測試、三語 parser／formatter／TTS／cache／resource contract 測試，修復回歸並保存測試命令與結果。
- [x] 11.2 執行 Android Lint 與硬編碼掃描，確認 MissingTranslation、HardcodedText、SetTextI18n、placeholder／plural 及白名單規則全部通過。
- [x] 11.3 在 API 36.1、約 360dp portrait 對三語 × 淺／深色的全部 App 畫面執行 font scale 1.0，對底部導航、常用、搜尋、設定及其他高風險畫面執行 1.3，對關鍵流程執行 2.0，保留截圖及問題清單。
- [x] 11.4 在 API 37 重複三語核心流程、語言／外觀連續切換、三個 destination、查詢、詳情、匯入匯出、活動通知及 font scale 1.0／1.3／2.0 驗證。
- [x] 11.5 使用 TalkBack／等效 accessibility 檢查核心操作、受控省略內容、Dialog、Bottom Sheet、通知 action 與狀態讀取；確認系統／第三方畫面邊界流程可繼續。

## 12. 真實外部服務與最終門檻

- [x] 12.1 對 Citybus `bsearch_p3.php`、`ppsearch_p3.php`、`showstops2.php`、`getp2pstopinroute.php` 及 DATA.GOV.HK ETA 執行繁體、簡體、英文真實矩陣，覆蓋多關鍵詞、單程／轉乘、日間／夜間並保存脫敏請求、語義比較與 fixture。
- [x] 12.2 逐項比較 `getp2pstopinroute.php` 保留／移除 `ginfo`、`lid` 的三語完整 RouteDetail；只有所有樣本穩定返回且語義等價才移除，否則保留並記錄證據；不得修改 `bsearch` 的 `limit`／`timestamp`。
- [x] 12.3 在可連接 Google 的網絡，以已配置 `GOOGLE_GEOCODING_API_KEY`、正確 package／certificate 對相同香港座標通過 `zh-Hant`、`zh-Hans`、`en` 真實 instrumentation；任何 mock 成功或網絡 timeout 均不得代替硬門檻。
- [x] 12.4 執行所需 connected tests、`./gradlew build`、`openspec validate add-app-multilingual-support --strict`，確認所有測試、lint、assemble 與 OpenSpec delta 通過。
- [x] 12.5 更新本檔所有完成 checkbox，檢查 `git status --short` 與 staged 範圍只包含本 change，按專案規則建立簡潔英文 conventional commit 且不提交構建產物。
