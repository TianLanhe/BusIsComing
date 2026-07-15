## Context

常用路線目前由 `RouteConfigRepository` 寫入 `RouteConfigDbHelper` 管理的 SQLite。每條 `RouteConfig` 對外包含名稱、起終點 `Place`、使用次數與最近使用時間；資料表另有 id、建立及更新時間。設定頁由 `SettingsActivity` 與 `activity_settings.xml` 承載，目前只有 App 資訊、偏好、支援與關於入口，沒有路線資料交換流程。

本 change 橫跨設定 UI、Android 文件選擇器、版本化 JSON 解析及 SQLite 批量寫入。它必須保護現有本機路線，避免把解析與 SQL 放進 Activity，並在最多 500 條路線、旋轉、取消、未知文件供應器及取代失敗等情境下保持可控。`MainActivity.onResume()` 已會重新載入路線，因此匯入完成後不需要新增跨頁事件總線。

目前有效規格存在一項歷史矛盾：早期 `route-config-management` 仍允許三要素完全重複，但現行 `RouteConfigRepository.hasDuplicate()`、新增／編輯 UI 與較新的 `route-management-actions` 已禁止完全重複。本 change 會移除舊要求，只使規格符合目前行為。

## Goals / Non-Goals

**Goals:**

- 從設定頁進入專用二級頁，全部匯出或預覽後匯入常用路線。
- 使用自訂 `.bicroutes`、版本化 UTF-8 JSON 與嚴格校驗，讓檔案可安全演進。
- 讓合併及取代都以單一 SQLite transaction 執行，失敗時不留下部分資料。
- 保留合併前既有路線的使用統計；所有新匯入路線從 0 開始。
- 使用 Storage Access Framework，不新增儲存權限或永久 URI 存取權。
- 將 codec、匯入規劃及 Repository 交易保持可獨立測試。
- 提供清楚的隱私、空狀態、預覽、危險確認、進度、完成與錯誤反饋。

**Non-Goals:**

- 不支援選擇部分路線匯出或直接打開系統分享面板。
- 不匯出 SQLite id、使用統計、本機時間戳、查詢結果、ETA、定位或監控 session。
- 不提供密碼、加密、簽名、雲端／帳號同步或自動備份。
- 不直接複製 SQLite 檔案，不修改資料庫 schema。
- 不修改 Citybus、DATA.GOV.HK、Google Geocoding 或任何網路流程。

## Decisions

### Decision 1: 新增專用 Activity 並分離 UI、codec 與 Repository

做法：`SettingsActivity` 只新增 `路線資料` 分組及單一入口；入口打開專用路線傳輸 Activity。Activity 負責頁面狀態、Storage Access Framework launcher、預覽與確認；純 Kotlin 傳輸模型／codec 負責 JSON；`RouteConfigRepository` 負責交易內合併及取代。

原因：既有專案要求 UI 不直接處理 SQLite 或長流程解析。獨立 Activity 能容納預覽、危險確認及完成摘要，codec 可在 JVM 測試，Repository 可保證交易邊界。

替代方案：把所有邏輯加入 `SettingsActivity` 或 `RouteConfigRepository`。否決原因是會分別讓設定頁承擔過多狀態，或讓 Repository 同時負責文件協議與 SQLite，降低可測試性及後續版本演進能力。

### Decision 2: 透過 Storage Access Framework 保存與開啟文件

做法：匯出使用 `CreateDocument`、MIME `application/octet-stream` 及建議檔名 `BusIsComing-routes-YYYYMMDD-HHmm.bicroutes`；匯入使用 `OpenDocument` 接受通用 MIME，再依檔名與內容校驗。App 不取得永久 URI 權限。

若文件供應器可提供顯示名稱，名稱必須以 `.bicroutes` 結尾；若供應器無法提供名稱，則以文件內格式標記及完整校驗為準。匯入以最多 `2 MiB + 1 byte` 的有界讀取處理未知大小。

原因：SAF 讓用戶決定保存及讀取位置，且 minSdk 25 可用，不需要廣泛儲存權限。通用匯入 MIME 可兼容無法辨識自訂副檔名的文件供應器。

