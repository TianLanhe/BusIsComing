## Context

路線查詢先解析 Citybus P2P 候選結果，再以 `rawInfo + lang` 查詢 `showstops2.php`。`CitybusP2pStopMapResolver` 目前使用簡單正規表達式擷取 `addstoponmap(...)`，並在解析參數時遇到每個單引號都切換字串狀態；英文站名 `Healthy Gardens, King\'s Road` 會因此提前結束字串，使參數數量不足，整個站點被捨棄。站點預覽需要首末站都存在，而首程 ETA 亦需要同一 stop map 的上車 stop id，因此同一缺陷會同時造成英文預覽為空及 ETA 被誤標為無班次。

現有 `WaitTimeState.Unavailable` 同時代表有效空 ETA、stop map 不可用、上車站缺失、網絡錯誤及無效 JSON。UI 只能顯示單一文案，英文因而把技術失敗顯示為 `No live arrivals`。

## Goals / Non-Goals

**Goals:**

- 正確解析 `addstoponmap(...)` 中含轉義引號、反斜線、逗號及括號的 JavaScript 字串。
- 讓英文站點預覽和 ETA stop id 使用同一份修復後的 P2P stop map。
- 在領域狀態中分開「有效回應但沒有匹配班次」與「技術原因暫不可用」，並保留具體原因供測試及診斷。
- 為兩種 ETA 結果提供自然的繁體、簡體和英文文案，並縮短簡體載入提示。

**Non-Goals:**

- 不修復 Citybus `l=2` 仍可能返回繁體站名的上游限制；該項記錄於 TD-001。
- 不接入 DATA.GOV.HK stop 名稱、不做簡繁機器轉換、不跨語言重試或回退。
- 不修改 Citybus／DATA.GOV.HK URL、query、header、快取鍵、並發上限或排序規則。
- 不重構整體 Citybus HTML／JavaScript 解析架構。

## Decisions

### 1. 以逐字掃描器取代正規表達式擷取函式調用

解析器從 `addstoponmap(` 起逐字掃描，記錄目前引號、反斜線轉義及括號深度；只有在字串外遇到配對的右括號才結束調用。參數解析同樣只在字串外的逗號切分，並依 JavaScript 字串語義還原常見轉義字符。這能同時處理 `King\'s Road`、字串內逗號／括號及反斜線，而不改變既有欄位映射。

否決只為 `\'` 增加一個正規表達式例外，因為它仍會在括號、逗號或連續反斜線組合下失效；亦不引入完整 JavaScript 執行器，避免擴大攻擊面和依賴。

### 2. P2P stop map 仍是預覽與 ETA 的唯一 route-variant 對齊來源

修復後的 stop map 同時供 `RouteStopPreview` 和首程 ETA stop id 查找使用；解析失敗仍不以公開 `route-stop` 或其他語言資料替代。這保留 P2P route variant 與 station seq 的既有對齊契約，也避免英文顯示和 ETA 使用不同站點。

否決在英文失敗時改查繁體或依站名猜測 stop id，因為會破壞 `LanguageSnapshot` 一致性，並可能把不同 route variant 的站點錯配。

### 3. 用領域狀態表達 ETA 空結果與技術故障

`WaitTimeState` 保留 `Loading`、`Available`，新增成功空結果 `NoArrivals`，並把技術失敗表示為帶 `EtaUnavailableReason` 的 `Unavailable`。原因至少覆蓋首程元數據缺失、stop map 請求失敗、stop map 回應無效、上車站不存在、ETA 請求失敗及 ETA 回應無效。

當 ETA HTTP 回應具有可辨識的 `data` 陣列但沒有嚴格或降級匹配的有效 ETA 時，結果為 `NoArrivals`；無法取得有效 stop id、請求拋錯或回應缺少有效 ETA 資料結構時為 `Unavailable(reason)`。UI 只分成兩個自然文案，但結構化原因會保留在狀態中，供測試、日誌或後續遙測使用。

否決只靠 UI 根據錯誤字串推測狀態，因為 repository 已失去原始原因後無法可靠恢復；亦不把所有空記錄視為解析失敗，避免把真實無班次誤報成系統故障。

### 4. 三語文案由資源層映射

格式化層將 `NoArrivals` 映射為 `暫無車輛`／`暂无车辆`／`No live arrivals`，將 `Unavailable` 映射為 `候車暫不可用`／`候车暂不可用`／`Arrivals unavailable`。簡體 `Loading` 使用較短的 `候车查询中`；繁體 `候車查詢中` 和英文 `Checking arrivals` 保持不變。顏色仍依可用、載入、非可用三類處理，兩種非可用狀態使用一致的次要色，避免增加新的視覺語義。

否決在 Kotlin 中硬編碼或組合文案，以維持 Android resource qualifier、無障礙與三語鍵一致性要求。

## Risks / Trade-offs

- [上游 JavaScript 使用未覆蓋的複雜語法] → 掃描器限定解析既有函式調用和字串參數；以真實轉義樣例、括號／逗號／反斜線單測保護，未知結構安全地視為 stop map 不可用。
- [輕量 ETA JSON 結構判斷把部分異常回應當成無班次] → 只有存在可辨識 `data` 陣列才允許 `NoArrivals`，缺少必要資料結構一律標記 `ETA_RESPONSE_INVALID`。
- [新增 sealed state 造成未更新的 `when` 分支] → 由 Kotlin exhaustive `when` 編譯檢查與全量 build 找出所有消費端；排序和通知監控維持只有 `Available` 可用的既有判斷。
- [技術故障文案比英文主候車區更長] → 沿用既有固定候車區和省略策略，並按三語、360dp、字體縮放驗收矩陣抽查，不縮小字體。

## Migration Plan

1. 先加入 parser、ETA state 和 formatter 的失敗測試，確認現況可重現。
2. 實作掃描器與結構化 ETA 狀態，更新所有 sealed state 消費端。
3. 補齊三語資源及資源鍵一致性測試，運行相關單測和完整 build。
4. 在可用網絡環境以含英文撇號站名的路線抽查站點預覽與 ETA。

此改動不涉及資料庫或持久化遷移；回退時可整體回退代碼與資源，既有用戶資料不受影響。

## Open Questions

無；簡體站名後續方案已獨立記錄為 TD-001。
