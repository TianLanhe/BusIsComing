## MODIFIED Requirements

### Requirement: 路线详情成功结果缓存 1 天
系統 SHALL 按資料語義分開快取已通過完整性驗證的站點結構與完整步行距離，讓相同穩定查詢上下文可跨 Activity 與不同 session 在 App 進程內重用一天；系統 SHALL NOT 把時間敏感資料、分段票價、session 身分、派生 UI 值或載入／錯誤狀態併入同一長期快取。

#### Scenario: 快取成功站點結構
- **WHEN** 系統成功解析某個乘車方案在目前語言的站點、方向及穩定乘車段資料
- **AND** 每段站序已證明完整並與查詢方案對齊
- **THEN** 系統 SHALL 以 `plan fingerprint + lang` 在 App 進程內快取該結構 1 天
- **AND** cache key SHALL NOT 包含 `PHPSESSID`、session reference 或 `lid`

#### Scenario: 重新進入命中進程結構快取
- **WHEN** 用戶離開詳情頁後在同一 App 進程與語言內再次開啟相同 plan fingerprint
- **AND** 已驗證結構快取尚未過期
- **THEN** 新頁面 SHALL 在網絡動態詳情完成前使用該結構展示站點與正確乘坐站數
- **AND** 系統 SHALL NOT 因建立新的 Activity 或 repository consumer 而遺失該快取

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
- **WHEN** 詳情包含計劃上車、下車、到達時間、分段票價或 DATA.GOV.HK 即時 ETA
- **THEN** 系統 SHALL 使用本次查詢資料或各自既有短期刷新策略
- **AND** 系統 SHALL NOT 將這些易變欄位作為一天詳情快取的一部分重用
- **AND** 結構快取命中後系統 SHALL 仍可並發取得本次動態詳情

#### Scenario: session 缺失空資料不快取
- **WHEN** 站點可解析但 `showtimetable1(...)` 與所有必要步行欄位因 session 缺失而為空
- **THEN** 系統 SHALL NOT 把空距離保存為成功步行快取
- **AND** 空距離 SHALL NOT 覆蓋已有完整步行快取

#### Scenario: 部分或失敗結果不污染完整快取
- **WHEN** 詳情站序殘缺、只包含部分距離、請求失敗、回應為空或解析失敗
- **THEN** 系統 SHALL 只保存已通過其資料域完整性要求的內容
- **AND** 未知欄位 SHALL NOT 以零值寫入
- **AND** 殘缺或較差結果 SHALL NOT 覆蓋已有完整資料域
- **AND** 後續恢復或重試 SHALL 可取得並原子替換為新的完整資料

#### Scenario: 快取過期
- **WHEN** 某一結構或步行快取保存時間超過 1 天
- **THEN** 系統 SHALL 依目前有效 session 重新請求或恢復 Citybus 詳情
- **AND** 新的成功解析結果 SHALL 替換同資料域的舊快取

#### Scenario: App 進程結束
- **WHEN** App 進程結束後重新啟動
- **THEN** 系統 SHALL 將結構與步行資料視為冷快取
- **AND** 系統 SHALL NOT 為本能力新增磁碟、偏好或資料庫持久化

### Requirement: 路線詳情摘要展示可判定的完整方案指標
系統 SHALL 在 persistent bottom sheet 的摘要區展示路線鏈、總耗時、預計到達時間、總票價、乘坐站數、步行距離及可用首程即時 ETA，並 SHALL 明確處理站數可靠性、卡片摘要與完整分段的差異。

#### Scenario: 顯示路線摘要
- **WHEN** 系統有可用路線結果摘要
- **THEN** 摘要 SHALL 展示路線鏈、總耗時與總票價
- **AND** 有最終預計到達時間時摘要 SHALL 展示 `預計 HH:mm 到達` 或目前語言等效文案
- **AND** 已驗證站序可用時摘要 SHALL 展示每段途經站加該段下車站的總和
- **AND** 摘要 SHALL NOT 計算任何乘車段的上車站或額外計算換乘端點
- **AND** 有可靠首程即時 ETA 時摘要 SHALL 以緊湊形式展示該狀態

