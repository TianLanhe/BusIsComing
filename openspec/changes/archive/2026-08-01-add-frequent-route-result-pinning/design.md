## Context

目前常用頁由 `MainActivity` 管理已選行程、`RouteQueryState` 保存原始結果與排序狀態，並把排序後的 `BusRouteOption` 直接交給 `BusRouteAdapter`。`BusRouteAdapter` 同時由 `MainActivity` 與 `SearchFragment` 使用，現時繼承 `RecyclerView.Adapter`，每次 `submitList` 都以 `notifyDataSetChanged()` 全量刷新。ETA 與站點預覽會在基礎結果展示後漸進更新，候車排序亦可能因此重排結果。

本機行程保存在 `RouteConfigDbHelper` 管理的 `bus_is_coming.db`。目前 schema version 為 3，只有 `route_configs` 表；`RouteConfigRepository` 負責新增、修改、刪除與匯入 transaction。`RouteEditActivity` 現時不區分單純改名與修改起終點，`.bicroutes` 版本 1 則只交換行程名稱及起終點。

置頂同時跨越 UI 手勢、列表投影、Activity／task 生命週期、SQLite 遷移、行程編輯及匯入取代流程。它不能使用目前包含價格、耗時、步行距離及 `rawInfo` 的 `resultId` 作為長期身份，否則動態資料、語言或上游編碼變化會令偏好失效或錯配。

本設計沿用 XML + Material Components、RecyclerView、SQLiteOpenHelper 與輕量 Repository 分層，不改動 Citybus／DATA.GOV.HK 請求或解析契約。

## Goals / Non-Goals

**Goals:**

- 在常用頁為每個已保存行程提供本次置頂與長期置頂，並以後置頂優先的單一區域展示。
- 讓普通排序、ETA／站點預覽漸進更新、刷新及列表移動不破壞置頂順序或造成不必要的視口跳動。
- 使用可版本化、跨語言且排除動態展示資料的嚴格乘車方案身份。
- 以非破壞性 v3→v4 SQLite 遷移保存長期置頂，並與行程編輯、刪除及匯入 transaction 保持一致。
- 為持久化失敗、過期 callback、快速相反操作與 Activity recreation 提供明確恢復路徑。
- 保持三語、明暗色、窄屏、大字體、TalkBack 與關閉系統動畫時可用。

**Non-Goals:**

- 不在搜尋頁或臨時查詢結果提供置頂。
- 不新增卡片常駐按鈕、長按入口、獨立操作列、固定分組標題或首次使用教學。
- 不限制每個行程可置頂的路線數量。
- 不把置頂偏好加入 `.bicroutes` v1，亦不改動該格式版本。
- 不修改 Citybus、DATA.GOV.HK、Google 請求、解析、結果聚合或現有普通排序比較規則。
- 不以模糊路線號、展示名稱、價錢或其他近似資料恢復長期置頂。

## Decisions

### 1. 以行程為作用域的三態狀態機

每個可辨識的路線結果在目前已保存行程下具有 `UNPINNED`、`TEMPORARY` 或 `PERSISTENT` 狀態：

- `UNPINNED` 向右滑動後成為 `TEMPORARY`。
- `TEMPORARY` 再次向右滑動，或點擊首次置頂 Snackbar 的「長期置頂」，成為 `PERSISTENT`。
- `TEMPORARY` 或 `PERSISTENT` 向左滑動後直接成為 `UNPINNED`；不設長期降級為本次置頂的中間步驟。
- `PERSISTENT` 再次向右只露出「已長期置頂」文字並回彈，不更新時間、不寫入、不震動、不展示 Snackbar。

取消置頂 Snackbar 的「撤銷」保存完整狀態快照，包括原等級、排序 token 與原相對位置，因此撤銷長期取消會直接恢復長期置頂。

選擇此狀態機是為了讓同一方向表示「提高關注程度」、相反方向表示「完全移除」，同時避免卡片常駐控件。否決「長期向左先降級為本次、再向左取消」，因為它增加操作次數且使取消結果不符合用戶已確認的直接語義。

### 2. 所有置頂共用單一 LIFO 排序 token

