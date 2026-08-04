# AGENTS.md

## 文件用途

本文件是 BusIsComing 的倉庫級 agent 操作規則。產品介紹與開發入口見 `README.md`；架構、算法和功能原理見 `docs/`；可觀察行為見生效 OpenSpec。不要把完整產品說明或單次 change 歷史複製到本文件。

## 進入倉庫後先做什麼

1. 檢查 `git status --short`，保留使用者或其他任務已有改動。
2. 閱讀與任務直接相關的 `openspec/specs/`、active change、測試及 `docs/` 主題文件。
3. 以目前代碼、資源、Manifest、Gradle 和測試確認已實現事實；以同步後的主 spec 確認應成立的對外契約。
4. 發現代碼、spec 與 docs 衝突時先定位原因，不靜默選一方或把衝突寫成已確認結論。

## 專案不變量

- BusIsComing 是香港巴士通勤 Android App，核心閉環是保存或臨時選擇起終點、比較 Citybus 點到點路線及首程 ETA，並可在出門前啟動短時通知欄監控。
- 生產路徑使用真實 Citybus mobile、DATA.GOV.HK Citybus ETA 和已配置的 Google 服務；mock 與 fixture 只用於測試及隔離驗證。
- UI 負責展示、輸入及生命週期協調；HTTP、SQLite、HTML／JSON 解析及長流程編排不得散落在 Activity、Fragment 或 Adapter。
- repository 封裝資料存取、解析與查詢；model／policy 表達結構化狀態；service 封裝監控、通知、排程及 TTS。
- 耗時工作不得阻塞主線程；並發查詢、刷新和背景工作必須處理取消、generation、過期 callback、部分失敗及生命週期。
- 本機資料、通知、背景服務與權限改動必須保護既有資料及使用者可控性，提供清楚的停止、失敗與恢復路徑。
- 外部接口與網頁結構可變；相關假設集中在 parser／repository，並保留可復現樣例、fixture、日誌或針對性測試。

## 代碼位置

```text
app/src/main/java/com/golink/busiscoming/
├── data/local          SQLite schema、行程／置頂 repository helper、偏好
├── data/localization   locale、provider mapping、TTS 語言、LanguageSnapshot
├── data/location       目前位置、距離、附近行程與 Google 地址
├── data/model          行程、路線、ETA、置頂、更新、監控等領域狀態
├── data/repository     Citybus／ETA HTTP、parser、cache、詳情與資料存取
├── data/transfer       .bicroutes codec、讀取、預覽與匯入計劃
├── data/update         Play／網站更新來源、策略、狀態與外部操作
├── service             前台監控、通知、調度、session、TTS、formatter
└── ui
    ├── common          共用輸入、結果控制、短文案與 WindowInsets
    ├── main            頂層頁、查詢結果、詳情、置頂、更新及快捷入口
    ├── edit/manage     行程新增、編輯及管理
    └── navigation/settings 頂層狀態及次級設定頁
```

構建、SDK 和依賴版本以 `app/build.gradle.kts`、根 Gradle 文件及 version catalog 為準。工程目前未顯式應用 `org.jetbrains.kotlin.android` 仍會建立 Kotlin 編譯任務；調整 Kotlin 配置前先構建確認，避免現有 `kotlin` 擴展衝突。

## 文件入口

- `README.md`：產品、功能、構建、技術與文件導航。
- `docs/architecture.md`：目前架構、資料流、存儲及生命週期。
- `docs/journey-query-workflow.md`：行程、地點、搜尋、結果、置頂與匯入匯出。
- `docs/citybus-route-query-and-eta.md`：Citybus 查詢、P2P 站點對齊、詳情與 ETA。
- `docs/monitoring-design.md`：通知欄監控算法與背景行為。
- `docs/ui-style-guide.md`：UI／UX 定位、設計原則、視覺語言、互動、動效與無障礙。
- `docs/localization-guidelines.md`：三語、術語、動態資料與 TTS。
- `docs/localization-validation-matrix.md`：三語、主題、尺寸與無障礙驗收。
- `docs/app-update-check.md`：Play／網站更新流程與發佈契約。
- `docs/transit-code-launcher.md`：桌面乘車碼入口及候選鏈。
- `docs/technical-debt.md`：主動延期問題及關閉條件。
- `docs/documentation-governance.md`：文件職責及 OpenSpec 歸檔同步。

`docs/implementation-plan.md`、`docs/overview-design.md`、`docs/specification.md` 是永久刪除項；不得恢復或改名照搬。除非使用者另行要求，文件治理及翻新不處理 `docs/superpowers/`。

## 文件、runtime 語言與術語

