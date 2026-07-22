# notification-bar-monitoring Specification

## Purpose
TBD - created by archiving change add-notification-bar-monitoring. Update Purpose after archive.
## Requirements

### Requirement: 啟動通知欄監控

系統 SHALL 允許用戶從一條可展示首程 ETA 的路線結果啟動短時通知欄監控。

#### Scenario: 從結果卡片啟動監控設定
- **WHEN** 用戶點擊某條路線結果卡片的小鈴鐺入口
- **THEN** 系統 SHALL 打開通知欄監控啟動底部面板
- **AND** 面板 SHALL 展示該路線的路線號、所屬常用路線名稱或當前查詢上下文

#### Scenario: 沒有可用 ETA 時阻止啟動
- **WHEN** 用戶嘗試為候車時間仍在查詢中或暫無車輛的路線啟動監控
- **THEN** 系統 SHALL 不啟動前台監控服務
- **AND** 系統 SHALL 提示用戶等待候車時間更新或重新查詢

#### Scenario: 通知權限缺失
- **WHEN** 用戶點擊開始監控且系統缺少發送通知所需權限
- **THEN** 系統 SHALL 引導用戶授權通知
- **AND** 系統 SHALL NOT 啟動無常駐通知的後台監控

### Requirement: 設定步行到站時間

系統 SHALL 在啟動監控前提供 `步行到站` 分鐘數，並以接口距離估算、經緯度直線距離估算和用戶調整時間中的較長者作為監控使用值。

#### Scenario: 展示默認步行到站時間
- **WHEN** 系統打開通知欄監控啟動面板
- **THEN** 面板 SHALL 展示 `步行到站` 分鐘數
- **AND** 該分鐘數 SHALL 等於接口距離估算時間、經緯度直線距離估算時間和用戶調整時間中的最大值

#### Scenario: 接口距離估算
- **WHEN** 系統能從 Citybus 路線詳情取得起點到首程上車站的步行距離米數
- **THEN** 系統 SHALL 使用當前步行速度和該距離計算接口距離估算時間

#### Scenario: 經緯度直線距離估算
- **WHEN** 系統具有用戶起點坐標和首程上車站坐標
- **THEN** 系統 SHALL 使用兩點經緯度直線距離和當前步行速度計算直線距離估算時間
- **AND** 系統 SHALL NOT 對該直線距離額外乘繞路係數

#### Scenario: 用戶手動調整步行到站
- **WHEN** 用戶在啟動面板點擊 `-` 或 `+` 調整步行到站分鐘數
- **THEN** 系統 SHALL 更新用戶調整時間
- **AND** 面板 SHALL 重新展示三種時間中的最大值

### Requirement: 提供步速預設與場景修正

系統 SHALL 在監控啟動面板提供互斥步行速度預設和可多選場景修正，用於快速調整步行到站時間。

#### Scenario: 步行速度互斥選擇
- **WHEN** 用戶選擇 `慢行 3.2`、`帶小孩 3.5`、`一般 5.0` 或 `快走 6.0` 任一步行速度預設
- **THEN** 系統 SHALL 取消此前選中的其他步行速度預設
- **AND** 系統 SHALL 使用新速度重新計算接口距離估算時間和直線距離估算時間

#### Scenario: 雨天修正速度
- **WHEN** 用戶選中 `雨天 ×80%`
- **THEN** 系統 SHALL 將當前步行速度乘以 `80%` 後重新計算距離估算時間
- **AND** 系統 SHALL NOT 修改接口距離或直線距離本身

#### Scenario: 固定分鐘場景修正
- **WHEN** 用戶選中 `等電梯 +2` 或 `天橋/過馬路 +2`
- **THEN** 系統 SHALL 將對應分鐘加到用戶調整時間中
- **AND** 系統 SHALL 允許這些場景修正同時生效

#### Scenario: 取消場景修正
- **WHEN** 用戶取消已選中的場景修正
- **THEN** 系統 SHALL 移除該場景修正對步行到站時間的影響
- **AND** 面板 SHALL 重新展示三種時間中的最大值

### Requirement: 通知欄持續展示監控狀態
系統 SHALL 在用戶開始監控後啟動短時前台服務，並透過目前 App 語言的常駐通知展示候車監控狀態。

