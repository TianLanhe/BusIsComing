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
