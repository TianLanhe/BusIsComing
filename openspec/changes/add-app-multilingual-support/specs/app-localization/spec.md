## ADDED Requirements

### Requirement: App 提供跟隨系統與三種明確語言
系統 SHALL 支援跟隨系統、繁體中文、簡體中文及英文，並使用單一語言策略解析所有 App component 的實際語言。

#### Scenario: 新安裝或升級後預設跟隨系統
- **WHEN** 用戶首次啟動包含本能力的版本
- **AND** App 尚未保存明確語言選擇
- **THEN** 系統 SHALL 使用跟隨系統模式
- **AND** 系統 SHALL NOT 因用戶由舊版本升級而強制鎖定繁體中文

#### Scenario: 系統語言解析為支援語言
- **WHEN** App 處於跟隨系統模式
- **THEN** 英文的任何地區 SHALL 解析為英文
- **AND** `zh-Hant` 或香港、澳門、台灣中文 SHALL 解析為繁體中文
- **AND** `zh-Hans` 或中國大陸、新加坡中文 SHALL 解析為簡體中文

#### Scenario: 系統語言不受支援或中文文字體系不明
- **WHEN** App 處於跟隨系統模式
- **AND** 系統語言不是受支援語言或只有無法判定文字體系的裸 `zh`
- **THEN** 系統 SHALL 使用繁體中文

#### Scenario: 明確語言持久生效
- **WHEN** 用戶選擇繁體中文、簡體中文或英文
- **THEN** 系統 SHALL 立即套用該語言
- **AND** 系統 SHALL 在後續啟動中持續使用該選擇
- **AND** 系統 SHALL 直到用戶改選其他語言或跟隨系統才改變該選擇

#### Scenario: 回到跟隨系統
- **WHEN** 用戶選擇跟隨系統
- **THEN** 系統 SHALL 清除明確 App locale
- **AND** 系統 SHALL 立即按目前系統語言重新解析實際語言

### Requirement: App 自有內容完整提供三語資源
系統 SHALL 讓所有 App 自有用戶可見文字、語音及無障礙內容在繁體中文、簡體中文和英文中具有語義等價且自然的版本。

#### Scenario: App 自有內容跟隨目前語言
- **WHEN** 系統展示底部導航、Activity、Fragment、Bottom Sheet、Dialog、Toast、空狀態、錯誤、通知、分享內容、意見回饋或無障礙描述
- **THEN** 所有 App 自有文字 SHALL 使用目前實際語言
- **AND** 系統 SHALL NOT 在同一狀態中混合不同語言的 App 自有文案

#### Scenario: 三語資源完整
- **WHEN** 系統構建 App
- **THEN** 繁體、簡體及英文資源 SHALL 具有相同的可翻譯 key
- **AND** 相同 key 的 placeholder 類型與 plural 結構 SHALL 一致
- **AND** 系統 SHALL 只允許品牌、email、版本、路線號等真正語言無關內容標記為不可翻譯

#### Scenario: 翻譯符合各語言表達習慣
- **WHEN** App 新增或修改用戶文案
- **THEN** 繁體中文 SHALL 使用自然的香港實用書面語
- **AND** 簡體中文 SHALL 獨立撰寫而非只作繁簡字形轉換
- **AND** 英文 SHALL 使用自然、簡潔的產品語言而非逐字直譯

#### Scenario: 系統及第三方畫面不受 App 強制控制
- **WHEN** App 打開 Android 權限對話框、系統文件選擇器、分享面板、TTS engine 畫面、支付工具或外部瀏覽器
- **THEN** 系統 SHALL 允許該畫面使用系統或第三方自身語言
- **AND** App SHALL 只保證進入及返回流程可繼續

### Requirement: 語言切換保持操作上下文並拒絕舊語言結果
系統 SHALL 在語言切換時保留用戶操作上下文、作廢舊語言非同步工作，並只展示新語言結果。

