---
name: capture-busiscoming-screenshots
description: "Use when planning, creating, recapturing, or validating screenshots of any current or future BusIsComing Android feature, page, dialog, bottom sheet, notification, lock screen, permission flow, loading state, empty state, or error state. Enforces an approved scene contract, synthetic location-safe data, isolated data injection, emulator ownership, deterministic capture, artifact validation, and complete cleanup."
---

# BusIsComing 功能截圖

## 目標

從 BusIsComing 的真實 App UI 生成安全、可重現、可驗收的任意功能／場景截圖。既有五張網站示例只可作技術案例，不是固定場景清單，也不是本 Skill 的輸出上限。

## 硬閘門：先確認場景，後執行

每次截圖任務開始時：

1. 先讀 `references/scene-contract.md` 及 `references/synthetic-data-policy.md`。
2. 只做唯讀調查：檢查 `git status --short`，閱讀相關 spec、docs、production UI、resources、model、repository 及測試。
3. 把使用者的功能意圖翻譯成一份完整場景契約，直接在會話中展示。
4. 要求使用者確認契約，然後結束目前回合。

在使用者確認具體場景契約前，不得建立檔案或暫存目錄，不得改動代碼、資料或設備，不得啟動模擬器，也不得開始構建。只有使用者剛剛明確確認了同一份完整契約，才可略過再次確認；籠統的歷史授權不算本次場景確認。

若使用者指定特殊資料、語言、主題、尺寸、設備或輸出數量，把它們視為本次契約覆寫，不修改 Skill 預設值。

## 固定預設

除非本次契約另有要求：

- 所有可見業務、地點、站點、路線、時間、ETA、版本與使用者資料均為合成資料。
- 不顯示真實／正式地名、站名、地址、地標、屋苑或可識別個人資料。
- 語言為香港繁體中文 `zh-Hant-HK`，淺色主題，`font_scale=1.0`，直向。
- 只截能完整表達功能的最少張數。
- App 頁面、dialog、bottom sheet 裁走系統狀態列與導航列；通知、鎖屏、系統權限畫面保留全屏。
- 最終產物放在系統暫存任務目錄，不放 repo、目前工作目錄或 Desktop。
- 只接受 production layout/component 組成的真實 UI；不得用 HTML、繪圖、image generation 或測試內手工拼裝近似畫面代替。

## 總工作流

### 1. 保護原倉庫並建立隔離副本

契約獲批後，先用 `snapshot-repository-state.sh` 把原倉庫指紋寫入一個系統暫存檔；這必須早於 source copy。再執行：

```bash
"$SKILL_DIR/scripts/create-isolated-workspace.sh" \
  --source "$REPO_ROOT" \
  --scene-slug "$SCENE_SLUG"
```

安全解析腳本輸出的 `TASK_ROOT`、`WORKSPACE`、`OUTPUT`、`MANIFEST` 四行；不要 `eval`。腳本會把目前 tracked、staged、unstaged 及 untracked 原始內容複製到系統暫存工作區，同時排除 `.git`、IDE、Gradle 及 build 產物。

把原倉庫 snapshot 移入 `TASK_ROOT` 後，立即用 `verify-repository-state.sh` 驗證一次。若 copy 期間倉庫指紋已改變，停止並重新確認 source 狀態；不要使用可能混合兩個時點內容的副本。後續構建、臨時 seam、fixture、instrumentation、App 安裝及截圖操作一律針對 `WORKSPACE` 或任務設備；原倉庫保持唯讀。

不要直接執行 `scripts/generate-demo-screenshots.sh`。它是舊的固定示例管線，會依賴非明確設備並寫 Desktop；其中手工組裝 UI、reflection 及固定五場景也不是通用截圖契約。

### 2. 把已批准契約落盤並校驗

在 `MANIFEST` 寫入會話中已確認的完整契約，然後執行：

```bash
python3 "$SKILL_DIR/scripts/validate-scene-manifest.py" \
  "$MANIFEST" --phase contract \
  --expect '01-scene-state.png'
```

所有預期 PNG 都要各自傳入一次 `--expect`。校驗不通過時先修正契約，不開始實作或設備操作。

### 3. 選擇資料注入路徑

讀 `references/data-injection-patterns.md`，按以下次序選擇最低侵入方案：

1. 真實 UI + 已有 repository/store/model，從正常 App 入口寫入合成狀態。
2. 已有 instrumentation fake、factory、runtime seam 或 launch args。
3. fixture／fake server，在 repository 邊界回傳結構化合成資料。
4. 只在隔離副本新增狹窄、可清理的 test-only seam 或 runner。
5. reflection 只作最後手段，並在 manifest 記錄原因與脆弱點。