- 文件、OpenSpec 人類可讀內容、程式註解及中文測試名稱使用繁體中文；OpenSpec parser 所需英文結構關鍵字保留原樣。
- App 自有 UI、Toast、錯誤、通知、TTS、分享及無障礙文字必須同時提供香港繁體、獨立審校簡體與自然英文，不得在 XML 或 Kotlin 硬編碼。
- model／repository 回傳結構化狀態，UI／notification 層以目前 locale resource 格式化。
- 使用者自訂名稱、已保存／匯入地點、路線號及第三方原文保持不變，不機器翻譯或因語言切換改寫。
- 外部 API 樣例、fixture、第三方規格及 parser 原始標籤可保留原文。
- 新增動態資料源須定義三語 mapping、欄位或整體失敗回退、cache key、過期 callback 及真實／自動化驗證。

術語邊界：

- **行程**：使用者命名並保存的起點與終點配置；不保存或鎖定某條查詢結果。
- **路線**：按行程或臨時起終點查詢後返回的乘車方案。
- **乘車段**：一條路線中的單段巴士服務，只在詳情或換乘結構需要時使用。
- `RouteConfig`、resource key、SQLite `route_configs` 與 `.bicroutes` 是兼容保留的歷史名稱，不得據此把 runtime 行程文案寫成「路線」。

## 外部資料約束

- 地點、路線、P2P stop map、詳情、ETA 和 Google 地址的準確接口與失敗邊界以 `docs/citybus-route-query-and-eta.md` 及 `docs/localization-guidelines.md` 為準。
- `LanguageSnapshot` 是 Citybus、Google、DATA.GOV.HK、通知和 TTS 的一致語言來源；整體請求失敗不得改用另一語言重試。
- 修改 Citybus／ETA 參數、parser、route variant 或 stop id 對齊時，先區分上游原始回應、App 解析與 UI 展示，並補充可復現樣例和回歸測試。
- 生產 HTTP 不得被 fixture 或測試注入點替代。

## OpenSpec 工作流

- 新建 proposal、design、specs 與 tasks 的人類可讀內容使用繁體中文。
- `/opsx-propose` 生成完整 artifacts；需求未明確時先確認行為、UI、資料來源、錯誤處理及驗證方式。
- 使用者要求新 change 時建立獨立 change，不因內容重疊默默併入既有 change。
- `/opsx-apply` 按 `tasks.md` 實作並同步勾選；發現必要範圍擴張時先更新 artifacts。
- `/opsx-apply` 完成實作與驗證後自動提交；提交範圍不清或有明顯無關改動時先確認。

### 歸檔後文件同步

- 一個 change 也視為一個完整歸檔批次；歸檔後立即執行一次文件同步。
- 同一工作歸檔多個 changes 時，先全部完成 spec 同步及 archive，再對整批影響並集同步一次文件，不逐 change 重複檢查。
- 歸檔完成後必須使用專案 skill `openspec-archive-docs`；詳細治理標準唯一來源是 `docs/documentation-governance.md`。
- 不在每個 change 的 tasks 加入全倉文件翻新，不修改 OpenSpec 生成的 `openspec-archive-change` skill。
- 文件同步和驗證完成前，不得宣稱整個歸檔批次完整結束。

## 驗證與模擬器

Android 實現完成後原則上運行：

```bash
./gradlew build
```

窄改動可先跑定向 unit／instrumentation 測試；純文件或 OpenSpec 配置改動按風險運行鏈接、YAML、OpenSpec 和受影響功能測試，不需無關裝置驗證。

使用模擬器前先定義所需設備畫像，包括 API、螢幕尺寸／寬度、Google APIs 或 Play Store、方向、語言、主題、font scale 及其他硬條件。設備必須同時通過適配性和所有權兩個門檻：

- 只啟動目前未運行且符合畫像的 AVD，或繼續使用本任務此前自行啟動且仍符合畫像的 AVD。
- 不使用、停止、重啟或操作本任務開始前已運行的模擬器，即使它看似空閒或完全適配。
- 合適 AVD 被佔用時等待其釋放，再由本任務啟動；不得以不合適的 API、螢幕、Play 能力或其他設備降級代替。
- 有其他關閉且同等適配的 AVD 時可自行啟動；完全沒有合適 AVD 時停止裝置驗證並請求建立合適設備，不得宣稱以替代設備完成目標驗證。
- 驗證完成後主動關閉本任務啟動的全部模擬器。

## 開發與 Git

- 優先沿用既有 repository、service、formatter、policy 和測試注入點；保持改動窄而可審查，不順手重構或改動無關資料格式、排序及文案。
- 解析、格式化、排序、狀態判斷等邏輯盡量保持純粹且可單測；修復外部差異時加入針對性回歸。
- UI 改動遵循 UI 指南；多語言改動同時遵循本地化指南與驗收矩陣。
- 提交訊息使用簡潔英文 conventional commit；不提交構建產物，不回退自己未建立的改動。
- 提交前檢查：

```bash
git status --short
git diff --cached --stat
```
