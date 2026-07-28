## ADDED Requirements

### Requirement: 置頂只適用於已保存行程的常用頁結果
系統 SHALL 只讓用戶在「常用」destination 目前已選擇已保存行程時置頂該行程查詢返回的路線，並 SHALL 以行程 id 隔離不同行程的置頂狀態。

#### Scenario: 常用行程結果可置頂
- **WHEN** 用戶以一個已保存行程在常用頁查詢出可唯一辨識的路線
- **THEN** 系統 SHALL 允許用戶透過卡片手勢或對應無障礙 action 置頂該路線
- **AND** 該置頂 SHALL 只屬於目前已保存行程

#### Scenario: 相同路線在另一行程保持獨立
- **WHEN** 同一嚴格路線指紋同時出現在兩個已保存行程的查詢結果
- **AND** 用戶只在其中一個行程置頂該路線
- **THEN** 另一個行程的該路線 SHALL 保持未置頂

#### Scenario: 搜尋及臨時查詢不提供置頂
- **WHEN** 用戶查看搜尋 destination 或任何臨時起終點查詢結果
- **THEN** 系統 SHALL NOT 啟用卡片置頂手勢或置頂無障礙 action
- **AND** 系統 SHALL 保持既有詳情、ETA 與通知監控入口

#### Scenario: 不限制置頂數量
- **WHEN** 用戶在同一行程連續置頂多條可辨識路線
- **THEN** 系統 SHALL NOT 以預設數量上限拒絕新的置頂

### Requirement: 置頂遵循本次與長期三態轉換
系統 SHALL 將每條可辨識路線建模為未置頂、本次置頂或長期置頂，並 SHALL 使用向右提升、向左完全取消的手勢語義。

#### Scenario: 首次向右滑動建立本次置頂
- **WHEN** 用戶把未置頂路線卡向右滑過觸發門檻
- **THEN** 系統 SHALL 將該路線設為本次置頂
- **AND** 系統 SHALL 顯示包含路線識別的本次置頂 Snackbar
- **AND** Snackbar SHALL 提供「長期置頂」操作

#### Scenario: 本次置頂再次向右升級
- **WHEN** 用戶把本次置頂路線卡再次向右滑過觸發門檻
- **THEN** 系統 SHALL 將該路線升級為目前行程的長期置頂
- **AND** 系統 SHALL 顯示包含路線識別及行程名稱的長期置頂成功訊息

#### Scenario: 從 Snackbar 升級長期置頂
- **WHEN** 用戶在首次置頂 Snackbar 點擊「長期置頂」
- **THEN** 系統 SHALL 產生與再次向右滑動相同的長期置頂結果

#### Scenario: 長期置頂再次向右
- **WHEN** 用戶把長期置頂路線卡向右滑動
- **THEN** 卡片 SHALL 顯示已長期置頂的背景提示後回彈
- **AND** 系統 SHALL NOT 改變置頂時間、順序或資料庫內容
- **AND** 系統 SHALL NOT 為此操作震動或顯示 Snackbar

#### Scenario: 任一置頂向左直接取消
- **WHEN** 用戶把本次置頂或長期置頂路線卡向左滑過觸發門檻
- **THEN** 系統 SHALL 直接移除該路線的全部置頂狀態
- **AND** 長期置頂 SHALL NOT 先降級為本次置頂
- **AND** 系統 SHALL 顯示「已取消置頂」Snackbar 及「撤銷」操作

#### Scenario: 撤銷取消恢復完整狀態
- **WHEN** 用戶取消一條本次或長期置頂路線後點擊「撤銷」
- **THEN** 系統 SHALL 恢復取消前的置頂等級、置頂時間及相對順序

#### Scenario: 普通卡向左不移動
- **WHEN** 用戶嘗試把未置頂路線卡向左滑動
- **THEN** 系統 SHALL NOT 移動卡片或改變任何置頂狀態

### Requirement: 所有置頂共用後置頂優先順序
系統 SHALL 把本次與長期置頂放在同一置頂區域，並按嚴格遞增的置頂 token 降序展示，使後置頂的路線位於先置頂路線上方。

