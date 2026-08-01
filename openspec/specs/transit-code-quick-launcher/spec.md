# transit-code-quick-launcher Specification

## Purpose
定義當前生產 `乘車碼` 單按鈕入口：根據 AlipayHK 與支付寶安裝狀態自動組裝候選鏈，優先 AlipayHK，並與已移除的微信實驗入口完全解耦。
## Requirements

### Requirement: 正式乘車碼入口按安裝狀態組裝候選鏈
系統 SHALL 在用戶使用正式 `乘車碼` 入口時，根據本機 AlipayHK 與支付寶安裝狀態自動組裝固定候選鏈，並優先使用 AlipayHK。

#### Scenario: 只安裝 AlipayHK
- **WHEN** 用戶點擊正式 `乘車碼` 入口且系統明確檢測到只安裝了 AlipayHK
- **THEN** 系統 SHALL 先嘗試 `alipayhk://platformapi/startApp?appId=85200098`
- **AND** 若該候選本地啟動失敗，系統 SHALL 再嘗試 `https://render.alipay.hk/p/s/hkwallet/landing/easygo`
- **AND** 系統 SHALL NOT 嘗試支付寶候選入口

#### Scenario: 只安裝支付寶
- **WHEN** 用戶點擊正式 `乘車碼` 入口且系統明確檢測到只安裝了支付寶
- **THEN** 系統 SHALL 先嘗試 `alipays://platformapi/startapp?appId=200011235`
- **AND** 若該候選本地啟動失敗，系統 SHALL 再嘗試 `https://render.alipay.com/p/s/i?appId=200011235`
- **AND** 系統 SHALL NOT 嘗試 AlipayHK 候選入口

#### Scenario: 同時安裝 AlipayHK 與支付寶
- **WHEN** 用戶點擊正式 `乘車碼` 入口且系統檢測到 AlipayHK 與支付寶都已安裝
- **THEN** 系統 SHALL 依序嘗試 `alipayhk://platformapi/startApp?appId=85200098`、`https://render.alipay.hk/p/s/hkwallet/landing/easygo`、`alipays://platformapi/startapp?appId=200011235`、`https://render.alipay.com/p/s/i?appId=200011235`
- **AND** 系統 SHALL 僅在 AlipayHK 兩個候選都本地啟動失敗後才降級嘗試支付寶候選

#### Scenario: AlipayHK 與支付寶都未安裝
- **WHEN** 用戶點擊正式 `乘車碼` 入口且系統檢測到 AlipayHK 與支付寶都未安裝
- **THEN** 系統 SHALL 直接嘗試 `https://render.alipay.hk/p/s/hkwallet/landing/easygo`
- **AND** 系統 SHALL NOT 嘗試任何 scheme 候選
- **AND** 系統 SHALL NOT 顯示平台選擇器

### Requirement: 本地啟動失敗才觸發自動兜底
系統 SHALL 只在當前候選發生本地啟動失敗時嘗試下一個候選；一旦 Android 接受啟動請求，系統 SHALL 停止候選遍歷。

#### Scenario: scheme 沒有可處理 Activity
- **WHEN** 系統嘗試當前 scheme 候選且 Android 找不到可處理 Activity
- **THEN** 系統 SHALL 記錄該候選本地啟動失敗
- **AND** 若候選鏈仍有下一項，系統 SHALL 自動嘗試下一項

#### Scenario: startActivity 發生安全或啟動異常
- **WHEN** 系統嘗試當前候選且啟動流程發生 `SecurityException`、`ActivityNotFoundException` 或其他可捕獲啟動異常
- **THEN** 系統 SHALL 將該候選視為本地啟動失敗
- **AND** 若候選鏈仍有下一項，系統 SHALL 自動嘗試下一項

#### Scenario: 候選被 Android 成功接受
- **WHEN** 系統嘗試任一候選且 Android 成功接受啟動請求
- **THEN** 系統 SHALL 停止嘗試候選鏈中後續項目
- **AND** 系統 SHALL NOT 因無法判斷外部錢包內部頁面而繼續兜底
- **AND** 系統 SHALL NOT 顯示失敗 toast

#### Scenario: 所有候選均本地啟動失敗
- **WHEN** 系統已嘗試當前安裝狀態對應候選鏈中的所有候選且全部本地啟動失敗
- **THEN** 系統 SHALL 停止候選遍歷
- **AND** 系統 SHALL 顯示 `未能開啟乘車碼，請確認已安裝 AlipayHK 或支付寶。`

### Requirement: 正式入口與實驗入口及巴士查詢狀態隔離
系統 SHALL 將所有正式乘車碼入口匯入同一單按鈕自動拉起流程，並 SHALL NOT 保留或依賴已廢棄的微信／AlipayHK 實驗面板；正式入口 SHALL 與巴士查詢及候車監控狀態隔離。

#### Scenario: 正式入口不打開實驗面板
- **WHEN** 用戶使用靜態 shortcut、pinned shortcut 或通知乘車碼 action
- **THEN** 系統 SHALL 直接執行正式乘車碼候選鏈
- **AND** 系統 SHALL NOT 顯示實驗性乘車碼底部彈層、候選列表或診斷摘要