`TEMPORARY` 與 `PERSISTENT` 不分兩個子組，全部按 `pinnedAt` 降序展示；後置頂在上，先置頂在下。新 token 使用：

```text
max(currentTimeMillis, maxKnownPinnedAt + 1)
```

這讓同一毫秒內的操作及系統時鐘回撥仍保持嚴格遞增。`TEMPORARY` 升級為 `PERSISTENT` 時沿用原 token，不跳到區域頂部；重新啟動後長期置頂按保存 token 恢復，新產生的本次置頂位於其上方。

否決為本次／長期各設分組或在升級時重寫時間，因為前者增加視覺層級，後者會把「保存偏好」誤表現成一次新的置頂動作。

### 3. 以純投影組合置頂與既有排序

新增純邏輯 `PinnedRouteProjector`，輸入：

- 目前基礎路線結果；
- 目前 `SortField` 與 `SortDirection`；
- 該行程的本次／長期置頂狀態及 token。

輸出為：

1. 按 token 降序的已匹配置頂 `RouteCardItem`；
2. 當置頂與未置頂均非空時的一個 `UnpinnedDividerItem`；
3. 由既有 `BusRouteSorter` 排序的未置頂 `RouteCardItem`。

`RouteQueryState` 繼續保存查詢結果、排序、時間及錯誤；它不把置頂區域寫回 `results`。`BusRouteSorter` 維持只理解 `BusRouteOption` 的純排序責任，不新增置頂分支。ETA 或站點預覽更新先更新原結果，再重新投影；只有未置頂區在候車排序時可重排。

否決先把置頂結果插入已排序 `results`，因為後續 `toggleSort`、ETA 更新與刷新會丟失區域邊界或重排置頂結果。

### 4. 使用版本化嚴格路線指紋

新增指紋 formatter，按乘車段原順序編碼以下欄位：

- company；
- 公開路線號；
- route variant；
- bound；
- direction path；
- boarding sequence；
- alighting sequence。

格式以 `v1|` 開頭，每個文字欄位使用固定欄位順序及長度前綴，數值使用規範十進位表示，避免分隔字元碰撞。指紋明確排除：

- App／Citybus 語言及所有展示名稱；
- 價格、耗時、步行距離、ETA、站點預覽；
- `rawInfo` 整體字串；
- 排序、選中、監控或其他 UI 狀態。

指紋以 `routeDetailQuery.plan.legs` 為主要資料來源；只有全部乘車段具備必要欄位時才可建立。若結果缺少必要欄位，或同一查詢結果中兩張卡片產生相同指紋，相關卡片不可置頂，手勢回彈並顯示非阻塞提示。長期恢復只接受相同版本完整匹配，不退回路線號或 `resultId` 模糊匹配。

否決沿用 `resultId`，因為其包含會改變的價格、耗時、步行距離與 `rawInfo`；亦否決只以路線號匹配，因為同一路線號可有不同方向、variant、上下車區間或換乘組合。

### 5. SQLite v4 保存長期置頂

`RouteConfigDbHelper.DATABASE_VERSION` 由 3 升至 4。新安裝在 `onCreate` 建立：

```sql
CREATE TABLE route_result_pins (
    route_config_id INTEGER NOT NULL,
    route_fingerprint TEXT NOT NULL,
    pinned_at INTEGER NOT NULL,
    PRIMARY KEY(route_config_id, route_fingerprint),
    FOREIGN KEY(route_config_id)
        REFERENCES route_configs(id)
        ON DELETE CASCADE
);

CREATE INDEX index_route_result_pins_route_time
ON route_result_pins(route_config_id, pinned_at DESC);
```

`onConfigure` 執行 `PRAGMA foreign_keys = ON`。v3→v4 的 `onUpgrade` 只建立新表與索引，不刪除或重建 `route_configs`。既有 v1／v2 遷移分支保持原行為，完成舊遷移後亦建立 v4 結構。

新增 `PinnedRouteRepository`，只負責：

- 按 `route_config_id` 載入長期置頂；
- 插入不存在的 `(route_config_id, fingerprint, pinned_at)`，不覆寫既有 token；
- 刪除單一置頂；
- 計數及按行程清除。

