## MODIFIED Requirements

### Requirement: 按需查询 Citybus P2P 路线详情
系統 SHALL 在用戶點擊路線卡片後，使用候選的 P2P 詳情元數據及與其 `lid` 匹配的短期 Citybus session 按需請求路線詳情，且 SHALL NOT 為該請求設置靜態瀏覽器 header 或無關 Cookie。

#### Scenario: 構造詳情請求
- **WHEN** 系統獲得 `rawInfo`、`ginfo`、`lid`、`lang` 與可解析的匹配 session reference
- **THEN** 系統 SHALL 請求 `https://mobile.citybus.com.hk/nwp3/getp2pstopinroute.php`
- **AND** 請求 SHALL 攜帶 `info=<rawInfo>`、`ginfo=<ginfo>`、`lid=<lid>` 和 `l=<lang>`
- **AND** 請求 SHALL 只增加 `Cookie: PHPSESSID=<matching session value>`

#### Scenario: 詳情請求不攜帶瀏覽器 header 或無關 Cookie
- **WHEN** 系統發起 `getp2pstopinroute.php` 請求
- **THEN** 系統 SHALL NOT 顯式設置 `User-Agent`、`Referer`、`Sec-Fetch-*`、`sec-ch-ua*`、`Connection` 或 `Accept-Language`
- **AND** 系統 SHALL NOT 發送 ad、consent、tracking、未知或其他模式的 Cookie
- **AND** session Cookie SHALL NOT 被轉送至 `getlinep2p.php`、Google、DATA.GOV.HK 或其他 host

#### Scenario: 點擊後展示載入狀態
- **WHEN** 用戶進入全屏路線詳情頁且詳情請求尚未完成
- **THEN** 頁面 SHALL 立即展示由路線結果提供的摘要與詳情載入狀態
- **AND** 載入狀態 SHALL NOT 清空來源頁已有路線結果
- **AND** 返回操作 SHALL 保持可用

#### Scenario: 詳情 session 可恢復
- **WHEN** 匹配 session 缺失、過期或上游回應呈現 session-missing 形態
- **THEN** 系統 SHALL 依 `citybus-route-query-api` 的受控恢復契約重建一次候選關聯並重試詳情
- **AND** 頁面 SHALL 保留啟動摘要及已成功載入的地圖內容

#### Scenario: 詳情接口不使用公共 API 兜底
- **WHEN** Citybus P2P 詳情請求、session 恢復或站點主結構解析最終失敗
- **THEN** 系統 SHALL 展示詳情失敗或部分資料狀態
- **AND** 系統 SHALL NOT 調用 DATA.GOV.HK route-stop 或 stop 接口重建路線詳情

#### Scenario: 語言或請求 generation 已過期
- **WHEN** 詳情請求執行期間實際語言改變、頁面被銷毀或重試建立新的 request generation
- **THEN** 系統 SHALL 取消舊工作或忽略舊回應
- **AND** 舊回應 SHALL NOT 覆蓋目前頁面的語言、session 關聯或較新結果

#### Scenario: 最小 session 詳情 live 驗證
- **WHEN** 實作完成後執行 live 驗證
- **THEN** `getp2pstopinroute.php` SHALL 覆蓋繁體、簡體、英文及單段、多段有效樣本
- **AND** 每個樣本 SHALL 只使用匹配的 `PHPSESSID` 而不使用瀏覽器 header／無關 Cookie
- **AND** 每個樣本 SHALL 返回可解析站點、乘車段與預期數量的步行距離
- **AND** 驗證記錄 SHALL NOT 保存原始 `PHPSESSID`

### Requirement: 路线详情成功结果缓存 1 天
系統 SHALL 按資料語義分開快取成功解析的站點結構與步行距離，讓相同穩定查詢上下文可跨不同 session 重用一天；系統 SHALL NOT 把時間敏感資料或 session 身分併入同一長期快取。

#### Scenario: 快取成功站點結構
- **WHEN** 系統成功解析某個乘車方案在目前語言的站點、方向及穩定乘車段資料
- **THEN** 系統 SHALL 以 `plan fingerprint + lang` 在 App 進程內快取該結構 1 天
- **AND** cache key SHALL NOT 包含 `PHPSESSID`、session reference 或 `lid`

#### Scenario: 快取完整分段步行距離
- **WHEN** 系統對某一穩定起點、終點及乘車方案取得完整起點／轉乘／終點步行距離
- **THEN** 系統 SHALL 以穩定起點、終點及 plan fingerprint 在 App 進程內快取純數值距離 1 天
- **AND** 新 session 查詢相同上下文 SHALL 可命中該快取
- **AND** 不同起點或終點 SHALL NOT 串用首尾步行距離

#### Scenario: 地點穩定識別
- **WHEN** 起點或終點有可用 provider identifier
- **THEN** 步行 cache key SHALL 優先使用該 identifier
- **AND** identifier 缺失時 SHALL 使用送入 P2P 查詢的規範化坐標

#### Scenario: 計劃時間與 ETA 不進入一天快取
- **WHEN** 詳情包含計劃上車、下車、到達時間或 DATA.GOV.HK 即時 ETA
- **THEN** 系統 SHALL 使用本次查詢資料或各自既有短期刷新策略
- **AND** 系統 SHALL NOT 將這些時間敏感欄位作為一天詳情快取的一部分重用

#### Scenario: session 缺失空資料不快取
- **WHEN** 站點可解析但 `showtimetable1(...)` 與所有必要步行欄位因 session 缺失而為空
- **THEN** 系統 SHALL NOT 把空距離保存為成功步行快取
- **AND** 空距離 SHALL NOT 覆蓋已有完整步行快取