#### Scenario: 正式入口不嘗試微信
- **WHEN** 用戶使用任何正式乘車碼入口
- **THEN** 系統 SHALL NOT 嘗試微信 OpenSDK、小程序或微信 scheme
- **AND** 系統 SHALL NOT 引入微信 OpenSDK runtime dependency

#### Scenario: 使用乘車碼後不保存支付偏好
- **WHEN** 用戶使用正式乘車碼入口並成功或失敗返回 App
- **THEN** 系統 SHALL NOT 寫入任何 AlipayHK／支付寶偏好
- **AND** 系統 SHALL NOT 新增、修改或刪除任何常用行程資料

#### Scenario: 使用乘車碼不改變巴士狀態
- **WHEN** 用戶已有選中行程、搜尋上下文、排序、查詢結果或運行中的監控時使用正式乘車碼入口
- **THEN** 系統 SHALL 保留既有巴士查詢狀態與監控 session
- **AND** 系統 SHALL NOT 自動查詢、刷新、清空結果或停止監控

#### Scenario: 使用乘車碼入口後不保存支付偏好
- **WHEN** 用戶使用正式 `乘車碼` 入口並成功或失敗返回 App
- **THEN** 系統 SHALL NOT 寫入任何 AlipayHK／支付寶偏好
- **AND** 系統 SHALL NOT 新增、修改或刪除任何常用路線資料

#### Scenario: 使用乘車碼入口後不改變巴士查詢狀態
- **WHEN** 用戶在主頁已有選中路線、臨時查詢上下文、排序選擇或查詢結果時點擊正式 `乘車碼`
- **THEN** 系統 SHALL 保留既有巴士查詢狀態
- **AND** 系統 SHALL NOT 自動發起 Citybus 查詢、刷新結果或清空結果列表

### Requirement: 系統聲明正式錢包 package visibility
系統 SHALL 在 Android 11+ package visibility 限制下能檢測 AlipayHK 與支付寶安裝狀態，以支援正式候選鏈決策，且 SHALL NOT 為已移除的微信實驗能力聲明 package visibility。

#### Scenario: Manifest 包含正式錢包查詢聲明
- **WHEN** App 在 Android 11+ 裝置上運行
- **THEN** manifest SHALL 包含 `hk.alipay.wallet` package query 聲明
- **AND** manifest SHALL 包含 `com.eg.android.AlipayGphone` package query 聲明
- **AND** manifest SHALL NOT 包含 `com.tencent.mm` package query 聲明
- **AND** manifest SHALL NOT 註冊 `.wxapi.WXEntryActivity`

#### Scenario: package 檢測結果不可用時降級為未安裝
- **WHEN** 系統查詢 AlipayHK 或支付寶 package 狀態時發生可捕獲異常或無法確認已安裝
- **THEN** 系統 SHALL 將該錢包視為未安裝來組裝候選鏈
- **AND** 系統 SHALL NOT 因 package 查詢失敗而讓 `乘車碼` 點擊崩潰

### Requirement: 正式乘車碼由系統快捷與候車情境提供

系統 MUST 只在設定頁提供桌面快捷方式管理入口，並由靜態或 pinned 桌面快捷方式及候車情境中的既有入口開啟正式乘車碼；常用頁及其他高頻主流程不得新增乘車碼按鈕。桌面快捷方式 MUST 使用無界面轉發入口，候車情境入口 MUST 保留既有畫面與互動，兩者均沿用正式乘車碼啟動與降級契約。

#### Scenario: 常用頁不展示乘車碼入口

- **WHEN** 用戶進入常用頁、切換常用行程或瀏覽查詢結果
- **THEN** 頁面 SHALL NOT 顯示乘車碼按鈕、浮動入口或頂部工具區
- **AND** 路線結果的可視空間 SHALL 不被乘車碼入口佔用

#### Scenario: 設定頁只管理桌面快捷方式

- **WHEN** 用戶在設定頁查看乘車碼快捷方式項目
- **THEN** 該項目 SHALL 用於建立、檢查或引導管理桌面快捷方式
- **AND** 點擊該設定項 SHALL NOT 直接把設定頁作為正式乘車碼展示入口

#### Scenario: 桌面快捷方式開啟正式乘車碼

- **WHEN** 用戶點擊靜態或 pinned 乘車碼桌面快捷方式
- **THEN** 系統 SHALL 經無界面轉發入口執行正式乘車碼啟動契約
- **AND** 系統 SHALL NOT 先顯示 App 主頁或設定頁

#### Scenario: 候車情境保留正式乘車碼入口

- **WHEN** 用戶在既有候車或通知情境點擊乘車碼入口
- **THEN** 系統 SHALL 保留該情境既有的交互與狀態
- **AND** 系統 SHALL 使用與桌面快捷方式一致的正式支付 App 候選與降級順序

