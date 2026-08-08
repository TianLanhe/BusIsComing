## ADDED Requirements

### Requirement: 自動刷新使用全 App 共用間隔
系統 SHALL 以一個持久化偏好控制常用行程結果、臨時查詢結果及路線詳情的自動刷新，並 SHALL 對新安裝與由舊版本升級的使用者預設為每 1 分鐘。

#### Scenario: 首次讀取自動刷新設定
- **WHEN** 本機尚未保存自動刷新偏好或保存值無法識別
- **THEN** 系統 SHALL 使用每 1 分鐘作為目前間隔
- **AND** 常用結果、臨時結果及詳情 SHALL 讀取同一設定

#### Scenario: 選擇新的間隔
- **WHEN** 用戶選擇 1、2、5 或 10 分鐘
- **THEN** 系統 SHALL 立即持久化該值並通知目前可見頁面重新計算下一次到期時間
- **AND** 系統 SHALL NOT 要求額外儲存或重新啟動 App

#### Scenario: 關閉自動刷新
- **WHEN** 用戶選擇 `關閉`
- **THEN** 系統 SHALL 立即取消目前頁面的自動刷新 timer
- **AND** 系統 SHALL 保留首次查詢、手動查詢、手動下拉刷新及手動局部重試能力
- **AND** 系統 SHALL NOT 啟動背景排程替代前台 timer

### Requirement: 自動刷新只在有效結果及可見前台頁面運行
系統 SHALL 只在設定非關閉、目前頁面有有效查詢／詳情上下文、destination 可見且 App 位於前台時安排自動刷新。

#### Scenario: 首次查詢成功啟動結果排程
- **WHEN** 常用或臨時查詢首次成功顯示基礎結果，包括成功返回 0 條路線
- **THEN** 系統 SHALL 以該次成功時間建立結果頁下一次自動刷新基準
- **AND** 系統 SHALL 讓該結果上下文保持可自動刷新

#### Scenario: 首次查詢失敗
- **WHEN** 常用或臨時查詢首次因網絡、解析或其他錯誤失敗
- **THEN** 系統 SHALL NOT 為該失敗上下文安排自動重試
- **AND** 系統 SHALL NOT 因該失敗顯示首次自動刷新提示

#### Scenario: App 或 destination 不可見
- **WHEN** App 進入背景、裝置鎖屏、目前 destination 不再可見或結果頁進入路線詳情
- **THEN** 原頁面 SHALL 暫停自動刷新 timer
- **AND** 系統 SHALL NOT 在不可見期間發起該頁的自動網絡請求
- **AND** 進入詳情時只有詳情頁可成為自動刷新 owner

#### Scenario: 返回可見頁面時尚未到期
- **WHEN** 頁面恢復前台可見且目前時間早於按目前間隔計算的到期時間
- **THEN** 系統 SHALL 等待剩餘時間
- **AND** 系統 SHALL NOT 因 resume 立即刷新

#### Scenario: 返回可見頁面時已到期
- **WHEN** 頁面恢復前台可見且目前時間已達或超過到期時間
- **THEN** 系統 SHALL 立即發起最多一次自動刷新
- **AND** 系統 SHALL NOT 追趕不可見期間漏過的多個週期

#### Scenario: 編輯中的結果上下文
- **WHEN** 用戶正在編輯臨時查詢、展開地點候選、清空結果或目前結果上下文已失效
- **THEN** 系統 SHALL 暫停或取消原結果上下文的自動刷新
- **AND** 原上下文的 callback SHALL NOT 修改編輯中或新查詢的頁面

### Requirement: 排程以嘗試完成時間串行推進
系統 SHALL 在任何頁面同一時間最多運行一個刷新 cycle，並 SHALL 在目前嘗試完成後才安排下一次，而不是按固定牆鐘追趕週期。

#### Scenario: 自動刷新正在進行
- **WHEN** 目前自動刷新 cycle 尚未完成
- **THEN** 系統 SHALL NOT 發起第二個自動刷新、手動刷新或首次查詢
- **AND** 系統 SHALL 在目前 cycle 成功、失敗或取消後恢復可查詢狀態

#### Scenario: 初始或手動查詢正在進行
- **WHEN** 初始查詢或手動刷新正在進行
- **THEN** 自動刷新 SHALL 暫停且不得並行發起
- **AND** 成功完成後 SHALL 以新的最近成功時間重新計算到期時間

#### Scenario: 自動刷新成功後安排下一次
- **WHEN** 自動刷新成功且 cycle 已完成
- **THEN** 下一次到期 SHALL 不早於最近成功時間加目前間隔
- **AND** 下一次到期 SHALL 不早於本次嘗試完成時間加目前間隔

