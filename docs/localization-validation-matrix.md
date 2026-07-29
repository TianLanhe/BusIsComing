# 三語與外觀驗收矩陣

## 共用維度

每個核心畫面至少覆蓋：

- 語言：繁體中文、簡體中文、English；另驗證跟隨系統的英文、Hant、Hans、裸 `zh` 與不支援 locale。
- 外觀：固定淺色、固定深色；另驗證跟隨系統淺／深。
- 裝置：近期 API 36.1、約 360dp portrait；可用時補 API 37 與 Android 7.1。
- 字體：全部畫面 1.0；頂層 destination 及高風險頁 1.3；查詢、編輯、詳情、監控、設定語言、匯入匯出 2.0。
- 狀態：初始、載入、成功、空、失敗、刷新、重建；檢查 TalkBack 名稱、焦點順序及 48dp action。
- 術語：已保存起終點配置使用「常用行程／行程／Regular journey」，查詢結果使用「路線／路线／Route」，詳情中的單段巴士服務按需要使用「乘車段／乘车段／Leg」。

## 畫面清單

| 區域 | 必查內容 | 高風險 |
| --- | --- | --- |
| 常用 | 首次引導、快捷卡、附近標記、查詢、排序、結果、刷新 | 英文 header、路線卡 ETA、font 2.0 |
| 搜尋 | 起終點、目前位置、候選、交換、摘要、保存、排序、結果 | 未提交文字重建、候選與鍵盤、舊 callback |
| 設定 | 外觀、語言、行程資料、支援、關於 | 四語言選項自稱、摘要、大字體列高 |
| 行程編輯／管理 | 新增、編輯、複製、刪除、空狀態、候選 | 地址與驗證錯誤、輸入不被裁切 |
| 行程匯入匯出 | 文件選擇前後、預覽、合併、取代、錯誤、摘要 | `.bicroutes` 兼容、系統 picker 邊界、Dialog action 可達 |
| 路線卡／詳情 | 站點預覽、ETA、車費、方向、途經站、換乘 | 動態原文、長站名、Bottom Sheet 滾動 |
| 監控 | 設定、通知 channel／正文／action、三種狀態 | 語言切換保留 session、TTS 具體錯誤 |
| 對外內容 | 分享、回饋、網站、私隱、關於 | 三語路徑與自然文案、Intent 失敗 |

## 搜尋與行程編輯互動驗收

- 新增、編輯及複製行程：初始起點／終點僅為緊湊單行 label／hint，不保留常駐 helper 第二行；分別驗證搜尋中、無結果、失敗、定位、校驗和 Google attribution 仍在所屬欄位顯示及清除，且行程名稱說明與保存校驗不變。
- 搜尋候選：在 `360dp`、font scale 1.0、常見 IME 下驗證 5 至 6 個完整候選；小屏或大字體只能降級為完整可見項目，不得顯示半項。候選展開時在列表內向上、向下及越過頂／底滑動，確認 AppBar、頁面、結果列表和下拉刷新不移動；關閉候選後確認結果列表位置不變，並按目前展示狀態恢復原有刷新資格。
- IME 與導航：搜尋輸入時確認 `MainActivity` 的底部導航仍在物理底部而被 IME 覆蓋、不可觸控及不可取得無障礙焦點；候選仍在 IME 上方完整可見，收起鍵盤後導航位置、量度與 destination 不變。行程編輯等次級頁沿用其既有 IME 行為。
- 查詢狀態：常用與搜尋頁均驗證共用狀態卡的載入、空結果及失敗；搜尋中按鈕、鍵盤 action 與刷新不可重複提交，取消、舊結果刷新與過期回呼不得覆寫較新的畫面。
- 「本次行程」與保存：成功的非空查詢折疊為輕量 `起點 → 終點` 上下文欄，驗證沒有大型圓角卡片或等寬 tonal 操作帶，鉛筆與描邊短保存各有至少 `48dp` 觸控目標。正常字體在 `360dp` 及英文長地點仍為單行尾部省略；font scale `1.3／2.0` 可分兩列，但操作保持內容寬度並靠尾端。
- 編輯保留結果：鉛筆令完整編輯器以約 `240ms` 高度加淡化整體替換行程欄，不顯示取消編輯、不自動聚焦或彈出 IME；保存與刷新立即停用，修改地點後舊結果、排序、詳情及監控仍可用。確認監控收到上一次成功起點快照；點擊搜尋才清空舊結果，新成功才重新折疊與建立保存資格，空或失敗保持編輯器。
- 頁面捲動：常用與搜尋頁分別在無結果、有結果、編輯保留結果下拖動頂部區域，AppBar offset 必須不變；只有結果列表滑動可收折／恢復頂部並保持結果控制器吸頂。常用空狀態可按內容需要內部捲動，但不得驅動 AppBar。

執行記錄必須逐項列出實際語言、外觀、字體、裝置 API、導航模式、IME 與 Citybus 真實資料覆蓋；未執行的矩陣不得以文件規則視為已通過。

## 2026-07-30 路線搜尋互動回歸記錄

