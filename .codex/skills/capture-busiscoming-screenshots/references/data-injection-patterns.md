# BusIsComing 資料注入模式

## 核心原則

截圖要由 production Activity、Fragment、dialog、bottom sheet、adapter、notification formatter 或系統 UI 真實渲染。只替換「資料從哪裡來」或「如何穩定到達狀態」，不替換畫面本身。

所有臨時代碼只改系統暫存 `WORKSPACE`。不要修改原倉庫再嘗試 revert；不要以 Git destructive command 清理使用者改動。

## 選擇順序

### 1. 正常入口與既有 store/model

優先用 App 自己的 repository/helper 寫入合成資料，再經 production navigation 到達畫面。例如：

- `data/local` 的 SQLite helper、行程／置頂 repository 及 preference store。
- production launch args、Intent extra 或公開 UI action。
- `data/model` 的結構化 route、ETA、update、monitoring state。

這條路徑最接近真實使用，但必須在任務專屬資料層運行，不能讀寫使用者 AVD 或真實本機資料。

### 2. 既有 test factory 或 runtime seam

先搜索 `app/src/androidTest` 與 production runtime holder。現有案例包括：

- `SearchFragment.placeSearchRepositoryFactory` 及 `routeDetailRepositoryFactory`。
- `RouteDetailRuntime.repositoryFactory`、`etaResolver` 與 `RouteDetailLaunchArgs`。
- `AppUpdateRuntime`、`SharedPreferencesUpdateStateStore` 及 update model。
- 已有 visual-matrix tests 的 locale、theme、font scale 設定與 view assertion。

在 `try/finally` 中 install/reset 全域 seam，避免跨 test 泄漏。沿用結構化 repository/model 結果，不在 View 上直接塞最終文本。

### 3. Fixture 或 fake server

當狀態跨 HTTP/parser/cache 時，在 repository 的既有替換邊界使用 fixture 或本機 fake server：

- fixture 要保存完整語義，包含成功、部分失敗、空與錯誤狀態需要的欄位。
- fake server 綁定 loopback、使用隨機可用 port，測試結束關閉。
- 禁止 production HTTP 在截圖過程回退到 Citybus、DATA.GOV.HK、Google 或網站更新來源。
- 不得修改 production 預設 endpoint 令正式 App 永久指向 fixture。

### 4. 隔離副本內新增 test-only seam

只有前三級無法穩定表達場景時才新增。要求：

- seam 狹窄地放在資料／clock／permission／launcher 邊界，不在 UI 直接寫死畫面。
- 預設 production 行為完全不變；只有 instrumentation 明確 install 才生效。
- 使用 interface／lambda／provider，回傳既有 model 或 policy state。
- 補最小 assertion 證明 renderer 是 production 元件、狀態與契約一致。
- 在 manifest 列出新增檔案、選擇理由及清理方式。

### 5. Reflection 最後手段

Reflection 易受 private field/method 重命名和生命週期影響。只有無可用 seam、臨時新增 seam 又不合比例時才用，且要在操作前後斷言可觀察 UI。不可用 reflection 組裝 production 中不存在的畫面。

## 舊 demo 的使用邊界

`DemoScreenshotFixtures.kt`、`DemoScreenshotInstrumentedTest.kt` 和 visual-matrix tests 可參考以下技術：

- `UiAutomation.takeScreenshot()`、window-inset app-area crop。
- runtime seam install/reset、結構化 route/detail/ETA fixture。
- Espresso/view assertion、locale/theme/font scale 控制。

不可直接沿用以下行為：

- 固定五個輸出場景及固定 Desktop 目錄。
- 無 serial 的 `adb` 或自動接管任意已連線設備。
- 在測試內用 `LinearLayout`／`TextView` 手工拼一個近似 production bottom sheet。
- 未經契約審核直接顯示舊 fixture。
- 依賴 private `setField`／`invoke` 作常規方案。

## 穩定等待

按可觀察條件等待，並設明確 timeout：

- Espresso idling、view displayed／text／adapter item count。
- repository callback 完成、model state generation、notification posted。
- Fragment transaction、bottom-sheet state、map loaded callback。
- animation idle 後可加很短 settle，但不能只靠固定 sleep 判斷成功。

timeout 時保存 log／失敗原因到 `WORKSPACE`，先清理再報告；不要截載入中畫面冒充成功狀態。

## 驗證注入沒有越界

截圖前確認：

- production renderer class 與真實 navigation path 已被使用。
- 所有預期 network call 被 fake/disabled，沒有真實第三方 response 混入。
- 合成資料在 UI 各層一致，沒有真實 DB、preference、notification 或 account 殘留。
- global factory/runtime、clock、locale、theme 與 permission 改動都有對應 reset 或可丟棄資料層。
