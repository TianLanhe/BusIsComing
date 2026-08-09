# route-detail-progressive-loading Specification

## Purpose
TBD - created by archiving change fix-route-detail-progressive-loading. Update Purpose after archive.
## Requirements
### Requirement: 路線詳情資料域並發載入
系統 SHALL 在路線詳情頁同時啟動可用的 Google Map、Citybus 詳情、各乘車段幾何與首程 ETA 工作，並 SHALL 按每個資料域的真實完成時機展示可靠內容。

#### Scenario: 進入頁面同時啟動資料域
- **WHEN** 用戶開啟一條具有詳情、幾何及 ETA 元數據的路線
- **THEN** 系統 SHALL 在不互相等待的情況下啟動 Map、Citybus 詳情、各乘車段幾何與首程 ETA
- **AND** 頁面 SHALL 立即展示啟動參數中已有的可靠摘要

#### Scenario: 資料域以不同順序完成
- **WHEN** Map、Citybus 詳情、ETA 或任一乘車段幾何先於其他資料域完成
- **THEN** 頁面 SHALL 立即增加該資料域已驗證的內容
- **AND** 頁面 SHALL NOT 等待全部資料域完成後才集中展示

#### Scenario: 單一回應原子發布
- **WHEN** Citybus 單一詳情回應完成解析及完整性驗證
- **THEN** 系統 SHALL 同時發布該回應中已確認的結構化內容
- **AND** 系統 SHALL NOT 為製造漸進效果而把同一完整回應按固定延時串行展示

### Requirement: 並發結果按身份及品質單調歸併
系統 SHALL 以目前頁面、資料域 generation 及 stable key 驗證每個異步結果，並 SHALL 保證已發布的可靠成功內容不被過期或較差結果降級。

#### Scenario: 過期 generation 晚到
- **WHEN** 舊頁面、舊語言或舊重試 generation 的 callback 在較新結果後到達
- **THEN** 系統 SHALL 忽略該 callback
- **AND** 該 callback SHALL NOT 修改目前頁面的內容、錯誤或載入狀態

#### Scenario: 舊失敗晚於成功到達
- **WHEN** 某資料域已發布目前 generation 的可靠成功內容
- **AND** 較舊 Loading、Error、candidate 或較差 cache 結果稍後到達
- **THEN** 系統 SHALL 保留既有成功內容
- **AND** 頁面 SHALL NOT 回退至載入、錯誤、空白或較少內容

#### Scenario: 快取結構先於動態詳情完成
- **WHEN** 頁面先從可靠快取取得站點結構，其後新鮮詳情請求成功或失敗
- **THEN** 頁面 SHALL 先展示可靠站點與乘坐站數
- **AND** 新鮮成功 SHALL 只補充或更新本次動態資料
- **AND** 新鮮失敗 SHALL NOT 清除已展示的可靠結構

#### Scenario: 不同 geometry key 獨立歸併
- **WHEN** 多個乘車段幾何以不同順序成功、失敗或重試
- **THEN** 每個 geometry key SHALL 只更新自己的狀態與路線線條
- **AND** 任一 key 的事件 SHALL NOT 清除其他 key 的可靠成功幾何

### Requirement: 局部失敗與重試保留其他成功內容
系統 SHALL 讓 Map、詳情結構、動態詳情、每個幾何分段與 ETA 分別表示載入、成功、刷新及錯誤，且 SHALL 只重試失敗或過期的資料域。

#### Scenario: 單一資料域最終失敗
- **WHEN** Map、Citybus 詳情、某段幾何或 ETA 中只有一個資料域最終失敗
- **THEN** 頁面 SHALL 在對應區域展示失敗或不可用狀態
- **AND** 頁面 SHALL 保留其他資料域全部已成功內容與返回操作

#### Scenario: 用戶重試局部失敗
- **WHEN** 用戶對某個失敗資料域或幾何分段執行重試
- **THEN** 系統 SHALL 只建立該資料域或 stable key 的新 generation
- **AND** 系統 SHALL NOT 重新載入、清空或降級仍有效的其他成功資料域

#### Scenario: 刷新期間保留最近成功值
- **WHEN** ETA 或動態詳情已有成功值並開始刷新
- **THEN** 頁面 SHALL 在刷新中保留最近成功內容
- **AND** 刷新失敗時系統 SHALL 顯示對應刷新狀態而不把最近成功內容立即清空

### Requirement: 漸進更新保持互動與生命週期狀態
系統 SHALL 以 stable id 增量更新摘要、時間線、marker 與 polyline，並 SHALL 在其他資料域更新時保持與該更新無關的使用者互動狀態。

#### Scenario: 新資料域內容加入
- **WHEN** 一個新資料域的可靠內容加入已顯示頁面
- **THEN** 系統 SHALL 保留未受影響乘車段的展開狀態、選中站點、列表位置與既有地圖 overlay
- **AND** 系統 SHALL NOT 因整頁重建而令可靠內容或互動狀態短暫消失

#### Scenario: 頁面銷毀後 callback 到達
- **WHEN** 用戶已離開詳情頁或 Activity 已銷毀
- **AND** 先前的任一異步 callback 隨後到達
- **THEN** 系統 SHALL 停止向已銷毀頁面派送或忽略該 callback
- **AND** 共享工作仍有其他有效 consumer 時 SHALL NOT 因本頁離開而中斷其他 consumer

#### Scenario: configuration change 後恢復頁面
- **WHEN** 詳情頁因 configuration change 重建
- **THEN** 系統 SHALL 恢復可序列化的底部面板、選中站點、展開分段、列表位置及相機狀態
- **AND** 重建前 callback SHALL NOT 覆蓋重建後的新頁面 generation