#### Scenario: 保留 Activity 狀態
- **WHEN** 語言切換觸發 Activity 重建
- **THEN** 系統 SHALL 保留目前選中的常用／搜尋／設定 destination、常用路線 id、搜尋起終點、各 destination 的排序與滾動位置及尚未提交的表單輸入
- **AND** 系統 SHALL NOT 將舊語言查詢結果作為恢復狀態重新展示

#### Scenario: 舊語言結果晚到
- **WHEN** 語言切換前發出的地點、路線、詳情、ETA 或地址請求在切換後返回
- **THEN** 系統 SHALL 依語言版本拒絕該結果更新 UI 或語言相關 cache

#### Scenario: 語言切換自動重查
- **WHEN** 常用或搜尋 destination 已有曾發起有效查詢的起終點上下文
- **AND** 用戶切換語言
- **THEN** 系統 SHALL 清除舊語言候選、路線、詳情及地址狀態
- **AND** 每個具有有效查詢上下文的 destination SHALL 使用新語言及原座標自動重查
- **AND** 該自動重查 SHALL NOT 增加常用路線使用次數

#### Scenario: 未提交的搜尋表單不自動查詢
- **WHEN** 搜尋 destination 只有尚未提交的輸入或尚未形成有效起終點
- **AND** 用戶切換語言
- **THEN** 系統 SHALL 保留可安全恢復的輸入
- **AND** 系統 SHALL NOT 自動發起 Citybus 路線查詢

#### Scenario: 新語言重查失敗
- **WHEN** 語言切換後的自動重查失敗
- **THEN** 系統 SHALL 使用新語言顯示失敗狀態
- **AND** 系統 SHALL NOT 回退展示舊語言結果

### Requirement: 動態資料使用統一語言映射
系統 SHALL 讓 Citybus、DATA.GOV.HK、Google、官方網站路徑、通知及 TTS 使用同一實際語言映射。

#### Scenario: 繁體中文 provider 映射
- **WHEN** 實際語言為繁體中文
- **THEN** Citybus SHALL 使用 `l=0`
- **AND** Google SHALL 使用 `languageCode=zh-Hant` 與 `regionCode=HK`
- **AND** DATA.GOV.HK SHALL 優先使用 `*_tc` 欄位
- **AND** 官方網站首頁與私隱政策 SHALL 分別使用 `/zh-hant/` 與 `/zh-hant/privacy/`

#### Scenario: 簡體中文 provider 映射
- **WHEN** 實際語言為簡體中文
- **THEN** Citybus SHALL 使用 `l=2`
- **AND** Google SHALL 使用 `languageCode=zh-Hans` 與 `regionCode=HK`
- **AND** DATA.GOV.HK SHALL 優先使用 `*_sc` 欄位
- **AND** 官方網站首頁與私隱政策 SHALL 分別使用 `/zh-hans/` 與 `/zh-hans/privacy/`

#### Scenario: 英文 provider 映射
- **WHEN** 實際語言為英文
- **THEN** Citybus SHALL 使用 `l=1`
- **AND** Google SHALL 使用 `languageCode=en` 與 `regionCode=HK`
- **AND** DATA.GOV.HK SHALL 優先使用 `*_en` 欄位
- **AND** 官方網站首頁與私隱政策 SHALL 分別使用 `/en/` 與 `/en/privacy/`

### Requirement: 保存與匯入資料保持原文
系統 SHALL 保留用戶資料中的路線名稱及地點名稱原文，不因 App 語言切換而隱式改寫。

#### Scenario: 切換語言後顯示既有路線
- **WHEN** 用戶切換 App 語言
- **THEN** 已保存路線名稱與起終點名稱 SHALL 保持原文
- **AND** 系統 SHALL NOT 機器翻譯或字形轉換該資料

#### Scenario: 新選擇或編輯地點
- **WHEN** 用戶在某一 App 語言下重新選擇 Citybus 地點或目前位置地址
- **THEN** 新保存的地點名稱 SHALL 使用該次 provider 返回的目前語言原文

#### Scenario: 匯入與匯出路線
- **WHEN** 系統匯入或匯出常用路線
- **THEN** 路線名稱與地點名稱 SHALL 保持檔案原文
- **AND** 系統 SHALL NOT 因目前 App 語言改寫匯入匯出格式或內容