#### Scenario: 開始監控
- **WHEN** 用戶在啟動面板點擊目前語言的開始監控操作
- **THEN** 系統 SHALL 啟動前台服務
- **AND** 系統 SHALL 顯示 BusIsComing 常駐通知
- **AND** notification channel 名稱與說明 SHALL 使用目前 App 語言

#### Scenario: 通知展示內容
- **WHEN** 監控服務已有最近一次可用 ETA
- **THEN** 通知 SHALL 以目前 App 語言展示監控狀態、路線號、剩餘候車分鐘、步行到站分鐘和最後更新時間
- **AND** 路線號及用戶保存名稱 SHALL 保持原文

#### Scenario: 通知操作
- **WHEN** 監控通知展示時
- **THEN** 通知 SHALL 以目前 App 語言提供刷新、停止和打開 App 操作

#### Scenario: 每分鐘嘗試更新
- **WHEN** 監控服務處於運行狀態
- **THEN** 系統 SHALL 每分鐘嘗試刷新首程 ETA
- **AND** 系統 SHALL 使用目前語言的「嘗試更新」語義處理系統省電或網絡限制導致的延遲

#### Scenario: 手動刷新
- **WHEN** 用戶點擊通知中的刷新操作
- **THEN** 系統 SHALL 立即嘗試刷新首程 ETA
- **AND** 通知 SHALL 在刷新完成後以目前語言更新候車狀態或展示資料延遲

### Requirement: 判斷出門狀態

系統 SHALL 根據第 1 班 ETA 分鐘數和步行到站分鐘數計算剩餘可等待時間，並映射為三種出門狀態。

#### Scenario: 準備出門狀態
- **WHEN** `第 1 班 ETA 分鐘數 - 步行到站分鐘數` 大於 `2`
- **THEN** 監控狀態 SHALL 為 `準備出門`

#### Scenario: 立即出門狀態
- **WHEN** `第 1 班 ETA 分鐘數 - 步行到站分鐘數` 介於 `1` 到 `2` 分鐘之間
- **THEN** 監控狀態 SHALL 為 `立即出門`

#### Scenario: 快遲到了狀態
- **WHEN** `第 1 班 ETA 分鐘數 - 步行到站分鐘數` 小於或等於 `0`
- **THEN** 監控狀態 SHALL 為 `快遲到了`

#### Scenario: ETA 暫不可用
- **WHEN** 監控服務無法取得可用第 1 班 ETA
- **THEN** 系統 SHALL 保留最後一次成功 ETA 或展示資料延遲狀態
- **AND** 系統 SHALL 允許用戶手動刷新或停止監控

### Requirement: 狀態切換語音播報
系統 SHALL 在語音提醒開啟時，於監控狀態切換時使用目前 App 語言的 TextToSpeech 文案播報相同狀態語義。

#### Scenario: 默認開啟語音提醒
- **WHEN** 系統打開通知欄監控啟動面板
- **THEN** 目前語言的語音提醒開關 SHALL 默認為開啟

#### Scenario: 關閉語音提醒
- **WHEN** 用戶關閉語音提醒
- **THEN** 系統 SHALL 在本次監控 session 中不播放狀態切換語音
- **AND** 通知欄狀態更新 SHALL 不受影響

#### Scenario: 播報準備出門
- **WHEN** 監控狀態切換為準備出門語義
- **AND** 語音提醒已開啟
- **THEN** 系統 SHALL 以目前 App 語言播報剩餘到站分鐘及做好出門準備的語義

#### Scenario: 播報立即出門
- **WHEN** 監控狀態切換為立即出門語義
- **AND** 語音提醒已開啟
- **THEN** 系統 SHALL 以目前 App 語言播報剩餘到站分鐘及立即出門的語義

#### Scenario: 播報快遲到了
- **WHEN** 監控狀態切換為快遲到了語義
- **AND** 語音提醒已開啟
- **THEN** 系統 SHALL 以目前 App 語言播報剩餘到站分鐘及可能遲到的語義

#### Scenario: 同一狀態不重複播報
- **WHEN** ETA 刷新後監控狀態與上一次已播報狀態相同
- **THEN** 系統 SHALL NOT 重複播報同一狀態文案

### Requirement: 自動停止監控

系統 SHALL 在用戶停止、第二班車過站或 fallback 停止條件達成時停止通知欄監控。