#### Scenario: 自動刷新失敗後冷卻
- **WHEN** 自動刷新失敗且 cycle 已完成
- **THEN** 系統 SHALL 保留最近成功時間
- **AND** 下一次嘗試 SHALL 至少等待自本次失敗完成起的一個完整目前間隔
- **AND** 系統 SHALL NOT 進行高頻 retry

#### Scenario: 刷新中改為關閉
- **WHEN** 自動網絡請求已發出而用戶把設定改為 `關閉`
- **THEN** 系統可讓底層請求完成但 SHALL 立即使該自動 generation 失效
- **AND** 該請求的 callback SHALL NOT 更新 UI、內容、最近成功時間或建立下一個 timer

#### Scenario: 刷新中離開或 generation 改變
- **WHEN** 頁面銷毀、語言切換、查詢上下文切換或新 generation 取代目前自動 cycle
- **THEN** 舊 callback SHALL 被取消或忽略
- **AND** 舊 cycle SHALL NOT 修改新頁面或新上下文的內容與排程

### Requirement: 常用與臨時結果重跑原查詢快照
系統 SHALL 讓結果自動刷新重跑最近一次成功查詢的原始上下文，並 SHALL 保持使用者資料、排序、置頂與漸進更新語義。

#### Scenario: 常用行程自動刷新
- **WHEN** 常用行程結果到達自動刷新時間
- **THEN** 系統 SHALL 使用該結果 owner 的原起點、終點及查詢身份重新查詢
- **AND** 系統 SHALL NOT 更新行程使用次數、最近使用時間或真實排序

#### Scenario: 臨時查詢自動刷新
- **WHEN** 臨時查詢結果到達自動刷新時間
- **THEN** 系統 SHALL 使用首次成功查詢時保存的起點、終點及精確座標快照重新查詢
- **AND** 即使任一端點來源是目前位置，系統 SHALL NOT 重新取得位置
- **AND** 系統 SHALL NOT 把該查詢保存為常用行程

#### Scenario: 自動刷新成功返回路線
- **WHEN** 自動刷新成功返回一條或多條基礎路線
- **THEN** 系統 SHALL 以回應完成時的目前排序字段與方向更新結果
- **AND** 系統 SHALL 重新套用既有置頂身份並更新最後成功時間
- **AND** 該結果上下文 SHALL 繼續安排下一次自動刷新

#### Scenario: 自動刷新成功返回空結果
- **WHEN** 自動刷新成功但返回 0 條可用路線
- **THEN** 系統 SHALL 把空結果視為成功並顯示既有無結果狀態
- **AND** 系統 SHALL 更新最後成功時間並繼續安排下一次自動刷新
- **AND** 後續成功 SHALL 能再次顯示恢復的路線結果

#### Scenario: 結果自動刷新失敗
- **WHEN** 結果自動刷新因網絡、解析或其他錯誤失敗
- **THEN** 系統 SHALL 保留最近成功結果、排序、查詢上下文及最後成功時間
- **AND** 系統 SHALL NOT 顯示自動刷新失敗 Toast、警告或 `暫時無法自動更新`
- **AND** 手動刷新既有失敗回饋 SHALL 保持不變

#### Scenario: 基礎路線決定結果 cycle 完成
- **WHEN** 自動查詢已返回基礎路線結果
- **THEN** 系統 SHALL 將結果自動刷新 cycle 視為完成
- **AND** 後續 ETA、站點預覽與 CSDI walking SHALL 仍可按目前 query generation、result id 及 segment id 漸進更新
- **AND** 系統 SHALL NOT 等待全部 ETA、預覽或 CSDI 才安排下一個間隔

#### Scenario: 新基礎結果更新 CSDI consumer
- **WHEN** 自動刷新接受一組新的基礎路線結果
- **THEN** 舊結果專屬 CSDI consumer SHALL 失效，仍有效成功 cache SHALL 可由新結果重用
- **AND** `AUTOMATIC` SHALL 只為不在 walking 失敗退避中的缺失 key 建立新 flight
- **AND** 舊 query generation 的 CSDI callback SHALL NOT 修改新列表

### Requirement: 詳情每個週期並發刷新動態詳情與首程 ETA
系統 SHALL 在路線詳情的每個自動刷新 cycle 並發刷新 Citybus 動態詳情與首程 ETA，讓兩個資料域獨立發布可靠成功內容，並在兩者都到達 terminal 狀態後才完成 cycle。

#### Scenario: 詳情自動刷新開始
- **WHEN** 可見前台詳情頁到達自動刷新時間
- **THEN** 系統 SHALL 同時發起 Citybus 詳情與首程 ETA 刷新
- **AND** 系統 SHALL 在刷新期間保留兩個資料域最近成功值
- **AND** 系統 SHALL NOT 因本 cycle 重新請求任何路線幾何

