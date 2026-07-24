# 檢查更新 Dialog UI 優化設計

日期：2026-07-25
狀態：用戶已確認設計，尚未實作

## 背景

目前檢查更新 Dialog 已具備完整業務行為，但自訂內容只包含一行訊息及三個全寬 TextButton。三個按鈕垂直排列於右側，令 Dialog 留下大量空白，操作層級亦不明顯；整體看起來像把系統 Dialog 與設定清單臨時拼接，未能體現 BusIsComing「安靜實用的現代通勤工具」風格。

本設計只優化更新提示的視覺層級、按鈕排列及響應式表現。更新檢查、3 天提醒、略過版本、Google Play／網站分流、錯誤處理與不可取消規則全部保持不變。

## 目標

- 常規手機寬度及字體下，三個操作橫向排列，消除現有大片空白。
- 明確表達標題、版本、說明與操作的資訊層級。
- 溫和突出「前往更新」，但不造成強制更新或營銷彈窗的壓迫感。
- 保持香港繁體、簡體中文與英文在深淺色、360dp 及 font scale 1.0／1.3／2.0 下完整可用。
- 保留至少 48dp 觸控目標、TalkBack 可理解性與返回鍵／外部不可取消規則。
- 讓橫排／縱排判斷成為可獨立測試的 UI policy，不把尺寸判斷散落在業務流程中。

## 非目標

- 不修改更新檢查、節流、提醒、defer、skip 或本地狀態。
- 不修改 Google Play flexible update、Play 詳情頁或網站下載頁行為。
- 不加入發佈說明、下載大小、渠道名稱、倒數計時、強制更新或新業務狀態。
- 不改設定頁「檢查更新」摘要、小紅點或下載完成 Snackbar。
- 不引入 Compose、新依賴、插畫、動畫資產或遠端素材。
- 不順帶重構 `MainActivity` 其他更新流程。

## 方案比較

### 方案 A：緊湊純文字

標題、獨立版本行及簡短說明由上至下排列，操作區直接置於內容下方。沒有圖標、分隔線或內嵌卡片。

優點是資訊直接、佔用空間少、最符合既有安靜實用定位；缺點是視覺記憶點較弱。此方案獲採用。

### 方案 B：圖標標題組

在標題左側加入 tonal update 圖標容器，版本置於標題下方。辨識度較高，但對只有一個明確狀態的提示增加了非必要裝飾，因此未採用。

### 方案 C：分區狀態卡

把版本放入獨立 tinted panel，操作區以分隔線劃分。版本最突出，但只有單一版本欄位，分區顯得偏重並增加 Dialog 高度，因此未採用。

## 已確認視覺設計

### Dialog 容器

- 使用 Material Dialog surface，圓角為 16dp。
- 背景使用目前主題的 surface／`bus_card_surface` 語意色，深色模式沿用 `values-night`。
- 不加入圖標、插畫、分隔線、內嵌卡片或額外陰影層。
- 內容水平 padding 與頂部 padding 均為 24dp；操作區使用 12dp 水平 padding。
- Dialog 寬度沿用 Material 的安全螢幕邊距，不以固定像素寬度覆蓋不同裝置。

### 資訊層級

內容固定按以下順序排列：

1. 標題「發現新版本」：20sp，主要文字色，使用 Material title text appearance。
2. 動態版本「版本 1.0」：14sp，主色／強調色，使用 medium 字重。
3. 說明：14sp，次要文字色，允許自然換行。
4. 操作列。

標題與版本相距 4dp，版本與說明相距 12dp。版本從現有長句中抽離，讓用戶能先辨識「有更新」及目標版本，再閱讀輔助說明。

### 三語文案

| 語意 | 香港繁體 | 簡體中文 | English |
| --- | --- | --- | --- |
| 標題 | 發現新版本 | 发现新版本 | New version available |
| 版本 | 版本 `%1$s` | 版本 `%1$s` | Version `%1$s` |
| 說明 | 新版本已可下載。你可以現在更新，或稍後再處理。 | 新版本已可下载。你可以现在更新，或稍后再处理。 | A new version is ready to download. You can update now or come back to it later. |
| 左側操作 | 稍後提醒 | 稍后提醒 | Remind me later |
| 中間操作 | 略過此版本 | 跳过此版本 | Skip this version |
| 右側操作 | 前往更新 | 前往更新 | Update now |

所有 App 自有文字由三語 string resource 提供。版本名稱保持服務端或 Google Play 原文，不作翻譯。

## 操作列

### 順序與層級

常規橫排由左至右固定為：

```text
稍後提醒 ｜ 略過此版本 ｜ 前往更新
```

- 「稍後提醒」使用主色 TextButton，表示可逆的次要操作。
- 「略過此版本」使用次要文字色 TextButton，降低永久抑制同版本自動提醒的視覺權重。
- 「前往更新」使用低飽和 tonal 背景、主色文字及 8dp 圓角，作為推薦主操作；不使用高飽和 filled button，避免產生強制更新感。
- 三個按鈕常規橫排時使用等寬欄位及一致視覺高度，最小高度 48dp、`minWidth=0`，文字置中並允許換行。
- 按鈕短文案使用 `letterSpacing=0`，不使用全大寫、單行裁切或省略號。

### 響應式排列

佈局模式由獨立純 UI policy `UpdatePromptLayoutPolicy` 決定：

```text
screenWidthDp < 360 或 fontScale >= 2.0 → VERTICAL
其他情況 → HORIZONTAL
```