替代方案：寫入公共 Downloads、使用舊式檔案路徑或申請儲存權限。否決原因是 Android 版本相容與權限成本更高，也削弱使用者對檔案位置的控制。

### Decision 3: `.bicroutes` 版本 1 使用嚴格 JSON 協議

做法：檔案為未加密 UTF-8 JSON，必要外層欄位為：

```json
{
  "format": "com.golink.busiscoming.routes",
  "version": 1,
  "exportedAt": "2026-07-16T10:30:00Z",
  "routes": []
}
```

每條路線只包含 `name`、`origin.name/latitude/longitude` 與 `destination.name/latitude/longitude`。版本 1 缺少必要欄位、出現未知欄位、格式標記不符或版本不是 `1` 都整份拒絕。未來不相容改動必須提升版本。

原因：自訂副檔名避免用戶把檔案當成一般 JSON 編輯；格式標記與版本讓 App 可明確拒絕不支援內容。嚴格欄位避免悄悄忽略拼錯或惡意資料。

替代方案：CSV、SQLite 備份或無版本 JSON。CSV 不利於巢狀地點資料及演進；SQLite 會暴露本機狀態並綁定 schema；無版本 JSON 無法提供可靠相容策略。

### Decision 4: 完整校驗後才建立預覽

做法：依序校驗副檔名（如可取得）、2 MiB 大小、格式標記、版本、1 至 500 條路線、必要欄位、非空名稱、有限座標及範圍。緯度必須在 `-90..90`，經度必須在 `-180..180`，起終點不得完全相同。任一非法路線使整份文件失敗，不產生部分預覽。

檔案內三要素完全重複的項目是可識別的重複資料而非非法欄位，只保留第一條並計入跳過數。預覽顯示檔名、唯一有效路線名稱清單、檔案內重複數、合併新增／跳過數及取代刪除／匯入數。

原因：預覽必須代表可提交的完整資料，避免用戶確認後才發現部分路線被靜默丟棄。2 MiB／500 條限制遠高於正常通勤清單，也能限制記憶體與解析成本。

替代方案：邊解析邊寫入或跳過非法路線。否決原因是會產生部分成功與不明確摘要，取代模式尤其可能造成資料遺失。

### Decision 5: 重複路線以現行三要素規則判定

做法：路線名稱 trim 後相同、起點 `Place` 相同且終點 `Place` 相同，才視為重複；相同名稱但不同起終點，或相同起終點但不同名稱，均可並存。合併時重複項不更新、不覆蓋，也不重置統計。全部重複時回傳正常的零新增摘要。

原因：這與現行 `RouteConfigRepository.hasDuplicate()` 及新增／編輯行為一致，亦符合使用者已確認的分享語義。

替代方案：只看起終點或只看名稱。否決原因是會錯誤合併用戶刻意用不同名稱保存的情境，或錯誤阻止同名但不同路徑。

### Decision 6: 合併及取代在 Repository 內以單一 transaction 執行

做法：預覽階段只讀；確認後，Repository 在 transaction 內重新取得實際資料並再次判重。合併只插入不重複路線；取代在同一 transaction 內刪除全部現有路線後插入檔案唯一項目。任一步驟失敗即回滾。Repository 回傳實際新增、跳過及刪除數，不以預覽估計值當成結果。

合併保留既有 id、使用次數及最近使用時間；新路線由 SQLite 產生 id，使用次數為 0、最近使用時間為 null。取代後所有路線均以新 id 與零統計建立。

原因：預覽至確認期間資料可能變動，交易內再次計算才是可靠結果。單一 transaction 是防止取代留下空資料庫或部分路線的核心保證。

替代方案：逐條呼叫現有 `insert()` 或先刪除再分次插入。否決原因是無法原子回滾，也無法對實際結果提供一致摘要。

### Decision 7: 專用頁以明確狀態承載預覽、處理與結果

做法：操作首頁顯示目前路線數、匯入卡與全部匯出卡；0 條時匯出按鈕禁用。匯出每次先顯示隱私警告，再打開保存位置。匯入預覽提供 `合併匯入` 與危險色 `取代現有路線`；取代再顯示含 X／Y 數量的二次確認。成功後返回操作首頁並持續顯示摘要。

