## Context

常用行程結果由 `MainActivity` 管理，臨時查詢結果由 `SearchFragment` 管理；兩者已有手動下拉刷新、query generation、排序、置頂、首程 ETA 與站點預覽的漸進更新。手動刷新完成時會顯示固定刷新浮層與成功勾號，且有結果成功會滾動到頂部，這些屬於明確使用者手勢的既有回饋，不能直接套用到每分鐘自動刷新。

`RouteDetailActivity` 已每 60 秒刷新首程 ETA，但 Citybus 動態詳情不會週期更新。active `fix-route-detail-progressive-loading` 已完成實作任務，雖尚未同步／歸檔，現有代碼已引入頁面／domain generation、單一 reducer、可靠結構快取、動態詳情 `Refreshing` 與互動狀態保持，本 change 直接以該實現與 active delta 為基線。Citybus 詳情 endpoint 會在同一回應返回穩定結構與動態時間／票價，無法只下載動態欄位；因此每次週期必須完整請求、解析及驗證回應，但只可把通過身份與結構校驗的動態值歸併到目前頁面。

`integrate-landsd-pedestrian-routing` 會在結果基礎列表到達後繼續漸進更新步行狀態與步行排序。兩個 change 同批實作時必須共用 query generation、result identity、單次 projection 與 viewport anchor；自動刷新不能把 CSDI callback 當成 cycle 未完成，也不能讓每分鐘刷新繞過 walking runtime 對失敗 key 的退避。

設定頁目前的偏好順序是外觀主題、語言。新偏好須同時控制常用結果、臨時結果及詳情，不建立三套互相漂移的設定。產品已確認預設為 1 分鐘，選項為關閉／1／2／5／10 分鐘，且只在目前頁面可見及 App 前台時運行。既有五段行內按鈕在窄屏與大型字體下過大、突兀且搶奪設定頁層級，本次改為與相鄰偏好一致的標準設定行，並把選項移入單選對話框。

首次說明橫幅的 UI 已單獨確認為硬驗收合同：它不是 Snackbar、Toast、Dialog 或 Bottom Sheet；沒有左側圖示與關閉鍵；位於 AppBar 正常排版空間、查詢上下文之後及結果摘要之前，完整展示 5 秒後自動消失。實作必須遵循 `docs/ui-style-guide.md`、`docs/localization-guidelines.md` 與 `docs/localization-validation-matrix.md`。

## Goals / Non-Goals

**Goals:**

- 以一個全 App 偏好控制常用結果、臨時結果與詳情的前台自動刷新。
- 讓排程在前後台、destination 切換、編輯、失敗、改間隔及關閉時具有 deterministic 行為，永不並發或追趕漏掉的週期。
- 讓結果刷新重用原查詢快照並保持排序、置頂、視口與已開啟互動；讓詳情動態資料與 ETA 獨立成功且不重置穩定內容。
- 第一次成功顯示查詢結果時，以已確認的高提示感但非阻塞橫幅告知使用者，並提供直達設定的操作。
- 以 fake clock／scheduler、state reducer、instrumentation 與任務自有裝置真實網絡驗證所有邊界。

**Non-Goals:**

- 不在 App 背景、鎖屏或不可見 destination 執行，不新增 `Service`、`AlarmManager`、`WorkManager`、通知或喚醒鎖。
- 不改變手動下拉刷新的固定浮層、成功勾號、失敗提示或有結果後滾動到頂部行為。
- 不重新取得臨時查詢的目前位置、不修改已保存行程使用次數／最近使用時間、不自動保存臨時查詢。
- 不為詳情週期重新請求幾何，不用新詳情回應替換穩定結構，也不刷新監控 service 的排程。
- 不提供任意秒數、自訂輸入、按頁面獨立間隔或背景刷新選項。

## Decisions

### 1. 使用 App 級偏好 store 與頁面級純前台 controller

新增 `RouteAutoRefreshSettingsStore`，持久化枚舉值 `OFF`、`MINUTES_1`、`MINUTES_2`、`MINUTES_5`、`MINUTES_10`。缺少 key、升級自舊版本或遇到未知值時均解析為 `MINUTES_1`；使用者選擇立即保存並通知目前可見 controller。

新增可注入 monotonic clock 與 scheduler 的 `ForegroundAutoRefreshController`。每個目前結果 owner／詳情頁各自擁有 controller，controller 只輸出「現在可開始一次 AUTOMATIC trigger」及下一次到期資訊，不持有 Activity、View、repository 或 route model。頁面 owner 提供 eligibility、目前 generation、最近成功時間與一次嘗試的開始／完成事件。

