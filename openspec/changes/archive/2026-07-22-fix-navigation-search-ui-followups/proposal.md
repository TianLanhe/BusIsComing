## Why

最新實機畫面顯示，底部導覽選中膠囊仍會與標籤重疊，常用與搜尋頁的結果控制區背景、密度及吸頂行為不一致，搜尋地點編輯器亦在圖示對齊、候選距離與共用性上出現偏差。設定頁的乘車碼桌面快捷方式請求缺少可見回饋，使用者無法判斷請求是否已送出、已完成或不受支援，因此需要一次聚焦的 UI 與互動修正。

## What Changes

- 保留 `64×32dp` 底部導覽選中膠囊及 `24dp` 圖示，重新安排圖示、膠囊與標籤間距，使一般與大字體下均不重疊，並維持選中標籤加粗、未選中標籤正常字重。
- 建立常用與搜尋頁共用的透明結果控制器，統一排序、摘要、更新時間、垂直 padding、首張卡片間距與吸頂行為；頁面上滑時只讓排序及摘要吸頂。
- 建立新增、編輯、複製及搜尋頁共用的起終點編輯器，統一 56dp 輸入框、48dp 定位／交換工具區、候選列、欄位級 helper／error／attribution 與圖示置中。
- 讓候選列顯示相對目前位置的距離；搜尋頁恢復既有輸入時，以非阻塞、靜默方式補取定位快照，只更新候選距離而不覆寫起點或觸發 Geocoding。
- 將搜尋頁改為與常用頁一致的單一巢狀滾動結構，使搜尋輸入、保存與查詢操作隨頁面捲走，而共用結果控制器保持吸頂。
- 為設定頁的乘車碼桌面快捷方式補齊已新增、系統確認中、成功、不支援及失敗狀態；固定成功後透過 callback 更新畫面，返回設定頁時重新檢查狀態。
- 不改變 Citybus／Google／DATA.GOV.HK 查詢語義、路線卡片內容結構、已保存行程資料、排序規則、ETA、通知監控或乘車碼支付工具回退鏈。

## Capabilities

### New Capabilities

無。

### Modified Capabilities

- `app-ui-style-system`: 修正底部導覽選中膠囊、圖示與標籤的幾何關係，並規範透明結果控制器在深淺色下的視覺延續性。
- `route-query-results-layout`: 常用與搜尋頁共用緊湊的排序／摘要控制器，且只有該控制器在有效結果期間吸頂。
- `route-place-selection`: 新增、編輯、複製與搜尋共用同一地點編輯器，候選列顯示距離且展開時不再隱藏或移動交換按鈕。
- `app-settings-support`: 設定頁的乘車碼快捷方式入口展示目前狀態及每種平台結果的可理解回饋。
- `transit-code-quick-launcher`: pinned shortcut 請求提供成功 callback、已固定檢測、不支援指引與可重試失敗狀態。

## Impact

- 主要影響 `ui/main`、`ui/edit`、`ui/common`、設定頁與乘車碼 shortcut manager，以及對應 XML、style、drawable 和三語 string resources。
- 會以共用 View／binder 取代常用與搜尋結果控制器、搜尋與行程編輯器中的重複版面；既有 repository、資料模型與外部 API 契約維持不變。
- 搜尋頁會新增一次可取消且具生命週期保護的靜默前台定位快照請求；失敗時僅省略候選距離，不阻塞輸入或顯示錯誤。
- 不新增 Android 運行時權限或第三方依賴；桌面快捷方式繼續使用 Android `ShortcutManager` 與既有 `OPEN_TRANSIT_CODE` explicit intent。
- 驗證需涵蓋 JVM contract／狀態測試、instrumentation 互動測試、`./gradlew build`，以及繁體／簡體／英文、淺／深色、360dp 與字體縮放 `1.0／1.3／2.0` 的實機或模擬器畫面。