#### Scenario: Citybus 詳情回應通過驗證
- **WHEN** 完整 Citybus 詳情回應已解析且其 route identity、端點、乘車段及可靠結構與目前詳情一致
- **THEN** 系統 SHALL 只歸併該回應的動態預計時間、票價或等價動態值
- **AND** 系統 SHALL NOT 替換目前可靠站序、乘車段、walking、geometry、marker 或其他穩定結構

#### Scenario: Citybus 詳情結構不一致
- **WHEN** 新 Citybus 詳情回應無法完整解析、結構不可靠或與目前詳情 stable identity 不一致
- **THEN** 系統 SHALL 把此次動態詳情 domain 視為失敗並丟棄其動態值
- **AND** 系統 SHALL 保留目前全部可靠詳情內容與互動狀態

#### Scenario: 只有一個詳情資料域成功
- **WHEN** Citybus 動態詳情與首程 ETA 中只有一個成功
- **THEN** 系統 SHALL 立即發布成功 domain 的新值
- **AND** 另一 domain 的失敗 SHALL NOT 回滾該成功值或清除其最近成功內容
- **AND** 系統 SHALL 在兩個 domain 都成功、失敗或取消後才完成本 cycle

#### Scenario: 詳情自動刷新失敗
- **WHEN** 任一或全部詳情自動刷新 domain 失敗
- **THEN** 系統 SHALL NOT 顯示全頁錯誤、自動刷新失敗警告或成功動畫
- **AND** 系統 SHALL 保留各 domain 最近成功內容與最後成功時間
- **AND** 手動局部重試與既有初次載入失敗狀態 SHALL 保持可用

#### Scenario: 詳情更新保持互動狀態
- **WHEN** 動態詳情或 ETA 在自動 cycle 中更新
- **THEN** 系統 SHALL 保持地圖相機、bottom sheet detent、展開乘車段、選中 marker／timeline 及列表位置
- **AND** 系統 SHALL NOT 因動態值更新重建整頁或搶回使用者相機

#### Scenario: 詳情自動刷新不接管 walking domain
- **WHEN** 詳情 automatic cycle 開始、完成或失敗
- **THEN** 系統 SHALL 保持目前 walking generation、CSDI 成功／Loading／fallback、步行 paths 及相機內容
- **AND** 本 cycle SHALL NOT 建立 CSDI flight、清除摘要 pending target 或以新 Citybus 詳情替換 walking domain

### Requirement: 首次自動刷新提示全 App 只完成一次
系統 SHALL 在使用者尚未明確選擇刷新設定且首次成功顯示常用或臨時查詢結果時立即提供自動刷新說明，並 SHALL 以可恢復的持久化狀態確保完整提示只需完成一次。

#### Scenario: 首次成功顯示查詢結果
- **WHEN** notice 尚未完成且使用者尚未明確選擇刷新設定
- **AND** 常用或臨時查詢首次成功顯示基礎結果，包括 0 條路線
- **THEN** 系統 SHALL 立即在該結果頁顯示首次自動刷新橫幅
- **AND** 路線詳情頁 SHALL NOT 顯示該橫幅

#### Scenario: 橫幅自然完整展示
- **WHEN** 首次橫幅完成整個建議可見時長並自然消失
- **THEN** 系統 SHALL 持久化 notice 已完成
- **AND** 後續常用、臨時或詳情頁 SHALL NOT 再次顯示該首次橫幅

#### Scenario: 用戶點擊橫幅設定
- **WHEN** 用戶點擊首次橫幅的 `設定`
- **THEN** 系統 SHALL 立即把 notice 標記為已完成
- **AND** 系統 SHALL 打開設定 destination 並捲動及聚焦整個自動刷新標準設定行，而 SHALL NOT 自動打開單選對話框
- **AND** 原查詢上下文與結果 SHALL 保持可返回

#### Scenario: 用戶明確選擇任一刷新設定
- **WHEN** 用戶在設定頁選擇關閉、1、2、5 或 10 分鐘，包括重新選擇目前值
- **THEN** 系統 SHALL 把 notice 標記為已完成
- **AND** 後續成功查詢 SHALL NOT 顯示首次橫幅

#### Scenario: 橫幅完整展示前中斷
- **WHEN** 頁面離開、App 離開前台或 configuration change 在橫幅完成可見時長前發生
- **THEN** 系統 SHALL 取消舊頁橫幅而不把 notice 標記為完成
- **AND** 下次符合條件的成功查詢 SHALL 重新顯示完整橫幅

#### Scenario: 進程重啟與卸載
- **WHEN** notice 已完成後 App 進程重啟或版本升級
- **THEN** 系統 SHALL 保持 notice 已完成狀態
- **AND** 卸載後重新安裝可按新安裝重新開始該狀態