選擇頁面級 controller，可讓 query context、detail stable key 與生命週期仍由既有 owner 掌握；它們共享同一純排程 policy 而不是複製 `Handler.postDelayed`。被否決方案包括：

- 每頁獨立手寫 timer：容易在 destination 切換、改間隔及失敗後產生不同語義。
- App 級背景 coordinator：會跨越頁面 owner／generation 邊界，並誘使不可見頁面或背景繼續網絡工作。

### 2. 明確定義 `Disabled / Waiting / Paused / Refreshing` 狀態機

controller 的可觀察狀態及轉移如下：

- `Disabled`：設定為關閉。取消 timer；若網絡已開始可讓底層結束，但 invalidate 此 AUTOMATIC generation，callback 不得更新 UI 或再排程。
- `Waiting`：有有效上下文、頁面可見、App 前台且沒有其他查詢。以 `max(lastSuccessfulAt + interval, lastAttemptFinishedAt + interval)` 計算下一次到期；沒有自動嘗試完成時間時，以首次／手動成功時間為基準。
- `Paused`：設定非關閉，但 App 背景／鎖屏、destination 不可見、結果 query 正在編輯或候選展開、上下文無效，或另一個 INITIAL／MANUAL trigger 正在進行。暫停期間不累積 catch-up 次數。
- `Refreshing`：已接受一個 AUTOMATIC trigger。直到該頁定義的 cycle 終止前不接受任何第二個自動、手動或首次查詢。

從 `Paused` 回到可見 eligibility 時重新計算 due：已到期則立即嘗試一次，未到期則等待剩餘時間。每次 cycle 終止後才安排下一次；失敗也以 `lastAttemptFinishedAt + interval` 提供完整冷卻，不做高頻 retry。系統時間回撥不影響當前進程 timer，畫面顯示時間另使用 wall clock。

設定改為較短／較長間隔時立即重算 due。結果頁進入詳情時結果 controller 轉為 `Paused`，詳情 controller 成為唯一可運行 owner；返回後按原結果上下文重新計算，不同頁面不能同時刷新。

### 3. 將查詢 trigger 類型顯式化並保持原查詢快照

結果 owner 將查詢原因顯式標記為 `INITIAL`、`MANUAL` 或 `AUTOMATIC`，共用既有 repository 查詢與 generation guard，但分開 UI side effects：

- `INITIAL` 沿用整頁 loading；成功（包括 0 條路線）建立自動刷新基準及首次 notice eligibility，失敗不建立 timer、不自動 retry，也不顯示首次 notice。
- `MANUAL` 完整保留現行下拉指示器、成功勾號、失敗 Toast 及有結果成功後滾動頂部的契約。
- `AUTOMATIC` 保留原內容，只在結果摘要顯示「正在更新」；成功更新最後成功時間，失敗完全靜默並保留舊內容／時間。

常用結果保存 query owner、行程 id、原起終點與查詢時的排序／置頂上下文；自動刷新不呼叫 usage update。臨時結果保存該次成功查詢使用的 `Place` 名稱及精確座標快照，即使來源是目前位置也不重新定位。使用者編輯起終點、展開候選、清空或發起新臨時查詢時，舊 controller 暫停或失效；切換常用行程同樣 invalidate 舊 generation。

基礎路線成功（包括空陣列）即終止自動 cycle，隨後 ETA／站點預覽／CSDI walking 仍按目前 query generation 漸進補全且不延長 cycle。新基礎結果使舊結果專屬 CSDI consumer 失效，但可重用仍有效成功 cache；`AUTOMATIC` 只為退避已到期的失敗 key 建立新 flight。ETA、預覽、CSDI 與基礎列表只更新同一 `RouteQueryState`，由一個 projection 依目前字段／方向及 pin identity 排序一次。所有可能改變位置的提交共用 viewport anchor：提交前記錄第一張可見路線 stable id 與相對列表頂部 pixel offset，提交後恢復；若 route 已消失則選新排序中最接近的下一個 route。自動刷新不主動關閉或切換已開啟的 ETA／詳情選擇。

### 4. 詳情 cycle 同時刷新 Citybus 動態詳情與首程 ETA

每次詳情 AUTOMATIC trigger 建立同一 cycle id，並發啟動：