- `HORIZONTAL`：三個操作等寬橫排；英文在 font scale 1.0／1.3 可自然換行，但不得省略或重疊。
- `VERTICAL`：三個操作改為全寬縱排，由上至下仍為「稍後提醒、略過此版本、前往更新」，視覺主次不變。
- 語言、主題、字體或 Activity configuration 改變後，重建 Dialog 時重新計算模式。
- 若極端螢幕高度令內容無法完整呈現，Dialog 內容必須可達，不得讓操作落在視窗之外。

## 互動與資料流

Dialog 只消費既有可靠 `AppUpdateState`：

```text
MainActivity.handleAppUpdateState
→ 確認有較高版本、沒有失敗、policy 允許提醒、Activity 可展示
→ showUpdatePrompt
→ 綁定版本與三語文案
→ UpdatePromptLayoutPolicy 選擇橫排或縱排
→ 用戶明確選擇三個操作之一
```

三個操作維持現有語義：

- **稍後提醒**：為目前 versionCode 寫入 3 天 defer，然後關閉 Dialog。
- **略過此版本**：保存目前 skipped versionCode，然後關閉 Dialog。
- **前往更新**：先為目前 versionCode 寫入 3 天 defer，關閉 Dialog，再啟動 Play 或網站更新操作。

Dialog 保持 `setCancelable(false)` 及 `setCanceledOnTouchOutside(false)`。返回鍵與外部點擊不產生隱式「稍後」或「略過」；焦點及 TalkBack traversal 跟隨畫面順序。Play／網站無法開啟時沿用既有三語錯誤提示。

## 組件與實作邊界

- `dialog_app_update.xml` 負責完整 Dialog 視覺，包括標題、版本、說明、操作容器及三個按鈕。
- `MainActivity.showUpdatePrompt` 繼續負責綁定版本、註冊三個 callback 及控制 Dialog 生命週期；不再透過 Builder 的系統 `setTitle` 建立另一套標題間距。
- `UpdatePromptLayoutPolicy` 只接收目前視窗的 `screenWidthDp` 與 `fontScale`，回傳 `HORIZONTAL` 或 `VERTICAL`；它不依賴 Activity、更新狀態或 Android 網絡／Play 類型。
- 更新 coordinator、state store、policy、source 及 external action 不因本次 UI 優化而修改。
- 不加入裝飾動畫；按鈕保留 Material ripple／pressed／focus 回饋即可。

## 無障礙與本地化

- 三個操作的觸控高度至少 48dp；橫排時每個操作有獨立、完整的可點擊區域。
- 標題、版本、說明和按鈕均允許 Android font scale 生效，不以縮字容納內容。
- TalkBack 依序朗讀標題、版本、說明、稍後、略過及更新；按鈕可見文字本身提供完整操作語意，不另加重複 `contentDescription`。
- 深淺色使用 `bus_*`／Material 語意 token，主要文字對比至少 4.5:1，按鈕邊界、focus 與必要圖形至少 3:1。
- 橫排按鈕不得因中文短文案被字符間兩端拉伸；英文可換行但不得省略。

## 驗證

### 純邏輯與資源測試

- `UpdatePromptLayoutPolicy` 覆蓋 `359dp / 360dp` 與 `fontScale 1.99 / 2.0` 邊界。
- 三語資源具有相同 key、placeholder 索引與類型。
- XML／source contract 確認標題、版本及說明分層，按鈕 DOM／View 順序為稍後、略過、更新。
- 確認稍後為主色 text、略過為次要 text、更新為 tonal，三者最小高度均為 48dp。

### Instrumentation 與視覺矩陣

- 香港繁體、簡體中文、英文 × 淺色、深色 × 360dp × font scale 1.0／1.3／2.0。
- font scale 1.0／1.3 斷言三個操作橫排；2.0 斷言三個操作縱排。
- 標題、版本、說明及按鈕必須完整顯示，無省略、重疊、錯誤留白或不可達操作。
- 保留返回鍵／外部不可取消、Activity recreation 後恢復、稍後 defer、略過版本及前往更新 callback 回歸測試。
- 深淺色截圖確認幾何一致、tonal 更新按鈕層級清楚且不過度突出。
- 最終執行 `./gradlew build`。

如需模擬器驗證，只使用驗證開始前未啟動的模擬器；若合適模擬器正被其他人使用則等待，完成後關閉本次啟動的模擬器。

## 實作範圍

### 包含

- 重排 `dialog_app_update.xml`，加入自訂標題與獨立版本文字。
- 新增或調整 Dialog 專用按鈕／文字 style 及必要語意色引用。
- 調整 `MainActivity.showUpdatePrompt` 的 View 綁定與響應式方向設定。
- 新增三語版本行及說明資源。
- 新增 layout policy、資源契約、instrumentation 與視覺矩陣測試。

### 不包含

- 不修改更新檢查資料來源、metadata、版本比較、24 小時／3 天規則或小紅點。
- 不改 Google Play／網站跳轉和下載完成流程。
- 不更改設定頁、其他 Dialog、全域 Material theme 或 App 其他按鈕。
- 不建立新的 OpenSpec change；若後續正式實作流程要求 OpenSpec，應由用戶另行指定。

## 設計預覽

本次討論的 HTML 預覽位於：

```text
/.superpowers/brainstorm/55785-1784914235/content/
```

`.superpowers` 為本機忽略目錄，預覽不加入 git。

## 未決事項

無。視覺方案、操作順序、主次層級、響應式門檻、行為邊界及驗證方式均已確認。
