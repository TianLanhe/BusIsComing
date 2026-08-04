## ADDED Requirements

### Requirement: 系統按 Google Play 可用性選擇更新渠道
系統 SHALL 優先使用目前裝置上可用的官方 Google Play 判斷及執行更新，並 SHALL 只在初始為非 Play 安裝且目前沒有可用官方 Play 時使用官方網站渠道。

#### Scenario: Debug 構建不宣稱 Play 已是最新
- **WHEN** 目前 App 為 debuggable 構建
- **AND** 系統發起自動或手動更新檢查
- **THEN** 系統 SHALL NOT 呼叫 Play package probe、Play 更新服務或網站 metadata
- **AND** 系統 SHALL NOT 保存可靠的已是最新或更新可用快照
- **AND** 手動檢查 SHALL 提供前往 Google Play 的受控提示
- **AND** 自動檢查 SHALL 保持靜默並保留 24 小時嘗試節流

#### Scenario: Play 可用且允許目前用戶更新
- **WHEN** 裝置有可用的官方 Google Play
- **AND** Google Play 回報目前用戶有較高 versionCode 可更新
- **THEN** 系統 SHALL 將 Google Play 設為目前更新渠道
- **AND** 系統 SHALL NOT 因 App 最初由網站或其他非 Play 方式安裝而改用網站下載

#### Scenario: Play 更新展示真實 versionName
- **WHEN** Google Play 回報目前用戶有較高 versionCode 可更新
- **AND** 官方網站 metadata 的 versionCode 與 Play 可用 versionCode 精確一致
- **THEN** 系統 SHALL 只使用該 metadata 的 versionName 作展示名稱
- **AND** 設定摘要與更新 Dialog SHALL 以單一小寫 `v` 前綴展示，例如 `v1.2`
- **AND** 網站 metadata SHALL NOT 改變 Play 更新資格、渠道或 flexible 能力

#### Scenario: Play 更新暫無可驗證 versionName
- **WHEN** Google Play 回報目前用戶有較高 versionCode 可更新
- **AND** 網站 metadata 的 versionCode 不一致、請求失敗或資料無效
- **THEN** 系統 SHALL 保留 Google Play 的可靠更新結果與小紅點
- **AND** 設定摘要 SHALL 使用不含版本數字的通用更新文案
- **AND** 更新 Dialog SHALL 隱藏版本行
- **AND** 系統 SHALL NOT 把 availableVersionCode 當作 versionName 展示

#### Scenario: Play 對目前用戶沒有更新
- **WHEN** Google Play 回報目前帳號、軌道、地區及裝置沒有可用更新
- **THEN** 系統 SHALL 將目前版本視為對該用戶已是最新
- **AND** 系統 SHALL NOT 以網站全局版本覆蓋 Play 的資格判斷

#### Scenario: Play 存在但帳號尚未擁有 App
- **WHEN** Play 更新服務回報 `ERROR_APP_NOT_OWNED`
- **THEN** 系統 SHALL 保持 Google Play 為更新操作渠道
- **AND** 系統 SHALL 讀取只在 Play 目標地區達到 100% 發佈後上線的網站 metadata，判斷是否存在較高 `versionCode`
- **AND** 發現更新時系統 SHALL 將用戶導向 Google Play 而非網站 APK
- **AND** 網站 metadata 只有在 `versionCode` 高於目前 App 時 SHALL 形成可靠更新快照
- **AND** 網站版本相等、較低、請求失敗或 metadata 無效時 SHALL 回報 `PLAY_APP_NOT_OWNED`
- **AND** 系統 SHALL NOT 以這些非正向結果宣稱目前已是最新版本

#### Scenario: Play 暫時失敗
- **WHEN** Play 更新服務因網絡、服務或裝置暫時狀態無法完成檢查
- **AND** 官方 Google Play 仍已安裝、啟用且可處理 App 詳情頁
- **THEN** 系統 SHALL 保持 Play 渠道並保留最近一次可靠結果
- **AND** 系統 SHALL NOT 降級到網站 metadata 或網站 APK

#### Scenario: 非 Play 安裝且沒有可用 Play
- **WHEN** 初始安裝渠道為非 Play 或沒有 Play 的未知非 Play 安裝
- **AND** Play 更新服務與 package 可用性檢查均確認沒有可用官方 Google Play
- **THEN** 系統 SHALL 使用官方網站 metadata 判斷更新