1. Citybus 完整詳情 request：使用與目前頁面相同語言及 endpoints，完整 parse／validate。只有 route/detail stable key 與目前可靠結構一致時，才把預計時間與分段票價等動態欄位送入 reducer；任何結構缺失或 mismatch 都把此 domain 視為失敗，不替換站序、乘車段、walking、marker 或其他穩定內容。
2. 首程 ETA request：沿用目前 ETA repository、首程 stop／route identity 與 generation，成功即可獨立更新 ETA。

詳情狀態明確分離 `pageGeneration`、穩定 `structureIdentity`、`dynamicDetailGeneration`、`etaGeneration` 與 `walkingGeneration`。AUTOMATIC cycle 只推進 dynamic detail 與 ETA；既有 CSDI 成功／Loading／fallback、步行 paths、站序及巴士 geometry 不屬於本 cycle，不能被取消、替換或重新請求。摘要 pending target 綁定 page＋structure identity，單純週期動態更新不得清除。

兩個 domain 可同時為 `Refreshing`，各自一成功就立即發布；一方失敗不回滾另一方成功，也不顯示自動失敗警告。cycle 要等待兩方皆進入成功／失敗／取消 terminal 狀態後才把 `lastAttemptFinishedAt` 回報 controller。Citybus 回應不得引發 geometry request；現有 geometry、地圖相機、bottom sheet detent、展開乘車段、selected marker／timeline、列表位置都由 stable id reducer 保持。

這一決策接受「每分鐘仍下載完整 Citybus 詳情回應」的流量成本，因上游沒有獨立動態 endpoint；以不重繪穩定結構、無幾何請求及可調／可關閉間隔控制成本。被否決方案是只解析局部欄位而略過結構驗證，因為它可能把另一 route variant 的動態值合併到目前頁面。

### 5. 一次性 notice 與日常刷新回饋分離

`AutoRefreshNoticeStore` 保存全 App 的「已完成首次說明」布林值。僅當設定尚未被使用者明確選擇、notice 未完成，且常用或臨時查詢首次成功顯示結果（包括 0 條）時立即插入橫幅；詳情頁永不展示。橫幅的 `設定` 操作打開設定 destination，scroll／focus 到整個自動刷新標準設定行但不自動打開對話框，並保留原查詢上下文與結果。

notice 只在以下任一條件成立時標記完成：

- 橫幅完成整個可見時長並自然消失；
- 使用者點擊橫幅 `設定`；
- 使用者在設定頁有意選擇任一刷新選項，包括 `關閉` 或重新選擇目前的 `1 分鐘`。

若 App／頁面在完整時長前離開或重建，取消動畫但不標記，下次成功查詢重新展示。基準為完全可見 5 秒，進出各約 200ms；使用 `AccessibilityManager.getRecommendedTimeoutMillis()` 在 TalkBack 或系統建議時延長完全可見時長。卸載自然清除，升級與進程重啟保留。

日常刷新不復用首次橫幅：結果摘要把更新時間暫時替換為小型 progress +「正在更新」，成功後直接顯示新時間；詳情只在對應動態區域使用現有 `Refreshing` 語義。沒有成功動畫、成功 Toast 或自動失敗警告。

### 6. 首次橫幅與標準設定行採用硬 UI 合同

常用頁在 query controls／目前行程上下文之後、sticky result controls 之前插入橫幅；搜尋頁在完整編輯器或折疊臨時行程上下文之後、共用 result controls 之前插入。它佔正常 AppBar layout 空間，不 overlay 結果卡、不 dim 頁面，頁面在顯示期間仍可捲動與操作。

橫幅使用語義淺綠表面、綠色 1dp border、14dp 圓角、克制陰影與底部 3dp 倒數線；左側只有兩行 `自動刷新已開啟`、`每 N 分鐘更新`，右側文字 action `設定` 至少 48dp 觸控區。沒有左上／左側圖示、關閉按鈕或額外說明。進場 slide + fade 約 200ms，完全可見計時後退場 slide + fade 約 200ms；系統動畫停用時立即切換進／出狀態但仍保留完整可見時長。TalkBack polite announce 一次，不搶焦點。

設定頁在 `偏好` 內依序顯示外觀主題、語言、自動刷新。自動刷新使用與前兩項一致的標準設定行：左側為標題，右側顯示目前值，整行至少 48dp 且可點擊；不在頁面內直接平鋪五個按鈕。點擊設定行以 `MaterialAlertDialogBuilder.setSingleChoiceItems` 打開單選對話框，完整列出關閉／1／2／5／10 分鐘並勾選目前值。選擇任何項目後立即保存、通知目前可見 controller、更新設定行右側值並關閉對話框，不顯示成功 Toast 或二次確認；重新選擇目前值同樣關閉對話框並完成首次提示 notice，但不觸發可見重載。

