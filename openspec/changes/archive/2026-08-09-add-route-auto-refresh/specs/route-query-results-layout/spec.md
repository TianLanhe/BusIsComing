## ADDED Requirements

### Requirement: 首次自動刷新橫幅遵循固定結果頁 UI 合同
系統 SHALL 在 `route-auto-refresh` 判定需顯示首次說明時，以結果頁 AppBar 正常排版空間內的專用橫幅呈現，並 SHALL 嚴格保持已確認的結構、視覺、時長與無障礙行為。

#### Scenario: 常用結果頁插入橫幅
- **WHEN** 常用行程結果頁顯示首次自動刷新橫幅
- **THEN** 橫幅 SHALL 位於目前查詢／行程控制之後及吸頂排序與結果摘要之前
- **AND** 橫幅 SHALL 佔用正常 layout 空間而不覆蓋路線卡或結果控制

#### Scenario: 臨時結果頁插入橫幅
- **WHEN** 臨時查詢結果頁顯示首次自動刷新橫幅
- **THEN** 橫幅 SHALL 位於完整搜尋內容或折疊 `本次行程` 上下文之後及共用排序與結果摘要之前
- **AND** 顯示期間頁面 SHALL 保持可捲動、可點擊且不被 dim

#### Scenario: 橫幅視覺結構
- **WHEN** 首次自動刷新橫幅完全顯示
- **THEN** 橫幅 SHALL 使用淺綠語義表面、綠色 1dp 描邊、14dp 圓角與克制陰影
- **AND** 橫幅底部 SHALL 顯示高度 3dp、由滿至空遞減的倒數線
- **AND** 橫幅 SHALL NOT 顯示左側圖示、左上圖示、關閉按鈕、遮罩或額外說明控件
- **AND** 系統 SHALL NOT 以 Snackbar、Toast、Dialog 或 Bottom Sheet 代替該橫幅

#### Scenario: 橫幅短文案與操作
- **WHEN** 自動刷新間隔為 N 分鐘且橫幅顯示
- **THEN** 左側 SHALL 只顯示兩行等價自然文案 `自動刷新已開啟` 與 `每 N 分鐘更新`
- **AND** 右側 SHALL 只顯示 `設定` 文字 action
- **AND** `設定` SHALL 提供至少 48dp 的有效觸控區
- **AND** 文案 SHALL 使用目前 App 的香港繁體、獨立簡體或自然英文資源

#### Scenario: 橫幅展示時長與動效
- **WHEN** 系統顯示首次自動刷新橫幅
- **THEN** 橫幅 SHALL 以約 200ms slide 加 fade 進場
- **AND** 橫幅 SHALL 在完全可見後保持至少 5 秒
- **AND** 橫幅 SHALL 以約 200ms slide 加 fade 退場並自動移除
- **AND** 系統建議更長無障礙 timeout 時 SHALL 延長完全可見時長

#### Scenario: 系統停用動畫
- **WHEN** 系統動畫已停用
- **THEN** 橫幅 SHALL 立即切換進場與退場狀態
- **AND** 橫幅 SHALL 仍保持完整建議可見時長

#### Scenario: TalkBack 宣告橫幅
- **WHEN** 橫幅首次出現在目前頁面
- **THEN** 系統 SHALL 以 polite live region 宣告其兩行說明一次
- **AND** 系統 SHALL NOT 自動搶走目前無障礙焦點
- **AND** `設定` action SHALL 可獨立聚焦及啟動

#### Scenario: 窄屏或大型字體橫幅
- **WHEN** 360dp 級別可用寬度或字體比例 1.3／2.0 無法自然容納兩行文案與右側 action
- **THEN** 橫幅 SHALL 讓文字自然換行並把 `設定` 移至獨立 trailing action row 或等價 reflow
- **AND** 系統 SHALL NOT 縮小字體、裁切、重疊或把 action 移出可見範圍

### Requirement: 結果自動刷新使用安靜摘要回饋並保持閱讀位置
系統 SHALL 將自動刷新與既有手動下拉刷新視為不同 UI trigger，在自動週期中只提供輕量進行中狀態，並 SHALL 在結果更新後保持使用者目前閱讀與選擇。

#### Scenario: 結果自動刷新進行中
- **WHEN** 常用或臨時結果的自動刷新正在進行
- **THEN** 結果摘要 SHALL 以小型 progress 狀態及目前語言的 `正在更新` 暫時取代更新時間
- **AND** 系統 SHALL 保留原結果卡、排序與查詢上下文可讀及可操作
- **AND** 系統 SHALL NOT 顯示手動下拉刷新固定浮層或成功勾號

#### Scenario: 結果自動刷新成功
- **WHEN** 結果自動刷新成功完成
- **THEN** 摘要 SHALL 靜默顯示新的最後成功時間
- **AND** 系統 SHALL NOT 顯示成功動畫、成功 Toast 或把列表滾動到頂部

#### Scenario: 結果自動刷新失敗
- **WHEN** 結果自動刷新失敗
- **THEN** 摘要 SHALL 恢復顯示刷新前的最後成功時間
- **AND** 系統 SHALL NOT 顯示失敗警告、Toast 或 `暫時無法自動更新`

#### Scenario: 自動刷新後路線仍存在
- **WHEN** 自動刷新成功前記錄的第一張可見路線 stable id 仍存在於新排序結果
- **THEN** 系統 SHALL 恢復該路線相對列表頂部的 pixel offset
- **AND** 系統 SHALL NOT 主動把列表移至頂部

#### Scenario: 自動刷新後錨點路線消失
- **WHEN** 刷新前第一張可見路線在新結果中已消失
- **THEN** 系統 SHALL 以新排序中最接近的下一張路線作為視口錨點
- **AND** 系統 SHALL 避免無理由跳到列表頂部

#### Scenario: 自動刷新期間已開啟路線互動
- **WHEN** 用戶已打開某路線 ETA、選中路線或進入詳情，而對應結果自動刷新完成
- **THEN** 系統 SHALL 保持原 stable identity 的選擇或已開啟內容
- **AND** 系統 SHALL NOT 主動關閉、切換或重置該互動

#### Scenario: 漸進 CSDI 重排共用視口錨點
- **WHEN** 自動刷新基礎列表提交後，CSDI walking 更新令目前步行排序再次改變
- **THEN** 系統 SHALL 使用與自動刷新相同的 stable-id＋pixel-offset 錨點政策提交新 projection
- **AND** ETA、站點預覽、CSDI 與基礎結果 SHALL NOT 各自重複排序或無理由把列表移至頂部

#### Scenario: 手動刷新保持既有回饋
- **WHEN** 用戶明確觸發下拉刷新
- **THEN** 系統 SHALL 繼續使用既有固定進行中浮層、成功勾號、失敗提示及手動刷新滾動語義
- **AND** 自動刷新 UI SHALL NOT 取代或削弱該手動回饋