#### Scenario: Play 初始安裝後 Play 被停用
- **WHEN** 系統已保存初始安裝渠道為 Google Play
- **AND** Google Play 其後被停用、移除或變得不可用
- **THEN** 系統 SHALL 顯示 Play 暫不可用的受控狀態
- **AND** 系統 SHALL NOT 改用網站 APK 更新該安裝

#### Scenario: 初始安裝渠道持久化
- **WHEN** 系統首次判斷目前 App 的安裝渠道
- **THEN** 系統 SHALL 把渠道保存為 Play、非 Play 或未知非 Play
- **AND** 後續跨渠道更新 SHALL NOT 改寫初始渠道
- **AND** 目前有可用 Play 時 SHALL 始終由 Play 優先級覆蓋該初始渠道

### Requirement: 系統提供節流的自動檢查與不受節流的手動檢查
系統 SHALL 在 App 冷啟動後非阻塞地執行最多每 24 小時一次的自動檢查，並 SHALL 允許設定頁手動檢查繞過該節流。

#### Scenario: 冷啟動達到自動檢查間隔
- **WHEN** App 冷啟動並已完成首個主要畫面展示
- **AND** 距離上次自動檢查嘗試已達 24 小時
- **THEN** 系統 SHALL 在背景發起一次渠道感知更新檢查
- **AND** 系統 SHALL NOT 阻塞首屏展示或主要互動

#### Scenario: 冷啟動未達自動檢查間隔
- **WHEN** App 冷啟動距離上次自動檢查嘗試少於 24 小時
- **THEN** 系統 SHALL NOT 發起新的自動網絡或 Play 檢查
- **AND** 設定頁 SHALL 繼續使用最近一次可靠結果

#### Scenario: 自動檢查間隔邊界
- **WHEN** 距離上次自動檢查嘗試為 24 小時前一毫秒
- **THEN** 系統 SHALL NOT 發起新的自動檢查
- **AND** 剛好達到或超過 24 小時時系統 SHALL 允許新的自動檢查

#### Scenario: 自動檢查失敗
- **WHEN** 自動檢查因 Play、網站、網絡或資料格式失敗
- **THEN** 系統 SHALL 保持靜默且 SHALL NOT 顯示 Toast 或更新 Dialog
- **AND** 系統 SHALL 保留最近一次可靠版本結果及小紅點
- **AND** 本次嘗試 SHALL 仍受後續 24 小時間隔限制

#### Scenario: 手動檢查繞過間隔
- **WHEN** 用戶點擊設定頁的「檢查更新」
- **THEN** 系統 SHALL 立即發起或附著到正在進行的更新檢查
- **AND** 系統 SHALL NOT 因距離上次自動檢查少於 24 小時而拒絕該操作

#### Scenario: 自動與手動檢查重疊
- **WHEN** 自動檢查仍在進行且用戶發起手動檢查
- **THEN** 系統 SHALL 共用同一個有效請求或可靠地作廢舊請求
- **AND** 系統 SHALL NOT 讓較舊結果覆蓋較新結果或重複顯示更新 Dialog

### Requirement: 系統按渠道計算更新可用時間
系統 SHALL 使用對目前用戶有效的更新可用時間判斷 3 天提醒門檻，並 SHALL 在權威時間缺失時使用本機首次發現時間作保守兜底。

#### Scenario: Play 提供 staleness 天數
- **WHEN** Google Play 回報較高 versionCode 及 `clientVersionStalenessDays`
- **THEN** 系統 SHALL 使用該用戶的 staleness 天數判斷更新已可用多久
- **AND** 系統 SHALL NOT 以網站全局發佈日期取代 Play 結果

#### Scenario: Play 未提供 staleness 天數
- **WHEN** Google Play 回報較高 versionCode
- **AND** `clientVersionStalenessDays` 不可用
- **THEN** 系統 SHALL 保存並使用本機首次發現該 versionCode 的時間

#### Scenario: 網站渠道提供更新日期
- **WHEN** 網站 metadata 合法且包含較高 versionCode 與 `lastUpdated`
- **THEN** 系統 SHALL 把日期解析為香港時區當日零時並以滿 72 小時計算更新已發佈多久

#### Scenario: 網站三天門檻邊界
- **WHEN** 目前時間距網站 `lastUpdated` 香港零時為 72 小時前一毫秒
- **THEN** 系統 SHALL 視為尚未到期
- **AND** 剛好達到或超過 72 小時時系統 SHALL 視為已滿 3 天

