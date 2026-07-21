## ADDED Requirements

### Requirement: 新互動元件跨語言與主題保持穩定
系統 SHALL 讓本 change 的站名預覽、搜尋輸入、排序摘要、導航選中態與乘車碼快捷入口在繁體中文、簡體中文、英文、淺色及深色模式使用同一資訊結構與語義色層級。

#### Scenario: 三語與深淺色使用相同結構
- **WHEN** 用戶切換 App 語言或外觀模式
- **THEN** 系統 SHALL 保持相同元件順序、觸控目標與功能可用性
- **AND** 所有 App 自有文案 SHALL 使用目前語言資源
- **AND** 系統 SHALL NOT 翻譯、縮寫或改寫第三方站名

#### Scenario: 窄屏與大字體保持核心操作可用
- **WHEN** App 在 360dp 寬度或 font scale 1.3 至 2.0 顯示相關頁面
- **THEN** 主要操作和至少 48dp 觸控目標 SHALL 保持可用
- **AND** 導航、輸入文字、按鈕與結果摘要 SHALL NOT 互相重疊
- **AND** 路線卡片站名僅可按規格尾部省略，完整名稱 SHALL 由詳情與無障礙描述提供

#### Scenario: 焦點和選中狀態具有足夠對比
- **WHEN** 搜尋輸入獲得焦點或導航、排序控制被選中
- **THEN** 淺色與深色主題 SHALL 使用對應的強調前景與容器語義色
- **AND** 狀態辨識 SHALL NOT 只依靠瞬時動畫
