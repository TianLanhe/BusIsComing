# 文件治理與 OpenSpec 歸檔同步

## 目的

本文件定義 BusIsComing 長期文件的職責、事實來源及維護方式。目標是在一個或多個 OpenSpec change 歸檔、行為已固定後，以該批 change 的影響並集集中翻新文件，使 README、AGENTS、`docs/` 與 OpenSpec 上下文持續反映目前代碼，而不是累積歷史描述。

本契約不要求在 proposal、apply 或每個 change 的 `tasks.md` 中執行全倉文件檢查。單個 change 也視為一個歸檔批次，完成歸檔後執行一次文件同步；多個 change 在同一批次歸檔時，只在全部歸檔完成後同步一次。

## 管理範圍

批次同步可檢查及修改：

- `README.md`
- `AGENTS.md`
- `docs/*.md`
- `openspec/config.yaml`，但只限其穩定專案上下文與 artifact 規則

下列內容不屬於長期文件同步範圍：

- `docs/superpowers/`
- OpenSpec archive 中保存的 proposal、design、specs、tasks 與驗證記錄
- 一次性調試日誌、截圖、構建產物或本機私有記憶

需要補足知識時，應按本文件的責任邊界重新建立主題明確的文件。

## 事實來源與衝突處理

不同內容使用不同權威來源：

| 內容 | 主要事實來源 |
| --- | --- |
| 已實現的 runtime 行為 | 目前代碼、資源、Manifest、構建配置及測試 |
| 應成立的使用者可觀察契約 | 已同步的 `openspec/specs/` |
| 某次變更的背景、取捨與驗證 | 對應 OpenSpec archive |
| 架構、算法、外部資料流與長期原則 | 對應 `docs/` 主題文件 |
| 專案介紹、公開能力與開發入口 | `README.md` |
| agent 工作方式與倉庫級約束 | `AGENTS.md` |
| OpenSpec artifact 的寫作約束 | `openspec/config.yaml` |

不得以「代碼永遠優先」掩蓋規格缺失，也不得以舊 spec 否定已由其他 change 固定的實現。若目前代碼與已生效 spec 衝突，先判斷是實現遺漏、delta spec 未同步，還是後續 change 已取代舊行為；不能裁決時，明確報告衝突，不把其中任一方寫成已確認事實。

外部服務、商店狀態或真實裝置結果若無當前證據，須標明日期、版本及限制，不得由 fixture、mock 或歷史成功記錄推導為目前仍然成立。

## 文件職責

| 文件 | 負責 | 不負責 |
| --- | --- | --- |
| `README.md` | 產品定位、目前主要能力、快速開始、技術與私隱摘要、文件導航 | 完整 API 參數、agent 流程、全部邊界場景 |
| `AGENTS.md` | 事實查核入口、倉庫不變量、語言與術語、開發驗證、OpenSpec 與 Git 工作流 | 產品說明書、每項功能的完整設計 |
| `openspec/config.yaml` | 穩定專案背景及 proposal/specs/design/tasks 的專屬規則 | 完整目錄、易變版本、詳細接口、archive 執行步驟 |
| `docs/architecture.md` | 目前畫面與模組邊界、主要資料流、狀態存儲及並發生命週期 | 完整產品場景或第三方接口字段 |
| `docs/journey-query-workflow.md` | 行程、地點選擇、臨時查詢、結果操作、置頂與資料遷移工作流 | Citybus HTML／ETA 解析細節 |
| `docs/citybus-route-query-and-eta.md` | Citybus 查詢、P2P 站點對齊、路線詳情與 ETA 技術鏈 | 通用 UI 風格及產品導航 |
| `docs/monitoring-design.md` | 步行估算、監控狀態、調度、通知、TTS 與 session | 一般路線查詢 UI |
| `docs/ui-style-guide.md` | 全 App UI／UX 定位、設計原則、視覺與互動模式、排版、動效及無障礙 | 每頁的完整產品 requirements、精確元件契約或單次改版方案 |
| `docs/localization-guidelines.md` | 三語、術語、動態資料與 TTS 語言原則 | 功能業務邏輯及接口實作全文 |
| `docs/localization-validation-matrix.md` | 可重複執行的三語、主題、尺寸及無障礙驗收條件 | 歷史執行日誌 |
| `docs/app-update-check.md` | 更新檢查、渠道、提醒、網站契約及發佈鏈 | 通用設定頁說明 |
| `docs/transit-code-launcher.md` | 目前乘車碼入口、候選鏈及桌面兼容行為 | 已移除實驗的完整歷史 |
| `docs/technical-debt.md` | 已確認且主動延期、具有影響和關閉條件的問題 | 普通 TODO、願望清單或已完成 change 摘要 |

新增、刪除或重新劃分長期文件時，必須同步更新本表、README 文件導航與 AGENTS 的重要文件索引。

## 批次歸檔同步契約

### 觸發

- 使用者要求歸檔一個 change 時，該 change 自成一個批次，歸檔後立即同步一次文件。
- 使用者在同一工作中歸檔多個 changes 時，先逐個完成狀態檢查、delta spec 同步及 archive，最後針對整批同步一次文件。
- `/opsx-apply`、一般提交或尚未歸檔的 change 不觸發本流程。
- 不在每個 change 的 `tasks.md` 注入全倉文件翻新任務。
- 不修改 OpenSpec 自動生成的 `openspec-archive-change` skill；執行入口是專案自有 `openspec-archive-docs` skill 及 AGENTS 約束。