### Requirement: 系統管理首次提醒、稍後提醒與略過版本
系統 SHALL 只在更新對目前用戶可用滿 3 天後自動提醒，並 SHALL 以 versionCode 隔離稍後提醒與略過狀態。

#### Scenario: 更新未滿三天
- **WHEN** 系統已可靠發現較高 versionCode
- **AND** 更新對目前用戶可用少於 3 天
- **THEN** 系統 SHALL 保存並展示更新狀態
- **AND** 系統 SHALL NOT 自動顯示更新 Dialog

#### Scenario: 首次自動提醒
- **WHEN** 較高 versionCode 已對目前用戶可用至少 3 天
- **AND** 該 versionCode 未被略過且不在稍後提醒期限內
- **AND** 前台 Activity 處於可安全展示 Dialog 的狀態
- **THEN** 系統 SHALL 顯示更新 Dialog

#### Scenario: 更新 Dialog 只能明確選擇操作
- **WHEN** 系統顯示更新 Dialog
- **THEN** Dialog SHALL 只提供「前往更新」「稍後提醒」「略過此版本」三個操作
- **AND** Dialog SHALL NOT 因返回鍵或點擊外部而關閉

#### Scenario: 稍後提醒
- **WHEN** 用戶選擇「稍後提醒」
- **THEN** 系統 SHALL 把該 versionCode 的下一次自動提醒延後 3 天
- **AND** 設定頁 SHALL 繼續展示更新及小紅點

#### Scenario: 稍後提醒期限邊界
- **WHEN** 同一 versionCode 距離稍後提醒操作為 72 小時前一毫秒
- **THEN** 系統 SHALL 繼續抑制自動 Dialog
- **AND** 剛好達到或超過 72 小時時系統 SHALL 允許再次自動提醒

#### Scenario: 略過目前版本
- **WHEN** 用戶選擇「略過此版本」
- **THEN** 系統 SHALL 停止為該 versionCode 顯示自動更新 Dialog
- **AND** 系統 SHALL 繼續進行後續版本檢查並展示該更新及小紅點

#### Scenario: 點擊前往更新但未完成安裝
- **WHEN** 用戶選擇「前往更新」
- **THEN** 系統 SHALL 在啟動渠道更新操作前把該 versionCode 暫緩 3 天
- **AND** 用戶未完成安裝時系統 SHALL NOT 在次日再次自動提醒

#### Scenario: 發現更高版本
- **WHEN** 系統其後發現比已稍後或已略過 versionCode 更高的新版本
- **THEN** 舊版本的稍後與略過狀態 SHALL NOT 抑制新版本
- **AND** 新版本 SHALL 重新按其可用時間判斷 3 天門檻

#### Scenario: Activity 不可安全展示 Dialog
- **WHEN** 自動提醒條件成立但 Activity 不在 resumed 狀態或已保存狀態
- **THEN** 系統 SHALL 延後到前台可安全展示時再顯示
- **AND** 系統 SHALL NOT 從背景直接彈出 Dialog

### Requirement: 設定頁展示可靠更新狀態與小紅點
系統 SHALL 讓「檢查更新」設定列展示最近一次可靠狀態，並 SHALL 在可靠結果表明有較高 versionCode 時顯示具無障礙語義的小紅點。

#### Scenario: 設定頁尚未檢查
- **WHEN** 本機尚無可靠更新結果
- **THEN** 設定列 SHALL 提示用戶可點擊檢查新版本
- **AND** 系統 SHALL NOT 顯示小紅點

#### Scenario: 手動檢查進行中
- **WHEN** 用戶已發起手動檢查且結果尚未返回
- **THEN** 設定列 SHALL 顯示正在檢查狀態
- **AND** 系統 SHALL 防止同一入口重複提交

#### Scenario: 目前已是最新版本
- **WHEN** 最近一次可靠結果表明沒有較高 versionCode
- **THEN** 設定列 SHALL 以目前 App 語言顯示已是最新版本
- **AND** 系統 SHALL NOT 顯示小紅點

#### Scenario: 發現較高版本
- **WHEN** 最近一次可靠結果包含高於目前 App 的 versionCode
- **THEN** 設定列 SHALL 顯示可用 versionName 或等價更新摘要
- **AND** 「檢查更新」標題旁 SHALL 立即顯示不含數字的小紅點
- **AND** 輔助技術 SHALL 能讀取有新版本可用的語義