必須走 production Activity/Fragment/dialog/bottom-sheet/notification renderer。若 production UI 尚不存在或無法可靠到達，停止並說明缺口；不要製造看似完成的替代 UI。

注入後關閉會把合成資料換回真實資料的網路路徑。等待可觀察的狀態／view／repository 完成條件，不用任意長度 sleep 猜測畫面已穩定。

### 4. 取得任務專屬設備

在任何設備操作前讀 `references/device-capture-validation.md`，並記錄任務開始時所有 `adb devices -l` 設備及其 AVD 名稱。

- 絕不操作、停止、重啟或重用任務開始前已運行的 AVD。
- 只啟動一個當時關閉且完整符合契約的 AVD；被佔用就等待，不以較低 API、錯誤螢幕、缺少 Google 能力或其他畫像降級。
- 優先使用 read-only／任務暫存 clone 等可丟棄資料層，避免把 App、設定、通知或測試資料留在持久 AVD。
- 啟動後解析新出現的唯一 serial；後續每個 `adb` 指令都必須帶 `-s "$TASK_SERIAL"`。
- 沒有符合條件且可由本任務擁有的設備時，停止裝置驗證並請求合適設備。

### 5. 構建、驅動真實場景並截圖

在 `WORKSPACE` 只構建／安裝所需 variant 及 instrumentation。用正常 UI 導航或批准的 test seam 到達場景，並在截圖前斷言關鍵可見狀態。

優先用 instrumentation `UiAutomation.takeScreenshot()`：

- App 畫面根據當前 window insets 計算 app-area bounds 後裁切。
- 通知、鎖屏、permission controller 等系統 UI 保存整張屏幕。
- PNG 使用契約中的穩定小寫檔名，直接拉取至 `OUTPUT`。
- `OUTPUT` 只放最終 PNG；debug 圖、log、APK、測試結果留在 `WORKSPACE`。

### 6. 雙層驗收

先做機器校驗：

```bash
python3 "$SKILL_DIR/scripts/validate-screenshot-output.py" \
  "$OUTPUT" \
  --expect '01-scene-state.png'
```

已知設備與裁切後尺寸時，用 `--expect 'name.png=WIDTHxHEIGHT'` 作精確比較。然後逐張視覺檢查：

- 是契約要求的真實功能、入口、狀態與互動結果。
- 語言、主題、font scale、方向、系統欄裁切模式正確。
- 所有動態內容一致，沒有真實地名／站名／地址、PII、debug 標記或意外通知。
- 沒有鍵盤遮擋、載入 spinner、Toast、截斷、重疊、空白地圖瓦片或錯誤動畫幀。
- 截圖數量既不缺少也不超出契約。

任何一項失敗都先修正和重截，不把「PNG 可打開」當成場景驗收完成。

### 7. 無論成功失敗都清理

把清理放入 `finally` 思維處理；build、test、pull 或驗收失敗都不能跳過：

1. 在任務設備取消通知、關閉 dialog/service、移除測試 App／資料；若使用可丟棄資料層，直接關閉後丟棄該層。
2. 僅關閉本任務啟動的 `TASK_SERIAL`，等待它從 `adb devices` 消失；若建立 temp AVD，只刪除精確命名的 temp AVD。
3. 刪除 `TASK_ROOT/workspace`，保留 `TASK_ROOT/output` 與 `TASK_ROOT/manifest.md`。
4. 再次執行 `verify-repository-state.sh`；只有指紋一致才把「原倉庫清理」改為 `passed`。
5. 只有任務設備已關閉且不再殘留資料，才把「模擬器清理」改為 `passed`。
6. 把任務狀態改成 `passed` 或 `failed`，補齊限制／失敗事實，執行 complete 校驗：

```bash
python3 "$SKILL_DIR/scripts/validate-scene-manifest.py" \
  "$MANIFEST" --phase complete \
  --expect '01-scene-state.png'
```

7. complete 校驗成功後刪除 repository snapshot，只保留使用者需要的 PNG 與 manifest。

清理不完整時不得聲稱任務完成；保留 manifest 與可診斷資訊，明確報告仍殘留的精確目標。禁止為了「恢復」而對原倉庫執行 `git reset --hard`、`git checkout --` 或刪除原有未跟蹤檔案。

## 交付

回覆時提供：

- 系統暫存 `TASK_ROOT` 的絕對路徑。
- 每張最終 PNG 的可點擊絕對路徑及簡短場景說明。
- `manifest.md` 的可點擊絕對路徑。
- 機器與視覺驗收結果、原倉庫指紋結果、任務設備已關閉結果。
- 未執行或無法證明的驗證，直接列為限制。

系統暫存檔可能被作業系統清理；提醒使用者確認後自行複製到最終發布位置。本 Skill 不自行覆蓋網站、Play 素材或 repo 內資產，除非使用者另外明確授權。