讀取、解析、編碼、寫入及資料庫 transaction 在背景執行；處理期間禁用重複點擊並顯示輕量進度。頁面銷毀後不得更新舊 Activity。configuration change 只保存 URI、檔名及階段，重新讀取文件，不把最多 500 條路線放入 Bundle；若 URI 已失效則回到操作首頁並要求重新選擇。

原因：專用頁比 Bottom Sheet 更能容納長清單、大字體與危險操作。重新讀取 URI 可避免 Binder 大小風險。

替代方案：設定頁直接彈出 Bottom Sheet 或把完整預覽保存到 Bundle。前者在 500 條清單與警告狀態下擁擠，後者有 transaction-too-large 風險。

### Decision 8: 未加密檔案以每次警告及最小資料集控制隱私

做法：檔案不加密，只包含分享所需的路線名稱與起終點地點；每次匯出均提醒包含全部路線及精確座標，沒有「不再提示」。App 不自動上傳、不自動分享，也不保存永久 URI 權限。錯誤訊息不回顯完整座標。

原因：主要情境是在不同 BusIsComing 用戶之間交換檔案；密碼會增加雙方流程、金鑰與錯誤恢復複雜度。最小資料集與明確確認可在本期提供較簡單、可理解的控制。

替代方案：密碼加密或裝置綁定加密。前者顯著擴大協議與 UX；後者無法在另一位用戶裝置匯入，均不符合本期目標。

## Risks / Trade-offs

- [Risk] 全部匯出包含住家或工作地點精確座標。→ Mitigation：每次顯示不可略過的隱私確認，只保存到使用者選擇位置，不自動分享或上傳。
- [Risk] 文件供應器不提供大小或正確 MIME／檔名。→ Mitigation：使用有界讀取；通用 MIME 開啟；檔名可取得時校驗副檔名，否則依內容標記與嚴格 schema。
- [Risk] 預覽後本機路線被其他流程修改，摘要估計過期。→ Mitigation：Repository transaction 內重新判重並回傳實際數量，完成摘要使用實際結果。
- [Risk] 取代交易失敗導致資料遺失。→ Mitigation：刪除與所有插入位於同一 transaction，故障注入測試驗證完整回滾。
- [Risk] 大或惡意文件阻塞 UI／耗盡記憶體。→ Mitigation：2 MiB／500 條硬限制、背景解析、有限數字及嚴格欄位校驗。
- [Risk] configuration change 期間臨時 URI 權限失效。→ Mitigation：重新讀取失敗時安全回到操作首頁，資料庫保持不變並要求重新選擇。
- [Risk] 自訂副檔名無法被部分文件 App 正確分類。→ Mitigation：匯出使用通用 MIME，匯入接受通用 MIME 並以內容作最終信任依據。
- [Risk] 早期 active spec 與目前禁止重複行為矛盾。→ Mitigation：在本 change 明確移除舊允許要求，不改動現行手動保存行為。
- [Trade-off] 嚴格拒絕未知欄位降低同版本寬鬆兼容。→ Mitigation：任何協議擴展均提升版本並增加顯式解析與測試，避免靜默資料差異。

## Migration Plan

1. 新增傳輸模型、codec、匯入規劃及其 JVM 測試，不改變既有資料庫與 UI。
2. 擴充 `RouteConfigRepository` 的 transaction 合併／取代能力及 instrumentation 回滾測試。
3. 新增路線傳輸 Activity、版面、字串、Manifest 註冊與 Storage Access Framework 流程。
4. 在設定頁加入 `路線資料` 分組與單一入口，補充 UI／contract 及無障礙測試。
5. 執行相關測試、`./gradlew build`、模擬器完整流程及 OpenSpec strict validation。

本 change 不修改 schema，部署時不需要資料遷移。若需要回滾，只移除設定入口及傳輸頁／codec／Repository 批量 API；既有路線資料保持原樣。已匯入的路線是正常本機路線，回滾功能後仍可由既有管理及查詢流程使用。

## Open Questions

無。入口、匯出範圍、保存方式、檔案格式、預覽、合併／取代、重複、使用統計、空資料、隱私、加密及限制均已確認。
