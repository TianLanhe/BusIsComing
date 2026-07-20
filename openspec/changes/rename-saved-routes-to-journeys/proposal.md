## Why

BusIsComing 目前以「路線」同時表示用戶保存的起終點配置，以及 Citybus 查詢返回的乘車方案，令「上班」與 `118`、`85 → 106` 等不同概念在界面和文件中容易混淆。現在 App 已完整支援繁體、簡體與英文，應建立一致的三語術語邊界，讓用戶清楚理解行程與路線的關係。

## What Changes

- 把用戶保存並可重複使用的名稱、起點和終點配置統一稱為「常用行程／行程」，英文沿用 `Regular journey / journey`。
- 繼續把查詢返回的單程或換乘乘車方案稱為「路線」，並在需要描述換乘結構時把其中的單段巴士服務稱為「乘車段」。
- 審查繁體、簡體和英文的 Android string resources，更新常用頁、首次引導、新增／編輯／複製／刪除／管理、搜尋保存、匯入匯出、Dialog、Toast 及無障礙文案；查詢按鈕、結果摘要、路線卡和路線詳情仍使用「路線」。
- 更新 `docs/localization-guidelines.md`、`AGENTS.md`、`README.md` 及描述目前產品行為的現行文件，沉澱權威術語表與長期使用規則；不批量重寫歷史 OpenSpec change artifacts。
- 只在自動化測試直接斷言受影響文案時更新期望文字，不改變測試邏輯、產品流程或資料行為。
- 保留 `RouteConfig` 等 Kotlin 符號、string resource key、SQLite schema、`.bicroutes` 格式與外部服務行為，不進行領域模型重命名或資料遷移。

## Capabilities

### New Capabilities

- `journey-route-terminology`: 定義保存行程、查詢路線與乘車段的三語語義邊界、用戶可見文案範圍、文件沉澱及內部兼容要求。

### Modified Capabilities

無。既有查詢、管理、保存、排序、匯入匯出、監控及本地化能力的功能需求與流程保持不變；本 change 以新的跨功能術語能力統一約束其用戶可見名稱。

## Impact

- **Android 資源**：`app/src/main/res/values*/strings.xml` 中表示已保存起終點配置的繁體、簡體和英文文案；保留所有 resource key。
- **界面與無障礙**：常用首頁、行程新增／編輯／管理、搜尋保存、設定中的行程資料及匯入匯出流程；不改動版面結構、導航、生命週期或錯誤恢復行為。
- **文件**：`docs/localization-guidelines.md`、`AGENTS.md`、`README.md` 及必要的現行產品說明；歷史 change artifacts 保持原樣。
- **測試與驗收**：更新直接斷言舊文案的測試期望，執行相關測試及 `./gradlew build`，並檢查三語、淺／深色、窄屏、大字體和無障礙文案是否完整且未截斷。
- **兼容性**：不影響 Kotlin API、SQLite、`.bicroutes`、Citybus、DATA.GOV.HK、Google、cache、通知監控或 TTS，無外部接口、依賴或資料格式變更，亦無 breaking change。