### 批次輸入

對本批每個 change 收集：

- proposal、design、delta specs 與 tasks
- 同步後的主 specs
- 實際修改的代碼、資源、Manifest、構建配置及測試
- 已完成、未完成或受環境限制的驗證
- 外部接口、裝置、資料格式及相容性證據

將各 change 的影響合併為一個集合，不按 change 向長期文件逐段追加歷史。

### 影響分類

至少判斷：

| 變化 | 應檢查的文件 |
| --- | --- |
| 產品定位、主要使用者能力、安裝或私隱摘要 | README、對應主題文件 |
| 模組邊界、資料流、並發、生命週期、持久化 | `architecture.md` |
| 行程、搜尋、結果、置頂、匯入匯出 | `journey-query-workflow.md` |
| Citybus、Google、DATA.GOV.HK、parser、cache、ETA | 查詢／ETA 文件、本地化指南 |
| 監控、通知、排程、語音、權限 | `monitoring-design.md` |
| UI、互動、動效、尺寸、無障礙 | UI 指南、驗收矩陣 |
| 三語術語、動態文案、回退、TTS | 本地化指南、驗收矩陣 |
| 更新渠道或發佈鏈 | 更新檢查文件 |
| 延期問題新增、狀態改變或關閉 | 技術債清單 |
| agent 或 OpenSpec 工作方式 | AGENTS；artifact 規則改變時才更新 config |

### 深度與廣度

對本批受影響領域做深度核查：閱讀相關實現與測試，逐項校正文檔。另做一次全局輕量檢查：

- README 的功能與文件索引是否有效
- AGENTS 是否引用已刪除文件、錯誤路徑或失效規則
- docs 內部鏈接、文件名、版本與術語是否互相矛盾
- config 是否複製了應由主題文件承載的易變細節
- 技術債是否仍有影響、延期決策及可驗收關閉條件

全局輕量檢查不是每次重讀全部代碼；不得以此為由把批次同步降級成只改鏈接而不查受影響實現。

## 翻新方式

- 以目前事實重寫、刪減或合併舊內容，不只在文件尾部追加「目前已不同」。
- 同一規則只保留一個完整權威來源；其他文件寫摘要並鏈接。
- 不把 OpenSpec requirements 原樣複製到 docs，也不把 archive 當目前行為說明。
- 已失效的功能、實驗、計劃與一次性驗證記錄應刪除；Git 與 archive 已保存歷史。
- 只有安全、發佈、兼容或外部契約追溯仍有操作價值時才保留歷史證據，並標明日期、版本及不能證明的範圍。
- 無法從當前代碼或證據確認的內容不得寫成結論；必要時記入技術債或未完成驗證。

## 新建核心原則文件的門檻

只有同時滿足以下條件才新增長期文件：

1. 內容預期跨多個版本有效。
2. 屬於核心工作流、核心算法、外部資料契約、持久化、安全私隱或全 App 統一原則。
3. 現有文件沒有自然且不造成職責膨脹的承載位置。
4. 不能只靠某個 archive 讓後續開發者理解及安全修改。
5. 有明確的代碼、測試或外部契約作事實來源。
6. 可形成職責單一的主題，不是某次 change 的實作總結。
7. 不會與現有文件形成並列權威來源。

不滿足門檻時，更新現有文件或只保留在 OpenSpec archive。

## 合併與刪除條件

符合任一條件時應考慮合併或刪除：

- 對應功能已移除，且沒有持續操作價值。
- 內容已由更準確的主題文件完整吸收。
- 文件只剩過期實驗、歷史計劃或一次性驗證。
- 文件名與實際主題嚴重不符。
- 同一事實存在兩個完整權威來源。

刪除或替換後，必須清理 README、AGENTS、config、docs 及測試註解中的舊引用。

## 驗證與完成標準

按改動範圍執行：

- 搜尋舊文件名、失效鏈接、舊術語、過期版本與重複表述
- 重新對照關鍵代碼、資源、Manifest、構建配置及測試
- 驗證 YAML 及 skill frontmatter
- 運行 `openspec validate --all --strict`
- 運行受影響功能的測試；純文件改動不要求無關的 Android 裝置驗證
- 執行 `git diff --check` 並檢查工作區範圍

若存在未解決的事實衝突、失效引用或未聲明的驗證缺口，整個批次的「歸檔與文件同步」不得宣稱完整完成。已移動到 archive 的 change 不因此自動回退。

## 完成摘要

每次同步輸出：

```text
Documentation reconciliation

Batch:
- Archived changes:
- Synced capabilities:

Impact:
- User capabilities:
- Architecture and data:
- External contracts:
- UI and localization:
- Technical debt:

Documentation:
- Inspected:
- Updated:
- Added:
- Deleted:
- No-change reasons:

Validation:
- Code and test evidence:
- Link checks:
- OpenSpec validation:
- Unverified or incomplete items:
```

`No-change reasons` 不得只寫 `none`；若某類文件無需修改，應說明本批為何沒有對其長期內容造成影響。
