## 1. 前置規格與實作基線

- [x] 1.1 核對 active `fix-route-detail-progressive-loading` 的已實作代碼、測試及 delta specs 已具備 page／domain generation、可靠結構 cache、動態詳情 `Refreshing` 與互動狀態 reducer；本次不要求先同步或歸檔，但不得回退該基線
- [x] 1.2 重新檢查常用結果、臨時結果、手動下拉刷新、詳情 ETA timer、Citybus 詳情 request／parser、AppBar XML 與設定頁偏好結構，建立 `INITIAL`／`MANUAL`／`AUTOMATIC` 不互相改變 UI side effect 的對照測試基線

## 2. 偏好、notice 與純排程 controller

- [x] 2.1 先新增 `RouteAutoRefreshSettingsStore` 測試，覆蓋缺失／未知值預設 1 分鐘、關閉／1／2／5／10 分鐘持久化、升級與重新讀取
- [x] 2.2 先新增 `AutoRefreshNoticeStore` 測試，覆蓋初始未完成、完整展示／點擊設定／明確重選目前值後完成、進程重啟保持及中斷不完成
- [x] 2.3 實作兩個 store 與可觀察設定通知，不修改 SQLite、已保存行程或 `.bicroutes`
- [x] 2.4 先以 fake monotonic clock／scheduler 編寫 `ForegroundAutoRefreshController` 狀態測試，覆蓋每個間隔前 1ms／剛好／後 1ms、Disabled／Waiting／Paused／Refreshing、pause/resume、到期立即一次、無 catch-up 及任一時刻最多一個 trigger
- [x] 2.5 補充 controller 測試，覆蓋 `max(lastSuccessfulAt + interval, lastAttemptFinishedAt + interval)`、失敗完整冷卻、改短／改長間隔、刷新中關閉、時鐘回撥與過期 callback
- [x] 2.6 實作不持有 Activity、View 或 repository 的純 `ForegroundAutoRefreshController`，由頁面 owner 提供 eligibility、generation 與 attempt 完成事件

## 3. 設定頁行內 segmented selector

- [x] 3.1 新增三語 `自動刷新`、`關閉` 與 1／2／5／10 分鐘精簡文案、選中狀態及 TalkBack 描述，保持香港繁體、獨立簡體與自然英文
- [x] 3.2 在偏好分組的語言項之後加入行內 segmented selector，360dp／字體 1.0 時五項一行，空間不足或字體 1.3／2.0 時以 wrap／reflow 完整展示且每項至少 48dp 熱區
- [x] 3.3 綁定一次點擊即保存與目前可見 controller 重算；重新點擊目前值不重載但標記 notice 已完成，關閉時立即取消 timer 並 invalidate in-flight automatic generation
- [x] 3.4 新增 Settings instrumentation／layout 測試，覆蓋預設與五個選項、重選目前值、旋轉／重建、三語、明暗、360dp、font scale 1.0／1.3／2.0、TalkBack 選中狀態及無 radio dialog

## 4. 常用與臨時結果自動刷新

- [ ] 4.1 先新增結果 owner 測試，明確區分 `INITIAL`、`MANUAL`、`AUTOMATIC`，覆蓋首次成功含 0 條建立 timer、首次失敗不重試／不提示，以及初始／手動進行時自動 trigger 被拒絕
- [x] 4.2 為常用結果保存原 query identity／起終點並接入 controller，確保 automatic 不更新行程使用次數、最近使用時間或真實排序
- [x] 4.3 為臨時結果保存首次成功查詢的完整 Place 名稱與精確座標快照並接入 controller，確保目前位置不重新定位且不自動保存行程
- [x] 4.4 在兩個結果 owner 接入 destination／前後台／鎖屏／編輯／候選展開／清空／切換行程 eligibility 及 generation invalidation，進入詳情時暫停結果 owner，返回時按 due 或剩餘時間恢復
- [x] 4.5 讓 automatic 成功含空結果按完成時排序與既有 pin identity 更新、基礎路線回應即結束 cycle、後續 ETA／預覽繼續漸進；automatic 失敗保留舊結果與最後成功時間且不顯示警告
- [x] 4.6 新增 stable-id viewport anchor policy 與測試，保存第一可見 route 及 pixel offset，route 消失時選最近下一項，並保持已開啟 ETA／選中 route 而不滾動頂部
- [x] 4.7 在共用結果摘要加入小型 progress + `正在更新` automatic 狀態，成功靜默顯示新時間、失敗恢復舊時間；確認手動固定浮層、成功勾號、失敗 Toast 與滾動頂部語義完全不變
- [ ] 4.8 先以測試鎖定基礎列表完成 cycle 後 ETA／預覽／CSDI 仍可漸進、新 query generation 取消舊 CSDI consumer、`AUTOMATIC` 服從 walking 失敗退避，以及所有更新共用單次 projection 與 stable-id＋pixel-offset anchor，再接入結果 owner

## 5. 路線詳情雙資料域 cycle