#### Scenario: 後置頂顯示在更上方
- **WHEN** 用戶依次置頂路線 A、B、C
- **THEN** 置頂區域 SHALL 依次展示 C、B、A

#### Scenario: 同一毫秒及時鐘回撥仍保持順序
- **WHEN** 連續置頂操作取得相同系統時間或裝置時鐘向後調整
- **THEN** 系統 SHALL 仍為每次新置頂建立嚴格遞增 token
- **AND** 後發生的置頂 SHALL 位於先發生的置頂上方

#### Scenario: 升級長期不改變位置
- **WHEN** 一條本次置頂路線升級為長期置頂
- **THEN** 系統 SHALL 保留該路線原置頂 token 及相對順序
- **AND** 該路線 SHALL NOT 因升級跳到置頂區域頂部

#### Scenario: 重新啟動後保留長期順序
- **WHEN** App 全新啟動並重新查詢一個具有多條長期置頂的行程
- **THEN** 已匹配的長期置頂 SHALL 按原保存 token 恢復順序
- **AND** 其後新增的本次置頂 SHALL 顯示在既有長期置頂上方

### Requirement: 普通排序只作用於未置頂區域
系統 SHALL 讓目前排序字段與方向只決定未置頂路線的順序，並 SHALL 保持置頂區域不受排序、ETA 或站點預覽更新重排。

#### Scenario: 切換排序不重排置頂
- **WHEN** 用戶在存在多條置頂路線時切換排序字段或方向
- **THEN** 置頂路線 SHALL 保持原置頂順序
- **AND** 未置頂路線 SHALL 按新排序重新排列

#### Scenario: ETA 更新不重排置頂
- **WHEN** 目前按候車時間排序且一條置頂路線收到新的 ETA
- **THEN** 該路線 SHALL 保持原置頂位置
- **AND** 未置頂路線 SHALL 按更新後 ETA 及目前方向重新排列

#### Scenario: 站點預覽更新不重排置頂
- **WHEN** 一條置頂路線收到漸進補齊的站點預覽
- **THEN** 系統 SHALL 更新該卡片內容但保持置頂順序

#### Scenario: 取消置頂回到目前排序位置
- **WHEN** 用戶取消一條置頂路線
- **THEN** 系統 SHALL 將該路線放回目前排序字段與方向所決定的未置頂位置

### Requirement: 本次置頂遵循目前 task 與行程生命週期
系統 SHALL 在同一 Android task 的同一行程查詢上下文內保留本次置頂，並 SHALL 在作用域失效時清除。

#### Scenario: 排序刷新及同一行程重查保留
- **WHEN** 用戶切換排序、接收 ETA／站點預覽、下拉刷新或重新查詢同一已保存行程
- **THEN** 已匹配及暫時未匹配的本次置頂狀態 SHALL 保留

#### Scenario: 切換頂層 destination 後返回
- **WHEN** 用戶從常用頁切到搜尋或設定後返回同一 task 的常用頁
- **THEN** 目前行程的本次置頂 SHALL 保留

#### Scenario: Activity 在同一 task 重建
- **WHEN** 語言、主題、旋轉或系統回收進程令 Activity 在有效 task saved state 下重建
- **THEN** 系統 SHALL 恢復目前行程的本次置頂 fingerprint、token 及狀態
- **AND** 系統 SHALL NOT 從 saved state 恢復完整舊語言路線結果

#### Scenario: 切換已保存行程
- **WHEN** 用戶選擇另一個已保存行程並令原查詢 owner 失效
- **THEN** 系統 SHALL 清除原行程的本次置頂
- **AND** 系統 SHALL NOT 清除原行程的長期置頂

#### Scenario: 全新啟動不恢復本次置頂
- **WHEN** task 已移除、App 被強制停止或 App 在沒有有效 task saved state 下全新啟動
- **THEN** 系統 SHALL NOT 恢復任何本次置頂
- **AND** 系統 SHALL 繼續從 SQLite 恢復長期置頂