#### Scenario: 相鄰上下車乘車段
- **WHEN** 一個已驗證乘車段的上車站與下車站相鄰且沒有途經站
- **THEN** 該段 SHALL 為摘要乘坐站數貢獻 1 站
- **AND** 兩個相鄰上下車乘車段 SHALL 合計為 2 站，即使兩段為同站換乘

#### Scenario: 可靠站序仍在載入
- **WHEN** 頁面尚未取得已驗證站序或未過期結構快取
- **THEN** 摘要 SHALL 使用目前語言展示站數載入狀態
- **AND** 摘要 SHALL NOT 使用 plan 差值、空集合或預設整數顯示 `0 站`

#### Scenario: 站序最終不可用
- **WHEN** 詳情請求、受控恢復或站序完整性驗證最終失敗
- **AND** 頁面沒有可用的已驗證結構快取
- **THEN** 摘要 SHALL 使用目前語言展示站數暫時無法載入
- **AND** 摘要 SHALL NOT 把失敗或未知狀態格式化為 `0 站`

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

### Requirement: 詳情站點主結構通過完整性門禁
系統 SHALL 在發布時間線、計算乘坐站數、寫入結構快取或以站點端點驗證幾何前，證明 Citybus 詳情的每個乘車段與查詢方案及完整站序一致。

#### Scenario: 乘車段站序完整
- **WHEN** parser 產生某段上車站、途經站與下車站
- **THEN** 該段 route variant、公開路線號、上車 seq 與下車 seq SHALL 與查詢方案一致
- **AND** 所有 seq SHALL 唯一、嚴格遞增並完整覆蓋 `boardingSeq..alightingSeq`
- **AND** 上下車角色、stop id 與坐標 SHALL 有效

#### Scenario: 中間站序缺失或重複
- **WHEN** 某段缺少 `boardingSeq..alightingSeq` 之間的任一 seq、包含重複 seq 或端點不一致
- **THEN** 系統 SHALL 將該站點主結構視為不可靠
- **AND** 系統 SHALL NOT 展示、計數、快取該殘缺結構或用其驗證幾何

#### Scenario: 殘缺站序受控恢復
- **WHEN** 首次詳情回應的站點主結構不可靠
- **THEN** 系統 SHALL 依可用 recovery context 恢復 query／session，或在沒有 recovery context 時直接重試一次
- **AND** 第二次仍不可靠時系統 SHALL 停止自動恢復並展示局部詳情失敗

#### Scenario: 可選欄位缺失不否定完整站序
- **WHEN** 站序主結構完整但方向、分段票價、預計時間或部分步行距離缺失
- **THEN** 系統 SHALL 發布可靠站點主結構並將缺失可選欄位保持為空
- **AND** 系統 SHALL NOT 因可選欄位缺失而把完整站序視為失敗

### Requirement: 相同詳情請求進程內 single-flight
系統 SHALL 讓同一 App 進程內同時發起的相同 Citybus 詳情 request identity 共用一個上游工作，並 SHALL 讓每個 consumer 獨立遵守自己的頁面生命週期。

#### Scenario: 多個 consumer 同時請求相同詳情
- **WHEN** 多個有效 consumer 同時請求相同 `rawInfo`、`generalInfo`、`listId`、語言、plan、recovery context 與 session reference
- **THEN** 系統 SHALL 只執行一個共享上游詳情工作
- **AND** 完成後系統 SHALL 只向仍有效的 consumer 分發結果

#### Scenario: 不同 request identity 不合併
- **WHEN** 兩個詳情請求的語言、plan、端點 context、query 元數據或 session reference 任一不同
- **THEN** 系統 SHALL 將其視為不同 request identity
- **AND** 系統 SHALL NOT 因路線號相同而錯誤共用上游結果

#### Scenario: 單一 consumer 離開
- **WHEN** 一個等待共享詳情工作的頁面被銷毀但仍有其他有效 consumer
- **THEN** 系統 SHALL 停止向已銷毀頁面派送 callback
- **AND** 系統 SHALL NOT 中斷其他 consumer 仍需要的共享工作

#### Scenario: 共享詳情失敗
- **WHEN** 共享詳情工作以網絡、解析或完整性錯誤結束
- **THEN** 系統 SHALL 結束該次 flight 且 SHALL NOT 快取該錯誤
- **AND** 後續手動重試 SHALL 建立新的 request generation 與 flight
