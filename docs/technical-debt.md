# 技術債清單

本文件記錄已確認但經產品或工程決策主動延期的技術遺留。每個條目應説明目前影響、已驗證根因、延期邊界、後續建議及關閉條件；正式處理時應另建 OpenSpec change，不在已完成的 change 中追加未完成任務。

## TD-001：簡體路線卡站名仍可能顯示繁體

- **狀態**：已確認，主動延期
- **記錄日期**：2026-07-18
- **影響範圍**：簡體中文下，查詢結果卡片的上車站及下車站名稱
- **目前影響**：路線、站點身份、stop id、ETA 及查詢功能不受影響，但路線卡可能顯示 Citybus 返回的繁體站名，與目前 App 簡體語言不一致。

### 已驗證根因

- App 已按簡體語言向 Citybus `showstops2.php` 傳遞 `l=2`。
- Citybus 官方 mobile client 只定義 `l=0/1/2`；對已驗證的 8X 及 N118 P2P 樣本，`l=2` 的站名及完整回應與 `l=0` 相同，測試過的其他數值及字串亦沒有返回簡體站名。
- 路線卡目前直接展示 P2P stop map 的 `displayName`，因此無法只靠調整 `l` 取得簡體站名。
- DATA.GOV.HK `GET /v2/transport/citybus/stop/{stopId}` 可返回同一站點的 `name_tc`、`name_sc` 及 `name_en`，但接入後會增加冷快取請求、並發、超時、降級及快取一致性成本。

### 本次延期邊界

- 不在目前英文站點／ETA 修復中接入 DATA.GOV.HK 站名補齊。
- 不使用機器繁簡轉換，也不猜測未公開的 Citybus 語言參數。
- 保留目前繁體原文降級，避免因額外服務失敗而隱藏整條上車站／下車站資訊。

### 後續推薦方案

1. 繼續以 P2P `showstops2` 作為路線分支、leg、sequence 及 stop id 的身份來源，不改回公開 `route-stop` 站序。
2. 僅在需要補齊簡體站名時，按上車站／下車站 stop id 查詢 DATA.GOV.HK stop API。
3. 按 stop id 快取包含三語及 `data_timestamp` 的完整站點記錄，不按請求語言重複快取單一字串。
4. 對跨卡片 stop id 去重，合併相同 in-flight 請求並限制並發；站名補齊不得阻塞或延遲 ETA 狀態更新。
5. 成功記錄可使用有限 TTL；HTTP 失敗、空 `data`、解析失敗及 Citybus 原名降級不得當作成功簡體結果長期快取。
6. 簡體欄位缺失時按 `name_sc → name_tc → name_en` 回退並保留實際欄位語言；整體請求失敗時暫時展示 Citybus 原名，後續查詢可重試。

### 關閉條件

- 真實 Citybus P2P 樣本能維持正確路線分支、上落車 sequence 及 stop id。
- 真實 DATA.GOV.HK stop 樣本在簡體下優先展示 `name_sc`，且不進行機器翻譯。
- 相同 stop id 的請求可去重，語言切換不造成跨語言快取污染或不必要的重複網路請求。
- DATA.GOV.HK timeout、HTTP 失敗、HTTP 200 空 `data` 及欄位缺失均有回歸測試，失敗時路線卡仍可用且不把降級值長期快取為簡體成功結果。
- 站名補齊與 ETA 並行時，ETA 不等待站名查詢完成；完成真實裝置三語及網路降級驗證。

## TD-002：Google Play flexible update 真實裝置驗收

- **狀態**：網站強制開關已刪除，等待真實 IAS flexible flow 驗收後關閉
- **記錄日期**：2026-07-24
- **最近更新**：2026-08-03
- **影響範圍**：App 自動與手動更新檢查、更新渠道選擇及 Play flexible update
- **目前影響**：正常構建固定使用 Google Play 優先分流；Debug 構建不宣稱 Play 已是最新。v11 網站 signed universal APK 與 metadata 已完成發佈鏈驗證，目前只餘 IAS v10 → v11 flexible update 真實裝置門檻。

### 已驗證狀態

- 正常 runtime 固定建立 Play source；只有沒有可用 Play 的非 Play／未知非 Play 初始安裝使用網站渠道。
- Debug 構建在 installer、Play package、Play source 與網站 source 前短路，手動提供 Play 恢復提示，自動保持靜默及 24 小時節流。
- `ERROR_APP_NOT_OWNED` 只有在網站 `versionCode` 較高時形成 Play 渠道可靠更新；相等、較低、網絡失敗或非法 metadata 均保持無法驗證，不再誤報最新。
- 網站 v11 metadata 為 `versionCode=11`、`versionName=1.0`、`sizeBytes=6094814`；APK application ID 為 `com.golink.busiscoming`，簽名為 Play app signing SHA-256 `33:D0:0B:A0:B0:3A:EA:3F:38:2D:82:42:93:CE:03:5F:9D:8C:92:B3:A4:C1:E6:6E:AE:DF:F8:2D:BD:04:8D:58`。

### 本次延期邊界

- 自動化 fake 與契約測試可驗證分流及狀態矩陣，但不得取代已擁有 App 的真實 Play 帳號與裝置上的 flexible flow。
- 真實帳號重現 `ERROR_APP_NOT_OWNED` 可作補充證據，不是關閉本條目的硬門檻；確定性網站矩陣已負責防止誤報最新。
- 不以 Debug APK、直接 adb 安裝或網站 APK 代替 Internal App Sharing 的 Play 擁有權及更新交付證據。

### 剩餘驗收步驟

1. 取得同一 Internal App Sharing 渠道的 Play signed v10 與 v11 測試連結。
2. 使用目標真實帳號由 Play 安裝 v10，確認帳號擁有權及目前版本。
3. 發佈或開啟 v11，驗證 App 內檢查識別較高 `versionCode` 並啟動 flexible update。
4. 驗證取消／返回、重新進入、下載完成、`completeUpdate()`、升級到 v11 及小紅點清除。
5. 記錄帳號軌道、裝置、v10／v11 版本及日期後，勾選 OpenSpec 任務 7.2 並關閉本條目。

### 關閉條件

- Internal App Sharing v10 → v11 在真實裝置與已擁有 App 的帳號完成資格判斷及 flexible update。
- 取消／返回、下載完成、重新啟動安裝及升級後狀態清理均有真實裝置證據。
- OpenSpec `add-app-update-check` 任務 7.2 已完成勾選並記錄驗收資訊。
