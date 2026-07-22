## ADDED Requirements

### Requirement: 導航與搜尋優化在三語及深淺色保持相同層級
系統 SHALL 讓本 change 的底部導航、搜尋輸入器、欄位狀態、結果操作及排序控件在繁體、簡體、英文與淺色、深色模式下保持相同幾何、互動和資訊層級。

#### Scenario: 深淺色保持相同布局
- **WHEN** 用戶在淺色或深色模式查看底部導航、搜尋頁或首次空狀態
- **THEN** 控件尺寸、間距、圓角、展開位置、觸控區和焦點順序 SHALL 保持一致
- **AND** 系統 SHALL 只透過語意色切換表面、描邊、active indicator、選中內容及次要文字對比

#### Scenario: 三語文案使用資源並完整展示
- **WHEN** App 使用繁體、簡體或英文顯示新增或修改的導航、預覽、helper、錯誤及保存操作
- **THEN** 系統 SHALL 從對應 locale resource 取得自然文案
- **AND** 系統 SHALL NOT 在 XML 或 Kotlin 硬編碼 App 可見文案
- **AND** `Save as regular journey` 等長英文 SHALL NOT 與摘要、按鈕邊界或其他控件重疊

#### Scenario: 窄屏及大字體保持可操作
- **WHEN** 用戶在 `360dp` 寬度及 font scale `1.0／1.3／2.0` 查看受影響畫面
- **THEN** 核心文案 SHALL 透過換行、縱向重排或穩定增高保持完整可理解
- **AND** 系統 SHALL NOT 以縮小字體或裁切核心文字處理翻譯長度
- **AND** 圖示按鈕及主要操作 SHALL 保持不小於 `48dp` 的觸控區

## MODIFIED Requirements

### Requirement: 首次引導頁使用克制進入動效
系統 SHALL 在首次引導頁顯示時使用輕量、非阻塞的進入動效，並在系統動畫關閉時直接展示最終狀態。

#### Scenario: 首次引導內容依次進入
- **WHEN** 系統顯示首次引導頁
- **THEN** 主標題、路線結果預覽卡片和「新增常用行程」按鈕 SHALL 以淡入或輕微上移的方式依次出現
- **AND** 單段動畫時長 SHALL 維持在 150ms 到 250ms 範圍
- **AND** 動效 SHALL NOT 循環播放
- **AND** 動效 SHALL NOT 改變最終布局尺寸

#### Scenario: 動效不阻塞操作
- **WHEN** 首次引導頁進入動效正在執行
- **THEN** 用戶 SHALL 能在內容可見後點擊「新增常用行程」或「乘車碼」
- **AND** 頁面 SHALL NOT 顯示或等待頁內一次性查詢次按鈕
- **AND** 動效 SHALL NOT 阻塞底部搜尋導航、返回、旋轉、Activity 暫停或恢復

#### Scenario: 系統動畫關閉
- **WHEN** Android 系統動畫 scale 為 0 或等效設定表示動畫關閉
- **THEN** 首次引導頁 SHALL 直接顯示最終狀態
- **AND** 系統 SHALL NOT 依賴動畫完成回調才能讓按鈕可點擊