所有方法在專用單線程 executor 呼叫，不在主線程執行 SQLite。否決把欄位直接加入 `route_configs` 或保存 JSON，因為每個行程有零到多個獨立、有唯一鍵及排序需求的偏好；正規化關聯表可直接支援級聯、唯一性與索引。

### 6. 以會話狀態保存本次置頂與未匹配長期偏好

新增 `RoutePinSessionState`，按行程 id 管理：

- 本次置頂 fingerprint 與 token；
- 已載入長期置頂 fingerprint 與 token；
- 暫時未匹配的長期置頂；
- 每個 fingerprint 的 mutation generation；
- 最近取消操作的撤銷快照。

本次置頂在排序、ETA／站點預覽更新、刷新、同一行程重新查詢、切到搜尋／設定再返回，以及 Android 為同一 task 重建 Activity 時保留。必要的輕量 fingerprint、狀態及 token 透過 `savedInstanceState` 保存；不保存完整路線結果，重建後仍按既有語言／主題策略重新查詢。

以下事件清除對應本次置頂：

- 選擇另一個已保存行程並切換查詢 owner；
- 修改目前行程的起點或終點；
- 刪除行程；
- 用戶明確取消；
- task 被移除、強制停止或沒有 task 恢復狀態的全新啟動。

長期置頂即使本次結果沒有嚴格匹配亦保留在 SQLite 及 session 中，不建立空白卡片、不計入結果總數、不顯示錯誤；日後相同指紋重新出現時恢復原位置。

否決把本次置頂寫入 SharedPreferences 或 SQLite，因為其清除邊界是 task／行程會話而非全新啟動；亦否決在某次空結果或上游暫缺時刪除長期偏好。

### 7. 查詢結果與長期置頂並行載入

首次對已保存行程查詢時，Citybus 路線查詢與 `PinnedRouteRepository.load` 並行執行。基礎路線結果完成後，常用頁等待長期置頂讀取完成或明確失敗，再提交首個可見結果列表，避免先按普通排序展示後突然整批跳動。

若置頂讀取失敗，路線結果照常展示為未置頂，並只顯示一次非阻塞提示。Citybus 查詢失敗不修改任何本次或長期置頂；成功空結果隱藏全部卡片與分隔列，但保留偏好。

沿用現有 query generation，並為 pin load 加入行程 id 與 generation 檢查；切換 destination、行程、語言、起終點或開始新查詢後，舊 callback 不得更新目前列表。

否決先顯示普通列表再異步套用長期置頂，因為長列表會出現明顯位置跳動，亦可能令使用者點擊錯誤卡片。

### 8. 樂觀 UI 與每個指紋的最後意圖勝出

長期升級先在 UI 顯示書籤標記，再於單線程 executor 寫入。寫入失敗時：

- 若來源為本次置頂，回退為原本本次置頂及原 token；
- 顯示保存失敗提示；
- 不留下虛假的長期狀態。

取消長期置頂先移到未置頂區，再刪除 SQLite。刪除失敗時恢復原長期狀態、token 及位置並提示失敗。若用戶在刪除完成前點擊撤銷，系統在刪除之後排入恢復寫入；每個 fingerprint 的 mutation generation 令較舊 callback 只能完成底層序列，不可覆蓋較新的 UI 意圖。

否決等待資料庫成功後才移動卡片，因為滑動操作會顯得遲鈍；單線程序列加 generation 可在維持即時反饋的同時得到確定的最終狀態。

### 9. ListAdapter、兩種 item 與獨立手勢邊界

把 `BusRouteAdapter` 改為以 DiffUtil 驅動的 `ListAdapter<BusRouteListItem, RecyclerView.ViewHolder>`，支援：

- `RouteCardItem`：嚴格指紋可用時以其作穩定 identity；搜尋頁或常用頁不可置頂卡片可使用 `resultId` 作該次查詢的 fallback item identity，但該 fallback 不得保存或參與置頂匹配。
- `UnpinnedDividerItem`：固定語義 identity 加目前行程／查詢 generation，無卡片點擊或滑動能力。

`MainActivity` 為常用結果 RecyclerView 附加 `ItemTouchHelper`；`SearchFragment` 繼續使用相同 adapter/card binder，但不附加置頂手勢且只提交卡片 item。ItemTouchHelper 只允許可置頂、身份唯一的 `RouteCardItem`。

