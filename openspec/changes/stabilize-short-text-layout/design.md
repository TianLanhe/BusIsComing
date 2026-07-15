## Context

BusIsComing 目前使用 XML + AppCompat + Material Components，部分底部彈層和對話框由 Kotlin 動態建立 `TextView`、`MaterialButton` 和相關容器。已觀察到同一份 App 在 API 36.1 模擬器上會把若干短中文文案按容器寬度拉開，而 API 37 模擬器顯示正常。具體高風險位置包括首頁首次引導按鈕、`TemporaryRouteBottomSheet` 的 `臨時查詢` 標題、`使用此路線查詢` 與 `保存為常用` 主按鈕，以及其他短按鈕、chips 和底部彈層標題。

這類問題不是查詢流程、資料來源或文案內容造成的，而是 UI 文字控件在不同 Android 版本或平台排版策略下缺少明確約束。修復應保持窄範圍：穩定 App 自有短文案的排版屬性，不改變業務文案、查詢行為、資料格式或依賴版本。

## Goals / Non-Goals

**Goals:**

- 讓 App 自有短標題、按鈕、chips、短標籤、底部彈層標題、對話框標題和短操作項在 API 36.1 與 API 37 模擬器上保持自然字距。
- 對 Kotlin 動態 UI 和 XML UI 都提供一致的短文案穩定策略。
- 避免修復引入大字體下明顯截斷、重疊或不可理解的文字狀態。
- 用輕量契約測試和 API 36.1/API 37 截圖保護已知復現場景。
- 將規則沉澱到 `app-ui-style-system` 和 `docs/ui-style-guide.md`，供後續 UI 改動沿用。

**Non-Goals:**

- 不調整 Citybus、DATA.GOV.HK、ETA、排序、路線保存、本機資料或通知監控邏輯。
- 不改 App 文案內容，不進行硬編碼文案到 `strings.xml` 的資源化遷移。
- 不升級 AGP、compileSdk、targetSdk、Material Components、AppCompat 或 Kotlin 配置。
- 不引入 Compose，不重構現有動態 UI 到 XML 或把 XML 大規模改成動態 UI。
- 不要求真機驗證、不切換系統語言、不追求不同 Android 版本截圖像素一致。
- 不對長正文、路線站名、候選地點、用戶輸入、第三方動態資料或系統通知模板文本套用短文案規則。

## Decisions

### 1. 使用 App 內顯式排版策略，而不是依賴模擬器或系統設定

實作應在 App 自有 UI 控件上顯式禁用字符間兩端對齊，並重置字距與對齊方式。對 API 26+ 可使用 `TextView.justificationMode = Layout.JUSTIFICATION_MODE_NONE`；同時對短文案控件設定 `letterSpacing = 0f` 和明確的 `textAlignment`/`gravity`。API 25 仍應透過可用屬性維持自然字距。

替代方案是調整模擬器設定、系統語言或字體。這只能掩蓋單台設備上的症狀，不能保證真實用戶或其他模擬器版本不再觸發，因此不採用。

### 2. Kotlin 動態 UI 使用集中 helper，XML 使用 style 或顯式屬性

動態建立的 `TextView`、`MaterialButton`、chips 或等效短文案控件應使用集中 helper，例如放在 `ui/common` 的 `TextView` extension。helper 應允許調用方傳入 `Gravity.START`、`Gravity.CENTER` 或 `Gravity.END`，避免所有短文案被硬性居中。XML 控件可透過共用 style 或顯式屬性補齊 `android:justificationMode="none"`、`android:letterSpacing="0"` 和既有對齊要求。

替代方案是在每個文件中手寫同一組屬性。這會讓後續新增短文案時容易漏掉，也難以用契約測試保護，因此只作為 XML 少量補齊時的輔助手段。

### 3. 按 UI 角色界定短文案，不按字符數硬切

短文案覆蓋 App 自有靜態短標題、主/次按鈕、text button、chips、短標籤、底部彈層標題、對話框標題和短操作項。路線站名、用戶輸入、候選地點、Citybus/DATA.GOV.HK 動態返回內容、路線詳情長段落、隱私/說明正文與通知模板由系統渲染的文本不納入。

替代方案是按「少於 N 個字」套規則。中文排版是否被拉開取決於容器寬度、字體和平台排版策略，單純字符數不可靠，也容易誤傷短但需要自然換行或省略的動態內容，因此不採用。

### 4. 不用強制單行作為修復手段

本次修復的核心是禁用不穩定的字符間兩端對齊與字距，而不是把所有控件設成 `maxLines = 1` 或固定高度。對按鈕、chips 或本來就是單行的標籤，可保留既有單行/省略策略；對可能受系統字體放大影響的短文案，應避免新增會造成明顯截斷或重疊的硬限制。

替代方案是全局強制單行和固定高度。它可能在大字體或較窄屏幕上製造新問題，違反現有 UI 風格指南的可讀性要求，因此不採用。

### 5. 驗收以 API 36.1/API 37 視覺證據和契約測試結合

API 36.1 是已知復現環境，API 37 是正常對照環境，兩者都應作為硬門禁。驗收頁面至少包括首頁首次引導與臨時查詢底部彈層，截圖保存到 `openspec/changes/stabilize-short-text-layout/visual-review/`。契約測試應確認關鍵 XML 控件與動態 UI 文件使用短文案穩定策略；完整交付仍需 `./gradlew build`。

替代方案是只靠人工目視。這無法防止後續新增或重構時漏掉短文案策略，也不能在 code review 中提供穩定證據，因此不採用。

## Risks / Trade-offs

- [Risk] 套用範圍過廣，導致長文案或動態站名被錯誤限制。→ Mitigation：按 UI 角色界定短文案；spec 明確排除長正文、動態資料、候選地點與用戶輸入。
- [Risk] helper 被濫用成所有文字的默認樣式。→ Mitigation：helper 命名應突出 `ShortText` 或等效含義；`docs/ui-style-guide.md` 說明只適用短標題、按鈕、chips 和短標籤。
- [Risk] 只修臨時查詢彈層，其他底部彈層或 XML 按鈕仍會在 API 36.1 被拉開。→ Mitigation：tasks 要求掃描 App 自有短文案控件，並以契約測試覆蓋關鍵文件。
- [Risk] 視覺驗收被不同 Android 版本的系統欄、圓角或字體 fallback 小差異干擾。→ Mitigation：驗收只要求短文案自然可讀、不被拉滿、不重疊，不要求像素一致。
- [Risk] 修復時順手遷移文案或升級依賴，擴大 review 面。→ Mitigation：proposal 和 tasks 明確列出非目標；實作只允許排版屬性、helper、契約測試與視覺證據。

## Migration Plan

1. 新增或更新契約測試，先表達短文案穩定策略和關鍵控件覆蓋期望。
2. 新增 `TextView`/`MaterialButton` 等短文案 helper 或等效共用策略。
3. 將 helper 套用到臨時查詢、常用路線、ETA、路線詳情、監控和保存對話框等動態 UI 的短文案控件。
4. 補齊 XML 中首頁首次引導、排序 chips、路線管理/編輯頁等短文案控件的 style 或顯式屬性。
5. 更新 `docs/ui-style-guide.md`，記錄短文案排版穩定規則。
6. 運行相關契約測試和 `./gradlew build`。
7. 使用 API 36.1 和 API 37 模擬器保存首頁首次引導與臨時查詢底部彈層截圖，確認短文案自然可讀。

## Open Questions

無。範圍已確認：硬門禁只要求 API 36.1 與 API 37 模擬器；不要求真機，不切換系統語言，不做文案資源化或依賴升級。