### Requirement: 長期置頂持久保存且允許暫時未匹配
系統 SHALL 將長期置頂按行程持久保存，並 SHALL 在目前查詢缺少相同嚴格指紋時保留偏好而不建立虛假結果。

#### Scenario: 全新啟動恢復已匹配長期置頂
- **WHEN** App 全新啟動並查詢具有長期置頂的同一行程
- **AND** 查詢結果包含相同版本及完整指紋
- **THEN** 系統 SHALL 以長期置頂樣式及原順序展示該路線

#### Scenario: 長期置頂暫時缺席
- **WHEN** 一條長期置頂路線未出現在本次成功查詢結果
- **THEN** 系統 SHALL 保留該偏好
- **AND** 系統 SHALL NOT 建立空白卡片、預留位置、增加結果數量或顯示錯誤

#### Scenario: 暫時缺席路線日後恢復
- **WHEN** 先前未匹配的長期置頂在後續查詢重新出現相同嚴格指紋
- **THEN** 系統 SHALL 以原保存 token 恢復其長期置頂位置

#### Scenario: 空結果不刪除偏好
- **WHEN** 刷新或查詢成功返回空結果
- **THEN** 系統 SHALL 隱藏路線卡及置頂分隔列
- **AND** 系統 SHALL 保留本次會話及 SQLite 中的置頂偏好

#### Scenario: 查詢失敗不改變偏好
- **WHEN** Citybus 路線查詢失敗
- **THEN** 系統 SHALL NOT 新增、刪除、降級或重排任何置頂偏好

### Requirement: 長期置頂使用版本化嚴格路線身份
系統 SHALL 以版本前綴及有歧義免疫的固定編碼保存路線指紋，並 SHALL 只在每個有序乘車段的必要身份欄位完整且唯一時允許置頂。

#### Scenario: 指紋包含有序乘車段身份
- **WHEN** 系統為一條可置頂路線建立 `v1` 指紋
- **THEN** 指紋 SHALL 按乘車段順序包含 company、公開路線號、route variant、bound、direction path、上車 sequence 及下車 sequence
- **AND** 每個欄位 SHALL 使用固定順序及無歧義編碼
- **AND** 指紋 SHALL 以 `v1|` 開頭

#### Scenario: 動態及語言資料不影響指紋
- **WHEN** 同一乘車方案的 App 語言、展示名稱、價格、耗時、步行距離、ETA、站點預覽、排序或 `rawInfo` 改變
- **THEN** 系統 SHALL 產生相同 `v1` 指紋

#### Scenario: 身份欄位改變會產生不同指紋
- **WHEN** 任一乘車段的公司、公開路線號、variant、方向、上下車 sequence 或乘車段順序改變
- **THEN** 系統 SHALL 將其視為不同乘車方案

#### Scenario: 身份資料不完整時拒絕置頂
- **WHEN** 路線缺少任何必要乘車段身份欄位
- **THEN** 系統 SHALL NOT 建立本次或長期置頂
- **AND** 卡片 SHALL 回彈並顯示置頂暫不可用提示

#### Scenario: 查詢結果存在重複指紋
- **WHEN** 同一結果集中兩張或以上卡片產生相同嚴格指紋
- **THEN** 系統 SHALL 對全部衝突卡停用置頂
- **AND** 系統 SHALL NOT 以展示內容或 `resultId` 猜測目標

#### Scenario: 指紋版本不同不模糊恢復
- **WHEN** 已保存指紋版本與目前 formatter 產生的版本不同
- **THEN** 系統 SHALL 保留已保存記錄但不匹配目前卡片
- **AND** 系統 SHALL NOT 回退使用路線號、名稱或其他近似資料

### Requirement: 長期置頂讀寫失敗不得損壞可見狀態
系統 SHALL 在背景序列化執行長期置頂資料庫操作，並 SHALL 使用樂觀 UI、原狀態快照及最後意圖勝出規則恢復失敗。

#### Scenario: 路線與長期置頂並行載入
- **WHEN** 用戶首次查詢已保存行程
- **THEN** 系統 SHALL 並行載入路線結果及該行程長期置頂
- **AND** 首次提交可見非空列表 SHALL 等待兩者完成或置頂讀取明確失敗
- **AND** 系統 SHALL NOT 先展示普通順序再突然套用長期置頂