### Requirement: 乘車碼 pinned shortcut 使用可確認的建立流程
系統 SHALL 透過 Android pinned shortcut 能力建立乘車碼桌面入口，並提供成功 callback 及結構化平台結果，使設定頁不需要推測請求是否完成。

#### Scenario: 建立 pinned shortcut 請求
- **WHEN** launcher 支援 pinned shortcut 且乘車碼 shortcut 尚未固定
- **THEN** 系統 SHALL 使用穩定 shortcut id、App 圖示、當前語言標籤及正式乘車碼 explicit intent 建立請求
- **AND** 請求 SHALL 包含只在 launcher 真正完成固定後觸發的成功 callback

#### Scenario: 查詢已固定狀態
- **WHEN** 系統需要顯示或刷新乘車碼 shortcut 狀態
- **THEN** shortcut manager SHALL 回傳已固定、未固定或不可可靠判定的結構化狀態
- **AND** package／launcher 查詢例外 SHALL NOT 讓設定頁崩潰

#### Scenario: 平台接受但尚未成功
- **WHEN** `requestPinShortcut` 返回 true 且成功 callback 尚未觸發
- **THEN** shortcut manager SHALL 回傳請求已發出狀態
- **AND** 該狀態 SHALL NOT 等同固定成功

#### Scenario: 平台拒絕或拋出例外
- **WHEN** `requestPinShortcut` 返回 false 或拋出可捕獲例外
- **THEN** shortcut manager SHALL 回傳失敗狀態
- **AND** 系統 SHALL 保留再次請求能力

### Requirement: 所有乘車碼快捷入口沿用正式啟動契約

系統 MUST 讓靜態桌面快捷方式、pinned 桌面快捷方式及候車情境中的乘車碼入口共用同一正式支付 App 候選與降級契約。桌面快捷方式可以使用專用無界面轉發 Activity，候車情境可以保留既有 Activity 或通知入口，但不得各自硬編碼不同 URI、候選順序或錯誤提示。

#### Scenario: pinned 快捷方式沿用正式候選鏈

- **WHEN** 用戶從 pinned 桌面快捷方式啟動乘車碼
- **THEN** 無界面轉發入口 SHALL 呼叫既有正式乘車碼啟動器
- **AND** 候選與降級順序 SHALL 與 App 內正式入口一致

#### Scenario: 靜態快捷方式沿用正式候選鏈

- **WHEN** 用戶從靜態桌面快捷方式啟動乘車碼
- **THEN** 無界面轉發入口 SHALL 呼叫既有正式乘車碼啟動器
- **AND** 靜態快捷方式 SHALL NOT 維護獨立的支付 URI 清單

#### Scenario: 候車情境沿用正式候選鏈

- **WHEN** 用戶從既有候車或通知情境啟動乘車碼
- **THEN** 該入口 SHALL 呼叫同一正式乘車碼啟動器
- **AND** 該情境原有的監控、返回與生命週期狀態 SHALL 保持不變

#### Scenario: 正式候選鏈失敗時統一提示

- **WHEN** 任一乘車碼快捷入口無法啟動全部正式候選及降級入口
- **THEN** 系統 SHALL 使用同一組本地化失敗提示與可恢復行為

### Requirement: 桌面乘車碼快捷方式使用無界面轉發入口

系統 MUST 讓靜態與 pinned 桌面乘車碼快捷方式進入不展示 App 內容頁面的轉發入口，由該入口立即沿用既有乘車碼啟動器開啟支付 App，並在完成轉發後結束自身。

#### Scenario: 點擊 pinned 桌面快捷方式直接轉發

- **WHEN** 用戶點擊已建立的 pinned 乘車碼桌面快捷方式
- **THEN** 系統 SHALL 啟動無界面轉發入口並立即執行既有乘車碼候選與降級鏈
- **AND** 系統 SHALL NOT 先展示主頁、設定頁或其他 App 內容頁面
- **AND** 轉發入口 SHALL 在發出外部啟動請求後結束，不留在返回堆疊或最近任務中

#### Scenario: 點擊靜態桌面快捷方式直接轉發

- **WHEN** 用戶點擊 App 提供的靜態乘車碼桌面快捷方式
- **THEN** 系統 SHALL 使用與 pinned 快捷方式相同的無界面轉發入口
- **AND** 系統 SHALL 沿用既有支付 App 候選、scheme 與 HTTPS 降級鏈

#### Scenario: 所有乘車碼候選均無法啟動

- **WHEN** 無界面轉發入口嘗試全部正式候選與降級入口後仍無法開啟乘車碼
- **THEN** 系統 SHALL 顯示既有本地化失敗提示
- **AND** 轉發入口 SHALL 結束自身，不停留空白頁面

#### Scenario: 冷啟動快捷方式時不建立主頁流程

- **WHEN** App 程序尚未運行且用戶點擊桌面乘車碼快捷方式
- **THEN** 系統 SHALL 僅初始化執行乘車碼轉發所需的最小流程
- **AND** 系統 SHALL NOT 建立主頁導覽狀態或將主頁 Activity 加入任務堆疊