卡片手勢採用 pull-to-action，而不是 RecyclerView 的 swipe-to-dismiss。水平主導拖動在門檻前維持接近 1:1 跟手；每個方向按目前卡片狀態選取本地化動作文字，觸發距離由「卡片邊緣 16dp 留白 + 實際字型量測文字寬度」動態計算。可見最大位移為觸發距離再加 8dp；超出後卡片停在盡頭，不再跟隨手指離開畫面。門檻只看實際拖動距離，不設速度 fling 捷徑。

單次拖動首次跨過有效方向門檻時只震動一次。釋放後卡片一律先以約 180–240ms 回到零位移：未達門檻時不執行動作；達門檻時只在回彈完成後執行置頂、升級、取消或不可用提示。ItemTouchHelper 不得把路線卡當作已移除項目，也不得留下 pending cleanup；`clearView`、重新綁定及狀態更新均保證 `translationX = 0`，避免本次置頂升級為長期置頂時因相同穩定 identity 沿用已滑出畫面的 ViewHolder。

候車文字可點擊區與 48dp 鈴鐺觸控區在手勢起點命中時排除卡片滑動，保持 ETA 彈層與監控入口。普通卡左滑不位移。首次把普通卡設為本次置頂後，DiffUtil 先把同一穩定卡片移至 LIFO 置頂區第 1 項，再以短距離平滑方式顯示清單頂部，讓用戶看到新置頂卡片並感知原有內容向下推移。本次升級長期不改 token、不改位置、不強制回頂；取消置頂、排序、刷新、ETA 及站點預覽更新保存目前第一個可見 item 與 offset。

否決繼續使用 `notifyDataSetChanged()`，因為它無法可靠表達卡片跨區域移動、會重綁所有卡片且難以保持視口。

### 10. 視覺、Snackbar 與無障礙

本次置頂卡片只使用現有語意強調色描邊，不增加卡片尺寸。長期置頂沿用同一描邊，並在卡片既有左側約 14dp 內容 inset 內加入約 10dp × 25dp 的短書籤角標；角標不侵入右側候車／鈴鐺區，亦不改變卡片高度。

卡片被水平拉開時不繪製任何彩色、灰色或警示色底塊；露出的區域就是 RecyclerView 所在頁面的既有背景，只在其中繪製目前語言的動作文字。文字使用主題語意主要文字色，在淺色與深色主題下自動適配；置頂、升級、取消、已長期置頂與不可置頂均沿用同一透明底層規則。

只有置頂與未置頂兩區同時非空才插入橫向分隔列，文案按目前排序產生，例如「以下 10 條按耗時 ↑ 排序」。分隔列不 sticky、不可滑動；全部結果已置頂、沒有置頂或沒有結果時不展示。

主要 Snackbar：

- 首次置頂：「118 已在本次置頂」，操作「長期置頂」。
- 升級成功：「118 已為「上班」長期置頂」。
- 取消：「已取消置頂 118」，操作「撤銷」。

實際文案全部由三語資源產生。TalkBack 為卡片提供狀態及自訂 action：普通卡可「本次置頂」，本次置頂可「長期置頂」及「取消置頂」，長期置頂可「取消置頂」。裝飾性書籤不另行朗讀；分隔列朗讀包含數量、排序字段及方向的完整句子。

否決在卡片上顯示文字標籤或按鈕，因為需求明確要求不佔用卡片內容空間；亦否決只靠顏色區分長期狀態，因此保留形狀不同的書籤標記及無障礙描述。

### 11. 行程編輯、刪除、複製與傳輸

`RouteEditActivity` 在編輯模式載入原行程快照：

- 只改名稱時，保存原 id，所有本次及長期置頂保持不變。
- 起點或終點有變且長期置頂數量大於 0 時，保存前顯示會清除 N 條長期置頂的確認。
- 確認後，在同一 SQLite transaction 更新行程並刪除其長期置頂；失敗時整體回滾。取消確認不保存。
- 起終點改變亦使目前 task 中該行程的本次置頂作廢。