#### Scenario: 讀取長期置頂失敗
- **WHEN** 路線查詢成功但長期置頂讀取失敗
- **THEN** 系統 SHALL 展示路線結果而不套用長期置頂
- **AND** 系統 SHALL 顯示一次非阻塞失敗提示

#### Scenario: 升級長期寫入失敗
- **WHEN** 本次置頂升級為長期置頂後資料庫寫入失敗
- **THEN** 系統 SHALL 恢復原本本次置頂狀態、token 及位置
- **AND** 系統 SHALL 顯示保存失敗提示

#### Scenario: 取消長期刪除失敗
- **WHEN** 長期置頂取消後資料庫刪除失敗
- **THEN** 系統 SHALL 恢復原長期置頂狀態、token 及位置
- **AND** 系統 SHALL 顯示取消失敗提示

#### Scenario: 刪除完成前撤銷
- **WHEN** 用戶取消長期置頂後在底層刪除完成前點擊撤銷
- **THEN** 系統 SHALL 在刪除序列後恢復該長期置頂記錄
- **AND** 較舊 callback SHALL NOT 覆蓋用戶較新的撤銷意圖

#### Scenario: 過期 callback 不修改新行程
- **WHEN** 置頂讀寫期間用戶切換行程、語言、destination 或開始新查詢
- **THEN** 過期 callback SHALL NOT 修改目前列表、Snackbar 或置頂狀態

### Requirement: 橫滑手勢保持可控且不搶佔卡片子操作
系統 SHALL 讓路線卡水平跟手並以約卡片寬度 40% 作為觸發門檻，且 SHALL 排除 ETA 與通知監控的既有觸控區。

#### Scenario: 水平主導手勢跟手
- **WHEN** 用戶從可滑動卡片的非排除區域開始水平主導拖動
- **THEN** 卡片 SHALL 以接近 1:1 位移跟隨手指
- **AND** 系統 SHALL NOT 只因短距離快速 fling 觸發置頂

#### Scenario: 未達門檻平滑回彈
- **WHEN** 用戶釋放卡片時未達約 40% 寬度門檻
- **THEN** 卡片 SHALL 在約 180–240ms 內平滑回彈
- **AND** 系統 SHALL NOT 改變置頂狀態

#### Scenario: 跨過門檻只震動一次
- **WHEN** 用戶在單次拖動中首次跨過有效方向門檻
- **THEN** 系統 SHALL 只提供一次輕量觸覺反饋
- **AND** 在同次拖動中反覆跨過門檻 SHALL NOT 重複震動

#### Scenario: ETA 觸控區不啟動卡片滑動
- **WHEN** 手勢起點命中候車文字可點擊區
- **THEN** 系統 SHALL 保持打開 ETA 班次的既有互動
- **AND** 系統 SHALL NOT 啟動卡片置頂拖動

#### Scenario: 鈴鐺觸控區不啟動卡片滑動
- **WHEN** 手勢起點命中通知鈴鐺的 48dp 觸控區
- **THEN** 系統 SHALL 保持通知監控入口
- **AND** 系統 SHALL NOT 啟動卡片置頂拖動

#### Scenario: 系統動畫關閉
- **WHEN** 裝置的系統動畫比例為 0
- **THEN** 系統 SHALL 立即完成卡片最終位置及狀態
- **AND** 所有置頂操作 SHALL 保持可用

### Requirement: 置頂視覺與區域分隔不佔用卡片操作空間
系統 SHALL 以卡片描邊區分本次置頂，以同一描邊加左側短書籤角標區分長期置頂，並 SHALL 在兩個區域同時存在時使用單一排序分隔列。

#### Scenario: 本次置頂只加強描邊
- **WHEN** 路線處於本次置頂
- **THEN** 卡片 SHALL 使用符合目前明暗主題的語意強調色描邊
- **AND** 系統 SHALL NOT 新增佔用卡片內容行的按鈕或文字標籤

