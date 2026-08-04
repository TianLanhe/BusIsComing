# 通知欄監控設計

## 目的與邊界

通知欄監控是從一條具有完整首程 ETA 身份的查詢結果啟動的短時前台服務。它幫助使用者比較首班車到站時間與步行時間，提示準備出門、立即出門或可能遲到；它不是長期背景追蹤、鬧鐘或保證準時的導航服務。

只有具有首程公司、公開路線、route variant、方向、上下車 seq、P2P rawInfo 及語言的結果可啟動監控。服務刷新 DATA.GOV.HK ETA 所需的 stop id 仍由 P2P stop map 推導，不使用公開 route-stop 作 runtime fallback。

## 步行時間

使用者可選擇步行速度：

| 預設 | 速度 |
| --- | ---: |
| 慢速 | 3.2 km/h |
| 兒童 | 3.5 km/h |
| 正常 | 5.0 km/h |
| 快速 | 6.0 km/h |

場景修正：

- 下雨：有效速度乘以 0.8。
- 電梯：加 2 分鐘。
- 過馬路：加 2 分鐘。

距離分鐘數向上取整且至少 1 分鐘。最終基礎分鐘取 Citybus 步行距離換算、起點到上車站直線距離換算及使用者手動調整值三者的最大值，再加所有固定場景分鐘。使用較保守值是為了避免較短來源低估步行需要。

## 出門狀態

```text
剩餘等待 = 首班 ETA 分鐘 - 最終步行分鐘
```

| 條件 | 狀態 |
| --- | --- |
| 剩餘等待大於 2 分鐘 | 準備出門 `PREPARE` |
| 剩餘等待為 1 至 2 分鐘 | 立即出門 `LEAVE_NOW` |
| 剩餘等待小於等於 0 | 可能遲到 `LATE` |

語音只在狀態改變時嘗試播報，避免每次刷新重複朗讀相同狀態。通知仍顯示首班、下一班、步行、剩餘時間及最後成功更新時間；刷新失敗時保留上一次成功正文並清楚標示資料延遲及連續失敗次數。

## Session

建立 session 時保存：

- 路線展示名稱、步行分鐘和語音開關
- 完整 `FirstLegEtaQuery` 及語言
- 開始／到期時間
- 上次狀態、上次已播報狀態及上次成功通知
- 首班／第二班 ETA、停止時間及來源
- 連續失敗次數和中斷狀態

session 使用 `bus_monitor_session` SharedPreferences，使服務重建後可恢復。下列情況不恢復並清除：

- 已標記中斷
- 已到兩小時 session 上限
- 已到自動停止時間
- 資料損壞或缺少必要 query 身份

Activity recreation 不會停止服務。App 語言改變時，服務保留 session 和調度，停止舊 utterance、重建對應語言的 TTS controller，更新通知並以新語言執行後續請求。

## 刷新與調度

- 正常刷新間隔為 60 秒。
- 服務內 Handler 維持前台期間的下一次刷新；`AlarmManager` 提供退後台／idle 調度輔助。
- Android 6.0+ 在具備 exact alarm 能力時使用 `setExactAndAllowWhileIdle`，否則使用 `setAndAllowWhileIdle`；舊版本使用 `setExact`。
- session 期間持有受控 `PARTIAL_WAKE_LOCK`，停止時必須釋放。
- 成功刷新把連續失敗歸零並保存新 snapshot。
- 連續失敗達 10 次時停止監控，避免無限背景重試。
- 整個 session 最長兩小時，即使 ETA 一直不可用也不能無限運行。

精確鬧鐘不可用屬調度精度降級，不改用不合適的長期後台機制，也不要求使用者關閉電池最佳化。

## 自動停止

首次取得 ETA 後確定停止目標：

1. 有第二班 ETA 時，以第二班 ETA 作停止時間。
2. 只有第一班時，以第一班 ETA 加 2 分鐘作 fallback。
3. 已確定的停止目標在同一 session 內保持，後續刷新不反覆向後延長。

到達停止時間、使用者按停止、session 過期或失敗達上限時，取消刷新和停止 alarm、釋放 WakeLock／TTS、清除 session 並結束前台服務。

## 通知與操作

監控使用狀態與警示 notification channel，主通知是持續前台通知。操作包括：

- 打開 App
- 立即刷新
- 開啟乘車碼
- 停止監控

鎖屏 public notification 使用同樣的可理解正文，不暴露比主通知更多資料。通知權限不可用、channel 不可建立或系統拒絕前台通知時，不得假裝監控仍可靠運作。

## TTS

TTS 語言必須與目前 App 實際語言同一語言家族：

- 繁體接受粵語、香港／澳門／台灣中文或明確 Hant voice。
- 簡體接受普通話或明確 Hans voice。
- 英文接受 `en`，優先 HK／GB。
- 模糊 `zh` 及繁簡交叉 fallback 不可用。

controller 覆蓋無 engine、初始化失敗／8 秒逾時、缺少資料、不支援 locale、沒有相容 voice、audio focus、speak rejected、4 秒播放啟動逾時、播放錯誤及 release。TTS 失敗不停止 ETA 刷新與通知。

每一種具體失敗原因在同一 monitor session 最多顯示一次 Toast；不同原因可各提示一次。提示須說明監控會繼續但不播報。日誌不得記錄 API key、完整自訂行程名或 utterance 內容。

## 失敗與恢復

- ETA／stop map 失敗保留上次成功通知，不把舊資料標成最新。
- 手動刷新沿用同一 session，不重置兩小時上限或停止目標。
- process／service 重建只恢復仍有效且未中斷的 snapshot。
- 語言切換、TTS 失敗、exact alarm 不可用都不應改變路線身份或使用另一語言資料。
- 使用者始終能由通知明確停止；停止後不可留下 refresh／auto-stop PendingIntent、WakeLock 或 TTS。

## 驗證重點

- 純邏輯：速度、修正、向上取整、狀態門檻、語音狀態變更、停止目標和 session codec。
- service：成功／失敗刷新、10 次上限、兩小時到期、恢復、語言變更、通知正文及操作。
- scheduling：exact 能力、inexact fallback、API 版本及 PendingIntent。
- TTS：三個語言家族、voice 排序、所有失敗原因、每原因一次 Toast、focus／timeout／release。
- 裝置：通知權限、鎖屏、背景／idle、手動停止、語言切換及目標 API 對應的合適模擬器／實機。

模擬器使用必須遵循 AGENTS：只使用本任務自行啟動且符合驗證畫像的設備；合適設備被佔用時等待，不以不合適設備降級替代。