刪除單一行程及取代匯入依外鍵級聯刪除關聯長期置頂；UI 同時移除 session 狀態。複製、合併匯入及取代匯入所建立的新行程使用新 id，沒有任何置頂。`RouteTransferCodec` 維持 v1 嚴格 schema，不增加 fingerprint 或 pin 欄位。

否決以名稱或相同起終點把偏好轉移到新行程，因為置頂作用域是保存記錄 id；自動轉移會令複製／匯入出現不可見的本機偏好。

## Risks / Trade-offs

- [Citybus 的 route plan 欄位未來改變，令既有 `v1` 指紋無法匹配] → 保留版本前綴及 dormant 記錄；任何新算法使用新版本並另行設計遷移，不做跨版本模糊匹配。
- [同一結果出現重複嚴格指紋] → 對全部衝突卡停用置頂並提示，避免操作一張卡卻影響另一張。
- [等待 pin load 會略微延後首個結果畫面] → 與網路查詢並行讀取單一索引表；讀取失敗有明確降級，不等待無限期。
- [不限制置頂數量可能令普通區域很小] → 這是已確認的用戶控制選擇；全部置頂時隱藏無意義分隔列並保留取消入口。
- [樂觀寫入遇到快速取消／撤銷可能產生競態] → 專用單線程 executor、每指紋 generation、原狀態快照與最後意圖勝出規則共同約束。
- [ListAdapter 改造同時影響搜尋頁] → 搜尋頁不附加 ItemTouchHelper，只使用卡片 item；以既有 card binder 回歸測試詳情、ETA 與鈴鐺點擊。
- [同一穩定 identity 在本次升級長期時沿用已滑出畫面的 ViewHolder] → 禁止 swipe-to-dismiss，所有釋放均先回彈，`clearView` 與 bind 雙重歸零位移，並以連續兩次右滑回歸測試確保卡片仍可見。
- [三語、字型比例或系統字體令動作文字寬度不同] → 每次按實際 Paint 與本地化字串量測門檻，最大位移以門檻加固定 8dp 推導，不使用卡片寬度百分比或硬編碼像素。
- [外鍵若未啟用會留下孤兒置頂] → 在 `onConfigure` 明確啟用並以 instrumentation 測試 `PRAGMA foreign_keys` 與級聯刪除。
- [Activity task 恢復與真正全新啟動邊界依賴 Android saved state] → 只從有效 `savedInstanceState` 恢復本次置頂；SharedPreferences／SQLite 不保存本次狀態。
- [舊版 App 二進位直接降級無法打開 v4 database] → schema 變更保持純新增；如需產品版本回退，回退版本仍保留 database version 4 及相容 helper，不以 v3 二進位直接降級。

## Migration Plan

1. 先加入指紋 formatter、置頂模型、投影器及純邏輯測試，保持 UI 未接線。
2. 把 `RouteConfigDbHelper` 升至 v4，建立新表、索引、外鍵設定及 v3 fixture 遷移測試。
3. 加入 `PinnedRouteRepository` 與行程更新／刪除／匯入 transaction 協調，驗證失敗回滾。
4. 把 `BusRouteAdapter` 遷移到 ListAdapter 與兩種 item，先保持現有卡片展示及 SearchFragment 行為。
5. 在 MainActivity 接入 session、並行載入、投影、ItemTouchHelper、Snackbar、視覺與 saved state。
6. 補齊 RouteEditActivity 清除確認、三語資源、TalkBack action 及完整驗收。
7. 將既有 swipe-to-dismiss 改為有限 pull-to-action，加入動態文字門檻、透明底層、回彈後執行與 ViewHolder 位移歸零。
8. 將首次本次置頂改為移動後顯示置頂區頂部；升級、取消及背景更新使用各自的視口策略，並補充連續右滑與長列表回歸驗證。

遷移失敗時 SQLiteOpenHelper transaction 不提交 v4 schema，既有 `route_configs` 保持不變；App 對置頂讀取失敗採用無置頂降級。已成功建立的 v4 表為附加資料，不改寫行程欄位；回退功能可停止讀寫該表但保留資料，正式二進位回退必須維持 database version 4 相容性。

## Open Questions

無。功能範圍、狀態機、手勢方向、分隔方式、角標、Snackbar、生命週期、嚴格指紋、SQLite v4、失敗恢復及傳輸邊界均已在提案前確認。