#### Scenario: 查看、稍後或略過不清除小紅點
- **WHEN** 用戶點擊設定列、選擇稍後提醒或略過目前版本
- **THEN** 系統 SHALL 保留小紅點直到 App 升級或可靠檢查確認無更新

#### Scenario: 檢查失敗但已有可靠更新
- **WHEN** 本次檢查失敗
- **AND** 最近一次可靠結果仍表明有較高 versionCode
- **THEN** 系統 SHALL 保留更新摘要及小紅點
- **AND** 手動檢查 SHALL 另顯示可重試的失敗提示

#### Scenario: App 已完成升級
- **WHEN** App 啟動時目前 versionCode 已不低於快照的可用 versionCode
- **THEN** 系統 SHALL 在不等待網絡的情況下清理舊更新、小紅點、稍後及略過狀態

### Requirement: Google Play 渠道優先使用 flexible update
系統 SHALL 在 Play 允許時由用戶主動啟動 flexible in-app update，並 SHALL 對不允許或失敗狀態提供 Play 詳情頁恢復路徑。

#### Scenario: flexible update 可用
- **WHEN** 用戶選擇「前往更新」
- **AND** Google Play 允許 `AppUpdateType.FLEXIBLE`
- **THEN** 系統 SHALL 啟動 flexible update
- **AND** 用戶 SHALL 能在下載期間繼續使用 App

#### Scenario: flexible update 不可用
- **WHEN** Google Play 有更新但不允許 flexible flow，或 flow 無法啟動
- **THEN** 系統 SHALL 嘗試開啟 `market://details?id=com.golink.busiscoming`
- **AND** market Intent 無法處理時 SHALL 改開 Google Play HTTPS 詳情頁
- **AND** 系統 SHALL NOT 改用網站 APK

#### Scenario: flexible 下載完成
- **WHEN** Google Play 回報更新已下載完成
- **THEN** 系統 SHALL 顯示持續可操作的「重新啟動並安裝」提示
- **AND** 只有用戶確認後系統 SHALL 請求完成更新

#### Scenario: App 返回前台時有待完成更新
- **WHEN** App 回到前台且 Play 更新已下載但尚未完成
- **THEN** 系統 SHALL 恢復「重新啟動並安裝」提示
- **AND** 系統 SHALL NOT 因 Activity 重建遺失完成入口

### Requirement: 網站渠道驗證 metadata 並開啟三語下載頁
系統 SHALL 只接受官方 HTTPS metadata 的白名單版本資料，並 SHALL 讓網站渠道用戶在瀏覽器查看目前語言下載頁後自行確認下載。

#### Scenario: 合法網站 metadata 有更新
- **WHEN** 網站渠道取得 `https://www.busiscoming.com/api/downloads/android/latest/metadata`
- **AND** 響應的 `platform`、`status`、`versionName`、`versionCode`、`fileName`、`sizeBytes`、`lastUpdated` 與 `downloadUrl` 均合法
- **AND** versionCode 高於目前 App
- **THEN** 系統 SHALL 保存網站更新結果

#### Scenario: 網站 metadata 不依賴 applicationId
- **WHEN** 官方網站 metadata 不包含 `applicationId`
- **AND** 其他必填欄位與來源均合法
- **THEN** 系統 SHALL 正常完成版本判斷
- **AND** 系統 SHALL NOT 因缺少 `applicationId` 把響應視為失敗

#### Scenario: 網站 metadata 使用固定相對下載路徑
- **WHEN** 合法 metadata 的 `downloadUrl` 為 `/api/downloads/android/latest`
- **THEN** 系統 SHALL 接受該響應
- **AND** 系統 SHALL 只把該欄位用作契約驗證而不直接啟動下載

#### Scenario: 網站 metadata 使用等價官方絕對下載 URL
- **WHEN** 合法 metadata 的 `downloadUrl` 為 `https://www.busiscoming.com/api/downloads/android/latest`
- **THEN** 系統 SHALL 接受該響應
- **AND** 系統 SHALL 仍按目前語言開啟網站 `#download` 頁面

#### Scenario: 網站 metadata 沒有較高版本
- **WHEN** 合法網站 metadata 的 versionCode 不高於目前 App
- **THEN** 系統 SHALL 將目前版本視為網站渠道的最新版本
- **AND** 系統 SHALL NOT 顯示更新小紅點或 Dialog