#### Scenario: 長期置頂展示左側短書籤
- **WHEN** 路線處於長期置頂
- **THEN** 卡片 SHALL 顯示與本次置頂一致的描邊及左側短書籤角標
- **AND** 角標 SHALL 位於既有左側 inset 內，不改變卡片高度或侵入右側 ETA／鈴鐺區

#### Scenario: 同時存在置頂及普通結果
- **WHEN** 結果清單同時包含至少一條置頂及一條未置頂路線
- **THEN** 系統 SHALL 在最後一條置頂與第一條未置頂之間顯示一個橫向分隔列
- **AND** 分隔列 SHALL 顯示未置頂數量、目前排序字段及方向，例如「以下 10 條按耗時 ↑ 排序」
- **AND** 分隔列 SHALL NOT sticky 或可滑動

#### Scenario: 單一區域不顯示分隔列
- **WHEN** 沒有置頂路線、全部結果均已置頂或結果為空
- **THEN** 系統 SHALL NOT 顯示置頂／普通分隔列

#### Scenario: 結果摘要保持總數
- **WHEN** 一條或多條路線被置頂
- **THEN** 結果摘要中的總路線數 SHALL 保持本次查詢唯一結果總數
- **AND** 系統 SHALL NOT 把分隔列計入結果數量

### Requirement: 置頂移動保持長列表視口
系統 SHALL 以穩定 item identity 及差異動畫移動置頂或取消置頂卡片，並 SHALL 保持用戶目前閱讀位置。

#### Scenario: 深處卡片置頂
- **WHEN** 用戶在長列表較深位置置頂一張卡片
- **THEN** 系統 SHALL 將卡片資料位置移入置頂區域
- **AND** 清單 SHALL 保持目前視口而不自動捲到頂部

#### Scenario: 取消置頂
- **WHEN** 用戶取消一張置頂卡片
- **THEN** 系統 SHALL 以差異移動把卡片放回目前普通排序位置
- **AND** 清單 SHALL 保持目前第一個可見項目及其偏移

#### Scenario: 卡片內容漸進更新
- **WHEN** ETA 或站點預覽更新同一穩定卡片
- **THEN** 系統 SHALL 更新必要內容而不把卡片視為另一條結果
- **AND** 系統 SHALL NOT 因全量列表刷新造成可見卡片閃爍

### Requirement: 置頂提供三語與完整無障礙操作
系統 SHALL 為所有置頂文案、狀態、Snackbar、錯誤、分隔列及無障礙 action 提供香港繁體、獨立審校簡體與自然英文資源，並 SHALL 讓 TalkBack 用戶完成相同行為。

#### Scenario: 普通卡無障礙 action
- **WHEN** TalkBack 聚焦可置頂的普通路線卡
- **THEN** 系統 SHALL 提供「本次置頂」或目前語言的等效自訂 action

#### Scenario: 本次置頂無障礙 action
- **WHEN** TalkBack 聚焦本次置頂路線卡
- **THEN** 系統 SHALL 宣讀本次置頂狀態
- **AND** 系統 SHALL 提供「長期置頂」及「取消置頂」自訂 action

#### Scenario: 長期置頂無障礙 action
- **WHEN** TalkBack 聚焦長期置頂路線卡
- **THEN** 系統 SHALL 宣讀長期置頂狀態
- **AND** 系統 SHALL 提供「取消置頂」自訂 action
- **AND** 裝飾性書籤 SHALL NOT 被重複朗讀

#### Scenario: 分隔列完整朗讀
- **WHEN** TalkBack 聚焦置頂與普通區域之間的分隔列
- **THEN** 系統 SHALL 以完整句子朗讀未置頂數量、排序字段及方向

#### Scenario: 三語明暗及大字體
- **WHEN** 用戶在繁體、簡體或英文，淺色或深色，約 360dp 寬度及 font scale 1.0／1.3／2.0 查看置頂結果
- **THEN** 卡片、書籤、分隔列及 Snackbar SHALL 保持可辨識、可讀且不與 ETA、鈴鐺或核心文字重疊
