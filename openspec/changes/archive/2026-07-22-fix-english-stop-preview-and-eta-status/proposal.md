## Why

英文 `showstops2.php` 回應中的站名可能包含 `King\'s Road` 一類 JavaScript 轉義字面量，現有解析器會把轉義單引號誤判為參數邊界，導致部分路線卡上落車站為空，且首程 ETA 因無法取得 stop id 而被誤顯示為 `No live arrivals`。同時，現有候車狀態把「成功查詢但沒有有效班次」與網絡、站點映射或回應解析失敗合併，讓用戶無法判斷是否真的暫無車輛。

## What Changes

- 讓 `showstops2.php` 的 `addstoponmap(...)` 解析器正確處理引號、反斜線、逗號及括號等 JavaScript 字面量內容，恢復包含英文撇號站名的 P2P stop map。
- 保證英文路線卡能使用同一 P2P stop map 顯示有效的上車站與下車站，並讓 ETA 復用同一 stop id 推導結果。
- 將 ETA 結果分為「查詢成功但沒有匹配班次」與「站點映射、網絡或回應解析等技術原因暫不可用」，以三語自然文案分別展示，並保留可診斷的內部原因。
- 將簡體候車載入提示縮短為 `候车查询中`，不改變繁體與英文載入文案。
- 保留簡體 Citybus 站名可能仍為繁體的既有行為，將其記錄為 `docs/technical-debt.md` 的 TD-001；本次不接入 DATA.GOV.HK stop 名稱、不做簡繁轉換，也不增加跨語言 fallback。

## Capabilities

### New Capabilities

無。

### Modified Capabilities

- `citybus-p2p-stop-map`：擴充 `addstoponmap(...)` 解析要求，支援 JavaScript 字串中的轉義引號、反斜線、逗號與括號。
- `route-card-stop-preview`：要求英文站名包含撇號等合法字元時仍能生成完整上落車站預覽。
- `citybus-first-leg-eta`：區分成功但無匹配班次與技術失敗，並保留具體失敗原因供診斷。
- `route-query-results-layout`：以三語分開展示暫無車輛及候車暫不可用，並縮短簡體載入提示。

## Impact

- 主要影響 `data/model` 的候車狀態、`data/repository` 的 P2P stop map／ETA 解析，以及 `ui/main` 的候車狀態格式化與顏色映射。
- 修改繁體、簡體和英文 string resources；不新增外部依賴、不修改持久化 schema，也不改變 Citybus、DATA.GOV.HK 請求參數或跨語言回退策略。
- 外部格式假設仍限於 Citybus `showstops2.php` 的 `addstoponmap(...)` JavaScript 調用和 DATA.GOV.HK ETA JSON；需以包含 `King\'s Road` 的固定回歸樣例、有效空 ETA、無效 ETA 回應及網絡失敗測試覆蓋。
- 驗證包含相關 JVM 單元測試、三語資源鍵一致性、完整 `./gradlew build`，以及可用網絡環境下的英文真實路線抽查；簡體站名正體化不屬於本次驗收門檻。