#### Scenario: 網站 metadata 無效
- **WHEN** metadata 來自非 HTTPS／非官方 host、來源 URL 帶有非預期 port／query／fragment、缺少必填欄位、日期非法、狀態不可用，或 `downloadUrl` 為其他相對路徑、scheme-relative URL、非官方 host、HTTP、非預期 port、帶 query／fragment 的 URL
- **THEN** 系統 SHALL 將本次檢查視為失敗
- **AND** 系統 SHALL NOT 以服務端任意 URL 啟動外部下載

#### Scenario: 繁體網站更新入口
- **WHEN** 網站渠道用戶以繁體中文選擇「前往更新」
- **THEN** 系統 SHALL 在瀏覽器開啟 `https://www.busiscoming.com/zh-hant/#download`

#### Scenario: 簡體網站更新入口
- **WHEN** 網站渠道用戶以簡體中文選擇「前往更新」
- **THEN** 系統 SHALL 在瀏覽器開啟 `https://www.busiscoming.com/zh-hans/#download`

#### Scenario: 英文網站更新入口
- **WHEN** 網站渠道用戶以英文選擇「前往更新」
- **THEN** 系統 SHALL 在瀏覽器開啟 `https://www.busiscoming.com/en/#download`

#### Scenario: App 不直接下載或安裝 APK
- **WHEN** 網站渠道用戶選擇「前往更新」
- **THEN** App SHALL 只開啟普通 HTTPS 網站頁面
- **AND** App SHALL NOT 直接下載、驗證或安裝 APK
- **AND** App SHALL NOT 要求 `REQUEST_INSTALL_PACKAGES` 或 `QUERY_ALL_PACKAGES`

### Requirement: 更新能力完整支援三語、深淺色與無障礙
系統 SHALL 讓所有更新狀態、操作、錯誤及無障礙內容完整支援香港繁體、簡體中文與英文，並在深淺色、窄屏及大字體下保持可用。

#### Scenario: 更新文案跟隨 App 語言
- **WHEN** 系統顯示設定列、更新 Dialog、錯誤、Play 完成提示或網站跳轉失敗
- **THEN** 所有 App 自有文字 SHALL 使用目前實際 App 語言
- **AND** 系統 SHALL NOT 在 Kotlin 或 XML 中硬編碼用戶可見文案

#### Scenario: 窄屏與大字體顯示三個操作
- **WHEN** 約 360dp portrait 或 font scale 1.3／2.0 顯示更新 Dialog
- **THEN** 三個操作 SHALL 保持完整可讀及可點擊
- **AND** 系統 SHALL 使用換行、增加高度、滾動或垂直操作佈局容納內容
- **AND** 系統 SHALL NOT 以不可讀縮字或核心文字裁切容納翻譯

#### Scenario: 小紅點不依賴顏色傳意
- **WHEN** 設定頁顯示有更新的小紅點
- **THEN** 設定列文字與無障礙描述 SHALL 同時表達有新版本
- **AND** 淺色與深色模式 SHALL 使用具足夠對比的語意色

### Requirement: 網站 APK 與 Google Play 維持相容發佈鏈
發佈流程 SHALL 讓網站 APK 使用 Google Play app signing key 簽署的相同 application ID 與 versionCode，並 SHALL 只在 Play 目標地區完成相同版本 100% 發佈後公開網站版本。

#### Scenario: 產生網站正式 APK
- **WHEN** 團隊準備在網站公開 Android APK
- **THEN** APK SHALL 為從 Play Console 取得的 signed universal APK
- **AND** APK 的簽名憑證 SHALL 等於 Google Play app signing key 而非 upload key

#### Scenario: 網站版本上線順序
- **WHEN** 新版本尚未在 Google Play 目標地區完成 100% 發佈
- **THEN** 網站 SHALL NOT 公開該版本 APK 或把它標記為目前版本

#### Scenario: 網站 metadata 來自實際 APK
- **WHEN** 網站準備公開 Play 簽署 APK
- **THEN** application ID、versionName、versionCode、sizeBytes 與 SHA-256 SHALL 從實際 APK 驗證或提取，其中 application ID 與簽名屬發佈驗證而非公開 runtime metadata 必填欄位
- **AND** metadata、下載響應與 APK bytes SHALL 一致
