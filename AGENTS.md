# AGENTS.md

## 文件用途

這個文件是 BusIsComing 專案的專案級長期記憶，用於告訴後續進入本倉庫的 AI coding agent：

- 這個專案是什麼。
- 當前 App 的功能、架構和外部資料來源是什麼。
- 開發、驗證、OpenSpec 和提交時需要遵守哪些專案約定。

它應該隨倉庫一起提交到 git，因此屬於專案級規則，而不是單次會話記憶或用戶本機私有記憶。

## 專案目錄説明

當前目錄是 Android Studio 專案根目錄：

```text
BusIsComing/
├── app/                 Android App 模組
│   └── src/
│       ├── main/        App 代碼、資源與 AndroidManifest
│       ├── test/        本地單元測試與 Citybus fixture
│       └── androidTest/ Android instrumentation 測試
├── docs/                產品設計、資料推導、UI 風格與規格説明
├── gradle/              Gradle version catalog 和 wrapper 配置
├── openspec/            OpenSpec 規格、變更提案和任務
├── build.gradle.kts     根專案構建配置
├── settings.gradle.kts  Gradle settings
├── README.md            專案介紹
└── AGENTS.md            專案級 agent 説明
```

## App 背景和當前能力

BusIsComing 是一個面向香港巴士通勤場景的 Android App。它以「常用路線快速查詢」為核心，讓用戶保存常用起終點，快速比較 Citybus 點到點路線、候車時間、票價、步行距離與路線詳情，並可在出門前啟動短時通知欄監控。

專案已接入真實 Citybus mobile 站點與 DATA.GOV.HK 城巴公開 ETA API。測試中的 fixture 僅用於解析和回歸測試，不應替代正常 App 的 HTTP 呼叫邏輯。

當前主要功能：

- 常用路線管理：新增、編輯、複製與刪除常用起終點配置。
- Citybus 地點搜尋：新增路線和臨時查詢時，起點與終點都可從 Citybus 候選地點中選擇。
- 主頁快捷查詢：常用路線以快捷卡展示，支援完整列表與臨時查詢入口。
- 臨時查詢：不保存路線也能查詢，查詢後可一鍵保存為常用路線。
- 路線結果卡片：展示路線、上落車站預覽、HK$ 票價、耗時、步行距離與候車狀態。
- 多班 ETA：卡片突出首程「下一班」候車時間，並可查看最多 3 班到站時間。
- 路線詳情：點擊路線卡片可查看上下車站點、途經站點與換乘段。
- 結果排序與刷新：支援按路線、價格、耗時、候車和步行距離排序，並支援結果下拉刷新。
- 通知欄監控：從可監控路線啟動短時前台服務，定期刷新首程 ETA，提供刷新、停止、語音提醒與出門狀態提示。

## 當前技術棧

- 語言：Kotlin
- UI：XML + AppCompat + Material Components
- 清單與刷新：RecyclerView + SwipeRefreshLayout
- 本機存儲：SQLiteOpenHelper
- HTML/API 解析：jsoup + 輕量 JSON 欄位解析
- 通知監控：Foreground Service + NotificationCompat + AlarmManager 調度輔助 + TextToSpeech
- 架構風格：輕量 Repository 分層
- 構建：Gradle Kotlin DSL
- 測試：JUnit、AndroidX Test、Espresso、Citybus HTML fixture

當前工程使用 Android Gradle Plugin 9.2.1。該工程在未顯式應用 `org.jetbrains.kotlin.android` 插件時已經生成並執行 Kotlin 編譯任務。不要盲目重複應用 Kotlin Android 插件；如果需要調整 Kotlin 配置，先運行構建確認不會與現有 `kotlin` 擴展衝突。

## 外部資料來源

App 主要基於 Citybus mobile 站點與 DATA.GOV.HK 城巴公開 ETA API：

- `ppsearch_p3.php`：點到點路線候選結果。
- `showstops2.php`：基於 P2P rawInfo 取得 route variant 對齊的停站與 stop id，避免公開路線站序與 P2P 結果不一致。
- `getp2pstopinroute.php`：路線詳情與途經站點。
- `rt.data.gov.hk/v2/transport/citybus/eta/...`：首程即時到站 ETA。

外部 API 可用性和返回格式會直接影響查詢結果。修改解析器或 API 參數時，應優先保留可復現的原始樣例、fixture 或 cURL 等價資訊，並通過測試覆蓋已知路線案例。

## 代碼分層

```text
data/local        SQLite schema 與本地資料庫 helper
data/model        路線、地點、ETA、通知監控與排序模型
data/repository   Citybus 查詢、解析、路線配置和詳情資料存取
service           通知欄監控、刷新調度、TTS 和 session 持久化
ui/common         共用輸入和 WindowInsets 工具
ui/edit           路線新增與編輯
ui/manage         路線管理
ui/main           主頁查詢、結果卡片、底部彈層與監控入口
```

典型查詢流程：

```text
MainActivity
-> CitybusBusRouteRepository
-> ppsearch_p3.php
-> CitybusRouteParser
-> 先展示基礎路線結果
-> 後台補齊 showstops2 / ETA / 站點預覽
-> RecyclerView 增量刷新
```