### Requirement: 三語版面與無障礙在窄屏及大字體下可用
系統 SHALL 讓三語 App 自有畫面在約 360dp portrait 及大字體下保持核心內容可讀、核心操作可達及無障礙語義完整。

#### Scenario: 標準字體完整畫面驗證
- **WHEN** 在 API 36.1 與 37、約 360dp portrait、font scale 1.0，以淺色及深色模式分別顯示三種語言
- **THEN** 常用、搜尋、設定三個 destination、底部導航、所有次級 App 畫面、Bottom Sheet、Dialog、Toast 與 Notification SHALL 無文字重疊、無核心裁切且操作可點擊

#### Scenario: 高風險畫面放大字體
- **WHEN** 底部導航、常用／搜尋結果、路線卡、地點選擇、設定、詳情或監測畫面以 font scale 1.3 顯示
- **THEN** 文字與 action SHALL 保持可讀及不重疊

#### Scenario: 關鍵流程使用 font scale 2.0
- **WHEN** 查詢、編輯、詳情、監測、語言切換或路線匯入匯出流程以 font scale 2.0 顯示
- **THEN** 系統 SHALL 透過換行、增加高度或滾動容納內容
- **AND** 系統 SHALL NOT 以不可讀縮字、不可達 action、半截裁切或控件重疊容納內容

#### Scenario: 受控省略長動態名稱
- **WHEN** compact 路線卡無法完整展示長站名
- **THEN** 系統 SHALL 只在 compact 卡片內受控省略次要動態文字
- **AND** 完整內容 SHALL 可在詳情及無障礙描述中取得

### Requirement: 語言與外觀偏好保持獨立且可組合
系統 SHALL 將 App 語言選擇與外觀模式作為兩個正交偏好保存及套用，並 SHALL 讓 locale 與 night mode 資源限定符共同生效。

#### Scenario: 切換語言保持外觀模式
- **WHEN** 用戶已選擇跟隨系統、淺色模式或深色模式中的任一外觀模式
- **AND** 用戶切換 App 語言
- **THEN** 系統 SHALL 保持已保存外觀模式不變
- **AND** 重建後 SHALL 使用新語言與原外觀模式的組合

#### Scenario: 切換外觀保持 App 語言
- **WHEN** 用戶已選擇任一明確 App 語言或跟隨系統
- **AND** 用戶切換外觀模式
- **THEN** 系統 SHALL 保持已保存語言選擇不變
- **AND** 重建後 SHALL 使用原語言與新外觀模式的組合

#### Scenario: 連續切換只套用目標偏好
- **WHEN** 用戶在設定 destination 先後變更語言與外觀模式
- **THEN** 每次操作 SHALL 只更新對應偏好並由 AppCompat 套用該 configuration 維度
- **AND** 系統 SHALL NOT 因額外手動重建造成循環、重複套用或回到其他頂層 destination

### Requirement: 本地化規則與真實驗證形成完成門檻
專案 SHALL 保存可供後續迭代執行的三語規則，並以真實外部服務驗證動態語言正確性。

#### Scenario: 長期規則文件存在
- **WHEN** 本 change 完成
- **THEN** `AGENTS.md` SHALL 區分繁體中文文件規則與三語 App runtime 文案規則
- **AND** `docs/localization-guidelines.md` SHALL 記錄語氣、術語、provider、fallback、TTS、版面及測試規則
- **AND** `openspec/config.yaml` SHALL 保存全專案而非單一 change 的目前架構與三語約束
- **AND** `README.md` SHALL 按目前專案功能、架構、配置、資料源及驗證方式完成翻新

#### Scenario: 真實三語動態資料驗證
- **WHEN** 團隊判定本 change 是否完成
- **THEN** Citybus 地點、路線、停站、詳情及 ETA SHALL 通過繁體、簡體及英文真實請求驗證
- **AND** Google SHALL 對相同香港座標以 `zh-Hant`、`zh-Hans`、`en` 通過真實 Geocoding v4 驗證
- **AND** mock 或 fixture SHALL NOT 取代上述完成門檻
