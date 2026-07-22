## 1. 回歸測試與領域契約

- [x] 1.1 為 `CitybusP2pStopMapResolver` 加入英文轉義撇號、字串內逗號／括號及反斜線的失敗回歸測試，並確認現有實作無法通過。
- [x] 1.2 為首程 ETA 加入有效空結果、stop map 請求／解析失敗、上車站缺失、ETA 請求失敗及無效回應的狀態分流測試，並確認現有單一 `Unavailable` 契約無法通過。
- [x] 1.3 為結果卡 formatter 和三語資源加入暫無車輛、候車暫不可用及簡體短載入文案測試，並確認現有文案無法通過。

## 2. P2P stop map 與英文預覽修復

- [x] 2.1 以引號、轉義及括號深度感知的逐字掃描方式擷取 `addstoponmap(...)` 調用，避免字串內容提前結束調用。
- [x] 2.2 更新函式參數切分與 JavaScript 字串還原，正確保留英文撇號、反斜線、逗號及括號，且維持既有 route variant／seq／stop id 映射。
- [x] 2.3 確認路線卡預覽及首程 ETA 復用修復後的同一 P2P stop map，不增加公開 route-stop 或跨語言 fallback。

## 3. ETA 狀態分流與三語展示

- [x] 3.1 擴充 `WaitTimeState`，加入成功空結果及帶結構化原因的技術不可用狀態，覆蓋缺少首程資料、stop map、上車站、ETA 請求及回應失敗。
- [x] 3.2 更新首程 ETA service，以有效 `data` 陣列和匹配結果區分 `NoArrivals` 與 `Unavailable(reason)`，並保留既有嚴格／降級匹配、最多三班及語言欄位策略。
- [x] 3.3 更新所有候車狀態消費端的 exhaustive 分支，保持排序、通知監控、候車面板及非可用顏色的既有語義。
- [x] 3.4 在繁體、簡體和英文資源中加入候車暫不可用文案，將簡體載入提示改為 `候车查询中`，並由 formatter 按領域狀態映射文案。

## 4. 文件、驗證與交付

- [x] 4.1 在 `docs/technical-debt.md` 記錄 TD-001 簡體 Citybus 站名限制、證據、暫緩原因、後續方案和驗收條件，並在 `AGENTS.md` 加入文件入口。
- [x] 4.2 運行 parser、ETA、預覽、formatter 和三語資源的針對性單元測試，確認新增回歸案例及既有案例全部通過。
- [x] 4.3 在可用網絡環境抽查包含英文轉義撇號站名的真實 `showstops2.php` 回應及 ETA stop id 鏈路，記錄無法完成的外部驗證風險。
  - 驗證記錄：2026-07-18 真實 8X 英文 `showstops2.php` 成功返回 HTTP 200，並包含 `Healthy Gardens, King\'s Road`、stop id `001277` 等可解析站點；`rt.data.gov.hk` ETA 端點兩次分別於 20 秒總超時及 10 秒連線超時，HTTP status 均未建立，因此真實 ETA 回應驗證受當時外部網絡可用性阻擋，stop id 到 ETA URL 的鏈路由回歸測試覆蓋。
- [x] 4.4 運行 `openspec validate fix-english-stop-preview-and-eta-status --strict`、`./gradlew build` 和 `git diff --check`，檢查工作區與提交範圍後自動建立 conventional commit。
  - 驗證記錄：strict OpenSpec 校驗、完整 Android build 和 diff whitespace 檢查均通過；debug APK 安裝到兩台模擬器，並在 1080×2400 模擬器完成繁體、簡體、英文切換及 crash buffer 檢查。