## 重要文檔

優先閲讀以下文檔理解專案：

- `README.md`：專案功能、技術棧和資料來源總覽。
- `docs/overview-design.md`：概要設計。
- `docs/specification.md`：規格説明。
- `docs/implementation-plan.md`：實作計劃。
- `docs/ui-style-guide.md`：UI 風格、視覺層級和互動動效指南。
- `docs/citybus-eta-from-ppsearch.md`：基於 Citybus P2P 結果推導首程 ETA 的方案記錄。
- `openspec/changes/`：OpenSpec 變更提案、設計、規格和任務。
- `openspec/specs/`：已歸檔或當前生效的能力規格。

## OpenSpec 約定

- OpenSpec 生成的人類可讀文件使用繁體中文。
- 為了讓 OpenSpec 解析通過，spec 文件中必要結構關鍵字可以保留英文，例如：
  - `## ADDED Requirements`
  - `### Requirement:`
  - `#### Scenario:`
- 每次 `/opsx-propose` 需要生成完整 artifacts：
  - `proposal.md`
  - `design.md`
  - `specs/**/*.md`
  - `tasks.md`
- 每次 `/opsx-apply` 或 `/opsx:apply` 應按對應 change 的 `tasks.md` 執行，完成後更新任務勾選狀態。
- 如果用戶要求「新開 change」，即使相關文件有重疊，也應建立新的 OpenSpec change，不要默默合併到既有 change。
- 對需求尚未明確的功能，先和用戶討論到行為、UI、資料來源、錯誤處理和驗證方式明確，再寫 propose。

## 中文文案約定

- 後續新增或修改 App 文案、測試期望、文件和 OpenSpec 人類可讀內容時，中文一律使用繁體中文。
- 外部 API 回傳樣例、Citybus 原始 fixture 或第三方規格原文可保留原始文字，不為繁體化而改寫來源內容。

## opsx-apply 後提交規則

遷移自本機記憶：

```text
Preference: After every opsx-apply completes changes, run git commit to submit the changes automatically after verification. Ask only if commit message or scope is ambiguous.
```

專案級規則：

- 每次 `/opsx-apply` 或 `/opsx:apply` 完成實現和驗證後，自動執行 `git commit` 提交本次改動。
- 如果用戶沒有指定 commit message，基於變更內容生成簡潔提交資訊。
- 如果提交範圍不清晰、工作區存在明顯無關改動，先向用戶確認。
- 提交前優先運行必要驗證；Android 實現改動通常運行：

```bash
./gradlew build
```

## 驗證約定

代碼實現完成後優先執行：

```bash
./gradlew build
```

這會覆蓋：

- Kotlin 編譯
- unit tests
- lint
- debug/release assemble

針對較窄修改，可先運行相關測試或 `./gradlew testDebugUnitTest`，但最終交付 Android 實現前應盡量跑完整 `./gradlew build`。

如果需要真機或模擬器驗證，但當前沒有設備連接，應明確説明未完成手動視覺驗證。

檢查設備：

```bash
adb devices
```

## 開發注意事項

- 把 `README.md`、`docs/`、`openspec/` 和測試視為需求與行為來源。功能級細節應落在對應 OpenSpec、設計文檔或測試中，不要塞進本文件。
- 保持分層邊界清晰：UI 層負責展示與互動，repository 層負責資料存取、網路呼叫和解析編排，model/service 層封裝領域狀態與背景能力。
- 不要在 Activity、Adapter 或 Fragment 中直接散落 SQLite、HTTP、HTML/JSON 解析或長流程業務邏輯；優先沿用既有 repository、service 和 formatter。
- 外部 API 與網頁結構都可能變動。涉及 Citybus 或 DATA.GOV.HK 的假設應封裝在 parser/repository 中，並保留可復現的樣例、fixture、日誌或文檔線索。
- 生產路徑應使用真實資料來源；mock、fixture 和測試注入點只用於測試、回歸或明確隔離的驗證場景。
- 優先維持可測試性：解析、格式化、排序、狀態判斷等邏輯盡量保持純粹且可單測；修復外部資料差異時補充針對性回歸測試。
- UI 改動遵循 `docs/ui-style-guide.md` 和既有組件風格，注意文字截斷、間距、觸控目標、無障礙和不同螢幕尺寸下的穩定性。
- 涉及耗時查詢、並發刷新或背景任務時，避免阻塞主線程，處理取消、過期結果、失敗狀態和 Android 生命週期限制。
- 涉及本機資料、通知、背景服務或權限時，優先保護用戶已有資料和可控性，提供清晰的停止、失敗與恢復路徑。
- 保持改動範圍窄而可審查。除非需求明確要求，避免順手重構、改動無關文案、調整既有資料格式或重排行為。

## Git 提交約定

- 保持提交粒度清晰。
- 提交資訊使用簡潔英文 conventional commit 風格，例如：
  - `feat: implement bus query MVP`
  - `fix: remove app title bar`
  - `docs: add project agent notes`
- 不要提交構建產物。
- 工作區可能已有用戶或其他任務留下的改動；不要回退自己未創建的改動。
- 提交前檢查：

```bash
git status --short
git diff --cached --stat
```
