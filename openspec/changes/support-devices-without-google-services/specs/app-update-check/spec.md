## MODIFIED Requirements

### Requirement: 系統按 Google Play 可用性選擇更新渠道
系統 SHALL 優先使用目前裝置上可用的官方 Google Play 判斷及執行更新，並 SHALL 在官方 Play 商店明確缺失或停用時使用官方網站渠道，不受初始安裝來源限制。商店可用時的既有 Play 優先行為 SHALL 保持不變；未能判定、無法處理商品頁或暫時請求失敗 SHALL NOT 被當作缺失／停用。

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
- **WHEN** 初始安裝渠道為非 Play 或未知
- **AND** 套件能力檢查明確確認官方 Google Play 商店缺失或停用
- **THEN** 系統 SHALL 使用官方網站 metadata 判斷更新
- **AND** 系統 SHALL NOT 為此建立或呼叫不可用的 Play 更新服務

#### Scenario: Play 初始安裝後 Play 被停用
- **WHEN** 系統已保存初始安裝渠道為 Google Play
- **AND** 官方 Google Play 商店其後明確被停用或移除
- **THEN** 系統 SHALL 使用官方網站 metadata 判斷更新，並在符合既有提示規則時允許用戶主動前往三語下載頁
- **AND** 系統 SHALL 保留初始安裝渠道與既有資料，不自動下載或安裝 APK

#### Scenario: 初始安裝渠道持久化
- **WHEN** 系統首次判斷目前 App 的安裝渠道
- **THEN** 系統 SHALL 把渠道保存為 Play、非 Play 或未知非 Play
- **AND** 後續跨渠道更新 SHALL NOT 改寫初始渠道
- **AND** 目前有可用 Play 時 SHALL 始終由 Play 優先級覆蓋該初始渠道

#### Scenario: 商店探測不能確認缺失或停用
- **WHEN** 商店探測出錯，或商店存在並啟用但不能處理商品頁
- **THEN** 系統 SHALL 返回受控的 Play 不可用／未能判定狀態並保留最近可靠快照
- **AND** 系統 SHALL NOT 僅因該狀態轉向網站 APK 或宣稱已是最新

#### Scenario: 商店恢復後回到原渠道優先級
- **WHEN** 官方 Google Play 商店恢復可用且系統下一次有效檢查或使用者執行更新操作
- **THEN** 系統 SHALL 重新依目前能力選擇既有 Play 優先路徑
- **AND** 舊網站 callback SHALL NOT 覆蓋新能力世代的渠道結果
- **AND** 自動節流、提醒與版本略過狀態 SHALL 按既有規則保留

#### Scenario: 前台恢復時商店仍缺失
- **WHEN** App 回到前台或恢復既有更新 UI，但商店明確缺失或停用
- **THEN** 系統 SHALL 不呼叫不可用的 Play 下載狀態、listener 或完成更新能力
- **AND** 可預期的 SDK 建立／呼叫例外 SHALL 轉成受控結果，不阻止主要頁面使用

### Requirement: 網站 APK 與 Google Play 維持相容發佈鏈
發佈流程 SHALL 讓網站 APK 使用 Google Play app signing key 簽署的相同 application ID 與 versionCode，並 SHALL 只在 Play 目標地區完成相同版本 100% 發佈後公開網站版本。正式 Play 與網站產物 SHALL 不包含要求 Play 商店可用才能啟動的安裝程式檢查，且 SHALL 保留相容簽名及本機資料。

#### Scenario: 產生網站正式 APK
- **WHEN** 團隊準備在網站公開 Android APK
- **THEN** APK SHALL 為從 Play Console 取得、未套用安裝程式檢查的 signed universal APK
- **AND** APK 的簽名憑證 SHALL 等於 Google Play app signing key 而非 upload key

#### Scenario: 網站版本上線順序
- **WHEN** 新版本尚未在 Google Play 目標地區完成 100% 發佈
- **THEN** 網站 SHALL NOT 公開該版本 APK 或把它標記為目前版本

#### Scenario: 網站 metadata 來自實際 APK
- **WHEN** 網站準備公開 Play 簽署 APK
- **THEN** application ID、versionName、versionCode、sizeBytes 與 SHA-256 SHALL 從實際 APK 驗證或提取，其中 application ID 與簽名屬發佈驗證而非公開 runtime metadata 必填欄位
- **AND** metadata、下載響應與 APK bytes SHALL 一致

#### Scenario: 正式產物移除啟動檢查
- **WHEN** 團隊準備發佈此相容修復版本
- **THEN** 團隊 SHALL 核對 Play 自動保護設定與實際產物，並以無 GMS／Play 及受影響手機的超級省電場景驗證啟動
- **AND** 關閉控制台開關或本機 debug 啟動成功 SHALL NOT 被視為已安裝舊包或新正式包已通過驗證

#### Scenario: 從原版本升級
- **WHEN** 使用者以符合 Android 升級條件的同 application ID、相容 Play 簽名及較高 versionCode 安裝修復版本
- **THEN** 系統 SHALL 保留行程、置頂及偏好資料
- **AND** 系統 SHALL NOT 要求先卸載舊版本來解決簽名不相容