#### Scenario: 部分或失敗結果不污染完整快取
- **WHEN** 詳情只包含部分距離、請求失敗、回應為空或解析失敗
- **THEN** 系統 SHALL 只保存已通過其資料域完整性要求的內容
- **AND** 未知欄位 SHALL NOT 以零值寫入
- **AND** 後續恢復或重試 SHALL 可取得並覆蓋為新的完整資料

#### Scenario: 快取過期
- **WHEN** 某一結構或步行快取保存時間超過 1 天
- **THEN** 系統 SHALL 依目前有效 session 重新請求或恢復 Citybus 詳情
- **AND** 新的成功解析結果 SHALL 替換同資料域的舊快取

### Requirement: 路線詳情摘要展示可判定的完整方案指標
系統 SHALL 在 persistent bottom sheet 的摘要區展示路線鏈、總耗時、預計到達時間、總票價、總途經站數、步行距離及可用首程即時 ETA，並 SHALL 明確處理卡片摘要與完整分段的差異。

#### Scenario: 顯示路線摘要
- **WHEN** 系統有可用路線結果摘要
- **THEN** 摘要 SHALL 展示路線鏈、總耗時與總票價
- **AND** 有最終預計到達時間時摘要 SHALL 展示 `預計 HH:mm 到達` 或目前語言等效文案
- **AND** 摘要 SHALL 展示各乘車段途經站數之和且 SHALL NOT 重複計算上下車或換乘端點
- **AND** 有可靠首程即時 ETA 時摘要 SHALL 以緊湊形式展示該狀態

#### Scenario: 完整步行分段距離可用
- **WHEN** 起點、所有必要步行換乘與終點距離均已識別且完整
- **THEN** 詳情摘要 SHALL 顯示這些分段距離之和
- **AND** 摘要 SHALL 使用已確認的步行人物圖示與距離
- **AND** 系統 SHALL NOT 將完整合計回填至路線卡片或改變列表排序

#### Scenario: 完整步行合計不可判定
- **WHEN** 一個或多個必要步行段的距離缺失
- **THEN** 詳情摘要 SHALL 回退顯示 `ppsearch_p3.php` 路線卡片步行距離
- **AND** 摘要 SHALL 顯示目前語言的部分距離來源說明
- **AND** 系統 SHALL NOT 以缺失分段為零宣稱完整總量

#### Scenario: 摘要隨詳情內容捲動
- **WHEN** 用戶把詳情窗展開至半屏或全屏並向下瀏覽時間線
- **THEN** 摘要 SHALL 作為詳情列表首項正常捲出畫面
- **AND** 系統 SHALL NOT 把完整摘要固定在詳情窗頂部而壓縮時間線空間

#### Scenario: 從展開狀態收合至摘要
- **WHEN** 詳情列表未在頂部且用戶把詳情窗收合至摘要態
- **THEN** 系統 SHALL 先恢復列表頂部以完整展示摘要
- **AND** 摘要 SHALL NOT 停留在部分捲出或內部捲動狀態

#### Scenario: 大字體摘要超出普通目標高度
- **WHEN** font scale 1.3 或 2.0 令摘要無法容納於普通 25% 至 30% 目標高度
- **THEN** 摘要態 SHALL 按內容增高且半屏態 SHALL 不低於摘要所需高度
- **AND** 系統 SHALL NOT 縮字、裁切核心文字或讓摘要本身內部捲動

## ADDED Requirements

### Requirement: 路線詳情辨識並展示分段步行完整性
系統 SHALL 分別解析與展示起點、每次步行換乘及終點步行距離，並區分完整、部分及 session 缺失結果；系統 SHALL NOT 從總距離反推未知分段。

#### Scenario: 單段路線完整距離
- **WHEN** Citybus 詳情為單段路線提供起點至上車站及下車站至終點兩個距離
- **THEN** 時間線 SHALL 在對應首尾步行段分別展示準確米數
- **AND** 詳情 SHALL 將兩段標記為完整

#### Scenario: 多段路線完整距離
- **WHEN** Citybus 詳情為多段路線提供起點、每次步行換乘及終點距離
- **THEN** 時間線 SHALL 依實際方案次序展示每個距離
- **AND** 距離數量 SHALL 等於乘車段數加一

#### Scenario: 同站換乘
- **WHEN** Citybus 詳情標記相鄰乘車段為同站換乘
- **THEN** 時間線 SHALL 顯示目前語言的「同站換乘」
- **AND** 系統 SHALL NOT 顯示 `0 米`、未知步行距離或步行虛線
- **AND** 同站換乘 SHALL NOT 被計入必要步行段數量

#### Scenario: 只有部分距離
- **WHEN** 站點與轉乘類型可解析但一個或多個必要步行距離缺失
- **THEN** 時間線 SHALL 顯示所有已知分段米數
- **AND** 未知段 SHALL 保留步行語義但不顯示推算數字
- **AND** 系統 SHALL 將整體標記為部分完整

#### Scenario: 三語兼容解析
- **WHEN** 繁體、簡體或英文詳情以 `showtimetable1(...)` 或對應 HTML 標籤提供步行距離
- **THEN** 系統 SHALL 解析相同的數值與分段次序
- **AND** parser SHALL NOT 只依賴繁體 `步行距離(約)` 文本

#### Scenario: session 缺失形態
- **WHEN** 站點仍可解析，但 timetable payload 為空且所有應有步行距離欄位同時為空
- **THEN** 系統 SHALL 將結果分類為 session 缺失並觸發一次受控恢復
- **AND** 系統 SHALL NOT 把該形態誤判為合法的全零或永久未知距離