| 環境／範圍 | 覆蓋 | 結果 |
| --- | --- | --- |
| Pixel 9 API 36 模擬器；約 `360dp` portrait | 香港繁體／簡體／英文 × 淺／深色 × font scale `1.0／1.3／2.0`；緊湊行程欄、單行省略、大字體雙列、鉛筆／保存觸控框與雙向切換 | `RouteSearchInputVisualMatrixInstrumentedTest` 三檔通過 |
| 同一模擬器；搜尋展示狀態 | 成功折疊、鉛筆整體替換、無取消、修改保留舊結果、刷新停用、提交時才清空、跨 destination 保留 | 搜尋頁定向 instrumentation 通過 |
| 同一模擬器；捲動 ownership | 常用／搜尋無結果與有結果的頂部拖動、編輯器拖動、結果列表收折、候選頂／底邊界 | AppBar 與候選定向 instrumentation 通過 |

本輪未覆蓋 API 25、三按鍵導航、多款實體 IME、實機 TalkBack 及真實 Citybus 查詢；這些項目不得由上述模擬器證據替代。

## 動態資料

| 來源 | 繁體 | 簡體 | English | 失敗／回退 |
| --- | --- | --- | --- | --- |
| Citybus place | `l=0` | `l=2` | `l=1` | 無跨語言重試；保留 limit／timestamp |
| Citybus route | 車費、時間、步行、路線 | 同左 | 同左 | T/F/W、單程／換乘、日／夜間 |
| showstops／detail | stop id、方向、完整站序 | 同左 | 同左 | cache 依語言隔離；ginfo／lid A/B |
| DATA.GOV.HK ETA | tc→sc→en | sc→tc→en | en→tc→sc | 記錄實際欄位語言，不改寫資料 |
| Google 地址 | zh-Hant + HK | zh-Hans + HK | en + HK | 真實 key／identity；逾時不算通過 |

## 重建與並發

- 由常用、搜尋、設定各自切換語言，重建後仍停留原 destination。
- 常用已查詢、搜尋已查詢及搜尋未提交表單三種情況分別切換語言／外觀。
- 驗證常用行程自動重查不增加使用次數；下拉刷新與同一行程重查仍遵守 session 去重。
- 令舊語言的 route、ETA、站點預覽、地點搜尋、詳情及 Google callback 晚到，確認不更新畫面或 cache。
- 連續切換語言與外觀，確認兩個偏好互不覆寫且沒有額外手動 `recreate()`。

## 2026-07-17 實網驗收記錄

以下請求均不附加 Cookie、Referer、User-Agent 或 X-Requested-With，記錄只保留端點、語言與語義結果，不保存完整座標、API key 或 rawInfo。

| 來源 | 覆蓋 | 結果 |
| --- | --- | --- |
| `bsearch_p3.php` | `l=0/2/1`；「會展／会展／Convention」；保留 `limit=100`、`timestamp` | 三語均 HTTP 200；分別返回「會展站」、「会展站」、「Convention Plaza」及一致的座標語義 |
| `ppsearch_p3.php` | `l=0/2/1`；日間 12:00、夜間 03:15；單程與轉乘 | 三語均 HTTP 200；日間各 2 個單程、夜間各 7 個候選且含 5 個轉乘；票價、時間、步行距離與 rawInfo 語義一致 |
| 英文 P2P parser | 真實 `Hong Kong Dollar17.8`、`Estimated38Min`、`Walking distance (approx)` | 原 parser 未接受完整貨幣名稱；已加入真實 fixture、紅燈回歸及相容解析，三語 fixture 通過 |
| `showstops2.php` | 同一夜間轉乘 rawInfo、`l=0/2/1` | 三語均 HTTP 200 且各 20 個 stop；英文返回英文站名；上游 `l=2` 回應與 `l=0` 完全相同，故簡體流程保留 Citybus 官方繁體原文，不在 App 內轉換 |
| `getp2pstopinroute.php` | 同一路線三語；完整、移除 `ginfo`、移除 `lid`、同時移除 | 三語均 HTTP 200；移除 `ginfo` 會令全程分鐘及抵達時間缺失，`ginfo` 必須保留；單一樣本移除 `lid` 雖相同，但不足以證明全域無作用，因此 `lid` 亦保留 |
| DATA.GOV.HK stop／ETA | stop `001227`、夜間路線 `N118` | stop 的 `name_tc/name_sc/name_en` 與 ETA 的 `dest_*`、`rmk_*` 三欄均有語義一致的官方原文；夜間 ETA 時間可為空，formatter 仍按結構化不可用狀態處理 |
| Google Geocoding v4 | 相同香港座標、`zh-Hant/zh-Hans/en + HK`、真實 Android identity；新增行程的目前位置流程 | 三語真實請求均返回非 plus code 地址；繁簡結果包含中文、英文結果包含拉丁文字，新增行程亦填入真實地址並顯示 Google 歸因。Google 可按本地文字或最接近翻譯回退，故香港座標的繁簡全文相同仍屬有效原文，不在 App 內轉換 |

> [!IMPORTANT]
> Google 三語真實 instrumentation 已以有效 key、package／certificate identity 通過。任何後續 mock、fixture、timeout 或 403 仍不能代替此硬門檻。