首次橫幅的 `設定` deep navigation 捲動並聚焦整個自動刷新設定行，而不是已移除的分段按鈕；焦點朗讀標題及目前值，使用者再啟用該行打開對話框。窄屏、大型字體及三語環境讓標準設定行和對話框自然換行／擴高，不縮小字體、不裁切、不重疊，也不依賴橫向捲動。兩處 UI 均使用三語資源、theme semantic colors 及最少 48dp 操作熱區。

### 7. 驗證以狀態邊界、UI 合同與真實兩週期分層

- 純 JVM：fake monotonic／wall clock、scheduler 與 settings store，覆蓋每個間隔的前 1ms／剛好／後 1ms、pause/resume、改間隔、off、attempt finish 冷卻、時鐘回撥、無 catch-up 及任一時刻最多一個 trigger。
- 結果 owner：覆蓋 0 條成功、初次失敗、後續空結果恢復、固定臨時座標、usage 不變、排序／pin、stable-id viewport anchor、編輯／切換 invalidation、過期 callback、開啟 ETA selection 不被關閉。
- 詳情 reducer：覆蓋兩 domain 並發與所有完成排列、單方成功／失敗、結構 mismatch、語言／頁面 generation、無 geometry request、相機／bottom sheet／展開／選中狀態保持。
- Instrumentation／screenshot：三語 × 明暗 × 360dp × font scale 1.0／1.3／2.0，驗證標準設定行、目前值、單選對話框與橫幅精確層級、border／圓角／倒數線、無圖示／關閉鍵、非 overlay、TalkBack polite、動畫停用及 Settings deep focus。
- 真實裝置：只啟動本任務自有且符合畫像的模擬器，對常用或臨時結果及詳情完成至少兩個 1 分鐘 Citybus／ETA 週期，驗證前後台暫停與返回到期；完成後關閉本任務啟動的全部模擬器。

## Risks / Trade-offs

- [1 分鐘預設增加 Citybus／ETA 請求與流量] → 僅可見前台運行、cycle 完成後才重排、詳情不請求幾何，且提供一鍵 2／5／10 分鐘或關閉。
- [結果排序變動造成閱讀位置跳動] → 以 stable id + pixel offset 恢復第一可見 anchor；缺失時選最近下一項，不自動回頂。
- [CSDI 漸進重排與自動列表替換重複投影] → ETA、預覽、CSDI 及基礎結果共用單一結果 state／projection 與同一 anchor policy，禁止 callback 各自重排 adapter。
- [每分鐘刷新放大 CSDI 失敗] → `AUTOMATIC` 服從 walking runtime 的 5 至 30 分鐘失敗退避；基礎 cycle 不等待 CSDI，手動刷新才可按其契約繞過一次。
- [多入口 timer 造成重複請求] → 每個可見 owner 使用同一 controller policy，destination/lifecycle eligibility 保證只有目前頁面可接受 trigger，generation 丟棄舊 callback。
- [Citybus 詳情結構在週期中變更] → 完整 parse／validate，mismatch 只丟棄此次動態 domain；不以新回應重建目前頁面或污染可靠 cache。
- [一次性橫幅過強或過弱] → 保持 5 秒高辨識樣式但不遮擋、不搶焦點、可繼續操作，且全 App 僅完成一次。
- [大型字體令設定值或橫幅 action 擁擠] → 標準設定行與 Material 單選對話框自然擴高／換行，橫幅 action 可移到獨立 trailing row；禁止縮字、裁切或依賴橫向捲動才能理解目前值與全部選項。

## Migration Plan

1. 核對 active `fix-route-detail-progressive-loading` 的已實作 reducer、測試及 delta，直接作為詳情刷新基線；本次不要求先同步或歸檔。
2. 加入偏好與 notice store；缺少 key 的既有安裝按 1 分鐘初始化，不修改 SQLite 或已保存行程。
3. 先接入共用 controller 與結果 trigger 類型，再接入詳情雙 domain cycle，最後加入自動刷新標準設定行、Material 單選對話框、首次橫幅與日常回饋。
4. rollout 若需回滾，可把預設／runtime feature gate 收斂為 `OFF` 並移除 UI 入口；所有新增偏好都可安全忽略或清除，無資料格式回滾。

## Open Questions

無；刷新範圍、間隔、前台生命週期、失敗策略、首次提示與 UI 合同均已確認。