#### Scenario: 用戶手動停止
- **WHEN** 用戶點擊通知中的 `停止`
- **THEN** 系統 SHALL 停止前台服務
- **AND** 系統 SHALL 移除監控通知

#### Scenario: 第 2 班車過站後停止
- **WHEN** 監控服務取得第 2 班 ETA
- **AND** 第 2 班 ETA 已過站
- **THEN** 系統 SHALL 自動停止本次監控

#### Scenario: 缺少第 2 班 ETA 時 fallback 停止
- **WHEN** 監控服務無法取得第 2 班 ETA
- **AND** 第 1 班 ETA 已過站超過 `2 分鐘`
- **THEN** 系統 SHALL 自動停止本次監控

#### Scenario: 連續刷新失敗保護
- **WHEN** 監控服務連續刷新失敗且達到實作定義的保護上限
- **THEN** 系統 SHALL 停止監控或展示資料延遲並允許用戶手動處理

### Requirement: 啟動面板展示鎖屏與語音提示
系統 SHALL 在通知欄監控啟動面板展示自然提示，讓用戶在開始監控前理解鎖屏通知內容與語音播報行為。

#### Scenario: 顯示鎖屏通知提示
- **WHEN** 系統打開通知欄監控啟動面板
- **THEN** 面板 SHALL 提示鎖屏會顯示路線與 ETA
- **AND** 該提示 SHALL 與既有「每分鐘嘗試更新」和系統限制提示保持同一視覺層級

#### Scenario: 顯示鎖屏語音提示
- **WHEN** 系統打開通知欄監控啟動面板
- **AND** 面板展示 `語音播報` 開關
- **THEN** 面板 SHALL 提示開啟語音後，監控狀態變更可能在鎖屏時播報
- **AND** 系統 SHALL 保持 `語音播報` 開關由用戶控制

#### Scenario: 提示不阻塞監控啟動
- **WHEN** 用戶查看通知欄監控啟動面板
- **THEN** 系統 SHALL NOT 要求額外確認或勾選才能開始監控
- **AND** `開始監控` 操作、步行時間設定、步速預設和場景修正 SHALL 保持可用

### Requirement: 活動監控立即跟隨語言切換
系統 SHALL 在不停止 monitor session 的前提下令活動通知及後續 TTS 跟隨新的 App 語言。

#### Scenario: 監控期間切換語言
- **WHEN** 前台監控正在運作且用戶切換 App 語言
- **THEN** 系統 SHALL 保留 monitor session、路線、步行設定與刷新週期
- **AND** 系統 SHALL 使用相同 channel id 重新建立本地化 channel metadata
- **AND** 系統 SHALL 立即以新語言更新活動通知

#### Scenario: 切換時正在播放舊語言
- **WHEN** 語言切換時舊語言 utterance 尚未完成
- **THEN** 系統 SHALL 停止該 utterance
- **AND** 後續狀態播報 SHALL 使用新語言及相容 Voice

#### Scenario: 語言切換後 ETA 失敗
- **WHEN** 監控已切換新語言但下一次 ETA 刷新失敗
- **THEN** 系統 SHALL 以新語言保留最後成功資料或展示資料延遲
- **AND** 系統 SHALL NOT 恢復舊語言通知文案

### Requirement: 候車監控通知提供乘車碼情境操作
系統 SHALL 在候車監控通知中以「重新整理／乘車碼／停止」順序提供三個操作，並讓乘車碼操作復用正式支付 provider 回退鏈而不改變監控狀態。

#### Scenario: 監控通知顯示第三個操作
- **WHEN** 候車監控前台服務正在運行
- **THEN** 通知 SHALL 同時顯示重新整理、乘車碼及停止 action
- **AND** 乘車碼 action SHALL 使用目前 App 語言的短標籤

#### Scenario: 從通知開啟乘車碼
- **WHEN** 用戶點擊通知中的乘車碼 action
- **THEN** 系統 SHALL 進入與其他正式入口相同的乘車碼候選鏈
- **AND** 目前監控 session SHALL 繼續運行
- **AND** 系統 SHALL NOT 把該點擊當作重新整理或停止操作

#### Scenario: 通知乘車碼啟動失敗
- **WHEN** 所有乘車碼候選都無法由 Android 啟動
- **THEN** 系統 SHALL 顯示既有本地化失敗提示
- **AND** 候車監控 SHALL 保持運行並可繼續重新整理或停止
