## 1. 路線傳輸契約與 codec

- [x] 1.1 先新增 `RouteTransferCodec` JVM 失敗測試，覆蓋版本 1 往返、固定 `format`／`version`／`exportedAt`、路線及地點欄位，以及不輸出 id、使用統計與本機時間戳。
- [x] 1.2 新增版本、結構與資料校驗測試，覆蓋缺少／未知欄位、錯誤類型、未知格式／版本、非法 JSON、空名稱、非有限及越界座標、相同起終點、0 條、501 條及 500 條邊界。
- [x] 1.3 新增有界文件讀取及名稱判定測試，覆蓋 2 MiB、`2 MiB + 1 byte`、可取得名稱時的 `.bicroutes` 要求，以及無名稱時依內容校驗的降級。
- [x] 1.4 新增匯入規劃 JVM 測試，覆蓋檔案內完全重複只保留第一條、三要素完全重複、只有名稱或只有起終點相同、合併新增／跳過及取代刪除／匯入數量。
- [x] 1.5 實作純 Kotlin `RouteTransferModels`、版本 1 `RouteTransferCodec`、明確錯誤類型與有界讀取工具，使 1.1 至 1.4 測試通過且不依賴 Activity 或 SQLite。
- [x] 1.6 補充固定 `.bicroutes` 合法／非法測試樣例，確認編碼後內容不包含查詢結果、ETA、定位、排序快取或通知監控 session。

## 2. Repository 原子匯入

- [x] 2.1 先擴充 `RouteConfigRepositoryInstrumentedTest`，覆蓋合併新增、跳過完全重複、相同部分資料仍新增、既有統計保留及新路線統計為 0／null。
- [x] 2.2 新增取代 instrumentation 測試，覆蓋刪除全部既有路線、匯入去重後路線、重新產生 id、統計歸零，以及 0 條候選不得取代。
- [x] 2.3 建立可測試的交易失敗注入點並新增回滾測試，驗證合併任一插入失敗不留下部分新增，取代刪除或插入失敗完整保留原路線及統計。
- [x] 2.4 在 `RouteConfigRepository` 實作批量合併／取代 transaction 與實際結果模型；交易內重新判重並回傳新增、跳過及刪除數，不修改 SQLite schema 或既有單筆操作語義。

## 3. 設定入口與路線傳輸頁

- [x] 3.1 先擴充設定頁 contract 測試，要求 `路線資料` 分組位於 `偏好` 與 `支援` 之間、只有單一入口，且 Manifest 註冊專用 Activity 但不新增外部儲存權限。
- [x] 3.2 在 `activity_settings.xml`、字串資源與 `SettingsActivity` 加入 `路線資料` 分組及 `匯入與匯出常用路線` item，接入專用路線傳輸 Activity。
- [x] 3.3 建立專用 Activity 與 XML 版面，實作操作首頁、目前路線數、0 條匯出禁用、匯入／匯出卡、頁面返回、處理中狀態及持續完成摘要。
- [x] 3.4 實作全部匯出流程：每次顯示隱私確認、以 `CreateDocument`／`application/octet-stream` 建議 `.bicroutes` 檔名、背景編碼及一次性寫入，並處理取消、成功數量與失敗後不完整文件清理。
- [x] 3.5 實作匯入選檔及預覽流程：以 `OpenDocument` 接受通用 MIME、背景有界讀取及 codec 校驗，顯示檔名、唯一有效路線名稱清單、文件內重複、合併與取代影響；取消或返回不寫入。
- [x] 3.6 接入合併及取代：合併直接提交 transaction；取代先顯示包含現有 X 條與匯入 Y 條的危險二次確認；成功摘要使用 Repository 實際結果，失敗說明現有資料未變。
- [x] 3.7 實作 configuration change 恢復：只保存候選 URI、檔名及頁面階段並重新讀取校驗，不把路線清單放入 Bundle；URI 失效時安全返回操作首頁並提示重新選擇。
- [x] 3.8 確保文件及資料庫工作不阻塞主線程，處理期間禁止重複操作，Activity 銷毀後忽略舊結果並關閉 executor／stream／repository 等資源。

## 4. UI、生命週期與回歸驗證

- [x] 4.1 新增 JVM／contract 測試覆蓋匯出隱私文案、建議檔名、MIME、完成／全重複／錯誤摘要、按鈕可用狀態，以及不提供部分匯出、直接分享、密碼或加密入口。
- [x] 4.2 新增 instrumentation 測試覆蓋合法文件預覽、非法／未知版本不進預覽、合併、全重複、取代取消／確認、交易失敗回滾，以及返回主頁後重新載入最新路線。
- [x] 4.3 新增 Activity recreation 測試，覆蓋預覽旋轉後重新讀取、URI 失效降級、背景結果不更新舊 Activity 及重複點擊不重複提交。
- [x] 4.4 在模擬器或實機透過真實 DocumentsUI 驗證匯出再匯入往返、取消選檔／保存、0 條與多條路線、2 MiB／500 條邊界、合併與取代警告及寫入失敗提示。
- [x] 4.5 驗證長路線名稱、字體放大、窄屏、預覽滾動、至少 48dp 觸控區與 TalkBack 對檔名、數量、合併／取代影響、危險操作及完成摘要的讀出。
- [x] 4.6 更新 `README.md` 的常用路線能力與本機資料說明，記錄 `.bicroutes` 僅包含未加密路線地點、不包含使用統計，且本 change 不修改 Citybus／ETA 生產資料源。

## 5. 最終驗證與交付

- [x] 5.1 執行新增及相關 JVM 測試與 `RouteConfigRepository` instrumentation 測試，修正本 change 導致的失敗並記錄環境限制。
- [x] 5.2 執行相關 UI instrumentation 測試及可完成的模擬器驗收，確認現有設定、路線管理、主頁選擇與使用統計行為沒有回歸。
- [x] 5.3 執行 `./gradlew build` 與 `openspec validate add-saved-route-import-export --strict`，修正編譯、測試、lint 或 artifacts 可追溯性問題。
- [x] 5.4 `/opsx-apply` 完成後檢查 `git status --short` 與 staged diff，排除 `.superpowers/`、文件選擇器產物及其他構建檔，再依專案規則以清晰的 conventional commit 自動提交本 change。