- [x] 5.1 先新增詳情 coordinator／reducer 測試，覆蓋 Citybus 動態詳情與首程 ETA 同時開始、所有完成順序、單方成功／失敗、兩方 terminal 才結束 cycle 及下一次排程
- [x] 5.2 接入詳情頁 controller，取代硬編碼首程 ETA 60 秒 timer 的排程責任，但保留 ETA repository、identity、首次載入與手動局部重試語義
- [x] 5.3 每個 automatic cycle 並發發起完整 Citybus 詳情與首程 ETA request；沿用目前語言、endpoints、generation 及 existing single-flight，不新增 geometry request
- [x] 5.4 完整 parse／validate Citybus 回應並比對 route／endpoint／leg／可靠結構 stable identity，只歸併動態時間與票價；為結構缺失／mismatch／過期 generation 加入丟棄回歸測試
- [x] 5.5 驗證動態詳情與 ETA 各自成功即獨立發布、失敗靜默保留最近成功值，且刷新中／完成後保持地圖相機、bottom sheet detent、展開 leg、selected marker／timeline 及列表位置
- [x] 5.6 覆蓋詳情在背景、鎖屏、離開、語言變更、configuration change、設定改間隔／關閉時的 pause、取消／忽略 callback 與返回 due 行為
- [x] 5.7 把詳情 page／structure／dynamic／ETA／walking generation 邊界加入 reducer 測試，證明 automatic cycle 不取消或替換 CSDI 狀態／paths、不重新請求 walking，且動態更新不清除同一結構的摘要 pending target

## 6. 首次提示橫幅硬 UI 合同

- [x] 6.1 建立專用橫幅 layout／drawable／motion：淺綠語義表面、綠色 1dp border、14dp 圓角、克制陰影、底部 3dp 倒數線，明確不含左側圖示、關閉鍵、overlay 或頁面 dim
- [x] 6.2 加入三語兩行短文案 `自動刷新已開啟`／`每 N 分鐘更新` 的自然等價資源與右側 `設定` action；大型字體／窄屏把 action reflow 到獨立 trailing row，不縮字、裁切或重疊
- [x] 6.3 在常用頁 query controls 之後、sticky result controls 之前插入橫幅；在搜尋內容／折疊上下文之後、共用 result controls 之前插入，兩處都佔 AppBar 正常 layout 空間且不遮擋卡片
- [x] 6.4 實作約 200ms slide+fade 進場、完全可見 5 秒、約 200ms 退場與由滿至空倒數；使用系統建議 timeout 延長無障礙時長，動畫停用時 snap 但仍保留完整可見時長
- [x] 6.5 綁定全域 notice 完成規則：自然完整展示、點擊 `設定` 或設定頁明確選擇才完成；提前離頁／背景／重建不完成且下次成功結果重播，詳情頁永不展示
- [x] 6.6 實作 `設定` deep navigation，打開設定 destination 並捲動／聚焦 selector，同時保持原查詢上下文與結果；TalkBack polite 宣告一次、不搶焦點且 action 至少 48dp
- [x] 6.7 新增 instrumentation 與 screenshot 結構斷言，嚴格檢查兩入口插入位置、無圖示／關閉鍵、border／圓角／倒數線、非 Snackbar／Toast／Dialog／Bottom Sheet、可繼續操作及完成／中斷持久化

## 7. 生命週期、真實網絡與視覺驗收

- [ ] 7.1 以 lifecycle／navigation instrumentation 覆蓋常用→詳情→返回、臨時編輯→新查詢、前後台、鎖屏、configuration change、語言切換、改間隔與關閉，確認任何時刻只有目前可見 owner 能發起一次自動刷新
- [ ] 7.2 在香港繁體／簡體／英文、明暗主題、360dp、font scale 1.0／1.3／2.0、TalkBack 與系統動畫停用下驗收 selector、首次橫幅、日常摘要及詳情 refreshing，嚴格依 UI 合同保留截圖證據
- [ ] 7.3 只啟動本任務自有且符合 Google／螢幕畫像的模擬器，使用生產 Citybus／ETA 路徑讓常用或臨時結果及詳情各完成至少兩個 1 分鐘週期，驗證動態內容、部分失敗、前後台暫停與返回到期
- [x] 7.4 完成裝置驗證後關閉本任務啟動的全部模擬器，不操作、停止或重啟任務開始前已運行的任何 AVD

## 8. 回歸、構建與完成檢查

- [x] 8.1 運行 settings store、notice、controller、結果 owner、viewport anchor、詳情 coordinator／reducer 的全部定向 JVM 測試及既有手動下拉刷新、排序、置頂、ETA、詳情漸進載入回歸測試
- [x] 8.2 運行新增 instrumentation／screenshot 測試與 `./gradlew build`，如實記錄未完成的真實網絡、TalkBack 或設備畫像限制
- [x] 8.3 運行 `openspec validate --all --strict --no-interactive`，檢查 `git status --short`、變更 diff、tasks 勾選及無 `Service`／`AlarmManager`／`WorkManager`／geometry refresh／無關重構後再按倉庫規則提交
