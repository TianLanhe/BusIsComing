# AGENTS.md

## 文件用途

這個文件是 BusIsComing 專案的專案級長期記憶，用於告訴後續進入本倉庫的 AI coding agent：

- 這個專案是什麼。
- 當前 App 的背景和目標是什麼。
- 開發、驗證、OpenSpec 和提交時需要遵守哪些專案約定。

它應該隨倉庫一起提交到 git，因此屬於專案級規則，而不是單次會話記憶或用戶本機私有記憶。

## 專案目錄説明

當前目錄是 Android Studio 專案根目錄：

```text
BusIsComing/
├── app/                 Android App 模塊
├── docs/                產品設計、規格説明和實作計劃
├── gradle/              Gradle version catalog 和 wrapper 配置
├── openspec/            OpenSpec 變更提案、規格和任務
├── build.gradle.kts     根專案構建配置
├── settings.gradle.kts  Gradle settings
└── AGENTS.md            專案級 agent 説明
```

## App 背景和目標

BusIsComing 是一個用於查詢巴士到站資訊的 Android App。

第一版 MVP 的目標：

- 用戶可以本機管理常用路線配置。
- 每條路線配置包含路線名稱、起點地址和終點地址。
- 用戶可以在主界面選擇已儲存路線。
- 用戶點擊查詢後，App 展示從起點到終點的巴士路線結果。
- 查詢結果包含路線、港幣價格和預計車輛到站等候時間。
- 查詢結果支持按價格和預計等候時間排序。

當前階段使用本機 Mock 數據，不接真實 HTTP API。後續拿到真實 API 和呼叫方式後，再替換查詢倉庫實現。

## 當前技術棧

- 語言：Kotlin
- UI：XML + AppCompat + Material Components
- 本機存儲：SQLiteOpenHelper
- 清單和表格：RecyclerView
- 架構風格：輕量 Repository 分層
- 構建：Gradle Kotlin DSL

當前工程使用 Android Gradle Plugin 9.2.1。該工程在未顯式應用 `org.jetbrains.kotlin.android` 插件時已經生成並執行 Kotlin 編譯任務。不要盲目重複應用 Kotlin Android 插件；如果需要調整 Kotlin 配置，先運行構建確認不會與現有 `kotlin` 擴展衝突。

## 重要文檔

優先閲讀以下文檔理解專案：

- `docs/overview-design.md`：概要設計
- `docs/specification.md`：規格説明
- `docs/implementation-plan.md`：實作計劃
- `docs/ui-style-guide.md`：UI 風格、視覺層級和互動動效指南
- `openspec/changes/`：OpenSpec 變更提案、設計、規格和任務

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

如果需要真機或模擬器驗證，但當前沒有設備連接，應明確説明未完成手動視覺驗證。

檢查設備：

```bash
adb devices
```

## 開發注意事項

- 不要把 Mock 巴士數據寫死在 Activity 中，應通過 `BusRouteRepository` 提供。
- 本機路線配置通過 `RouteConfigRepository` 存取，不要讓 UI 直接操作 SQLite。
- 起點和終點目前只是純文本地址，不接地圖、不做地址聯想。
- 價格固定為港幣，展示格式為 `HK$金額`。
- 預計等候時間表示車輛到站時間，展示格式為 `約 N 分鐘`。
- 刪除路線配置需要二次確認。
- 主界面無路線配置時需要展示空狀態和新增入口。
- 查詢無結果時顯示 `暫無可用巴士路線`。
- 新增或改造頁面時預設遵循 `docs/ui-style-guide.md`，採用安靜實用的現代通勤工具風格。
- 查詢結果預設優先使用路線結果卡片；如果繼續使用表格，需要在 OpenSpec 設計中説明原因。
- 起點和終點交換預設使用圖示按鈕，不使用整行文字按鈕。

## Git 提交約定

- 保持提交粒度清晰。
- 提交資訊使用簡潔英文 conventional commit 風格，例如：
  - `feat: implement bus query MVP`
  - `fix: remove app title bar`
  - `docs: add project agent notes`
- 不要提交構建產物。
- 提交前檢查：

```bash
git status --short
git diff --cached --stat
```
