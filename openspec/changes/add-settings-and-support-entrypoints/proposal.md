## Why

BusIsComing 接下來需要承載關於我們、隱私政策、分享應用、問題反饋、語言、應用評分與檢查更新等 App 級入口；若繼續把這些功能平鋪到首頁，會稀釋主頁「快速查路線」的高頻任務。隱私政策很快需要對外提供入口，這也是整理 App 設定與支援資訊架構的時機。同時，臨時查詢結果目前只提供保存入口，用戶若發現起點或終點需要微調，只能重新從入口開始一次臨時查詢，流程不夠順。

## What Changes

- 新增獨立 `設定` 頁，從主頁右上角白底描邊圓形設定圖示進入，並以左上返回回到主頁。
- 設定頁按 `偏好`、`支援`、`關於` 分組展示：
  - `語言`：本期保留正常可點擊入口，點擊 Toast 提示暫不支援。
  - `分享應用`：打開系統分享面板，分享固定文案與 `https://www.busiscoming.com`。
  - `問題反饋`：打開郵件 Intent，收件人為 `hezhenyu966@gmail.com`，預填 App／Android／設備資訊與問題描述區。
  - `應用評分`、`檢查更新`：本期保留正常可點擊入口，點擊 Toast 提示暫不支援。
  - `關於我們`：打開設定內二級頁，展示 App 名稱、版本、簡介 `保存常用路線，出門前快速比較巴士方案。` 與官網入口。
  - `隱私政策`：打開線上 URL `https://www.busiscoming.com/zh-hant/privacy/`。
- 主頁頂部不再展示 `管理路線` 大按鈕；普通主頁與首次引導頁都展示右上角設定圖示。
- `常用路線` 標題行保留 `全部` 文字入口，並在旁邊新增獨立 `管理` 文字入口；兩者均為一鍵操作且具備足夠觸控區與清楚間距。
- 臨時查詢結果上下文條在 `保存` 之外新增 `編輯` 入口；點擊後以目前臨時查詢起點和終點預填臨時查詢底部彈層，用戶修改後可再次 `使用此路線查詢`，取消則保留原結果與原臨時查詢上下文。
- 外部動作失敗時使用短 Toast：網站、隱私政策、分享應用與問題反饋各有對應失敗提示。
- 本期不實作真正多語言切換、應用評分、更新檢查、Play Core 或自建更新服務。

## Capabilities

### New Capabilities

- `app-settings-support`: 定義 App 設定頁、關於我們、隱私政策、分享、問題反饋和暫不支援入口的展示與互動行為。

### Modified Capabilities

- `main-route-selection`: 主頁頂部入口改為乘車碼與設定圖示；首次引導頁也提供設定入口；常用路線標題行同時提供 `全部` 與 `管理` 文字入口。
- `route-management-actions`: 主頁 `管理路線` 入口由頂部主色文字按鈕調整為常用路線區的獨立低權重 `管理` 文字入口，首次引導頁仍不展示管理入口。
- `route-query-results-layout`: 臨時查詢結果上下文條由單一保存入口擴展為保存與編輯兩個一鍵入口，且保持緊湊、可點擊與不重疊。
- `route-place-selection`: 臨時查詢底部彈層支援從既有臨時查詢上下文預填起點和終點，並在重新查詢、取消和保存場景下保持既有校驗與資料保護。

## Impact

- 受影響代碼：
  - `app/src/main/java/com/example/busiscoming/ui/main/MainActivity.kt`
  - `app/src/main/java/com/example/busiscoming/ui/main/TemporaryRouteBottomSheet.kt`
  - `app/src/main/res/layout/activity_main.xml`
  - 新增設定相關 Activity／頁面、關於頁面、佈局、字串與圖示資源。
  - `AndroidManifest.xml` 需註冊新增頁面。
- 受影響規格：
  - 新增 `openspec/changes/add-settings-and-support-entrypoints/specs/app-settings-support/spec.md`
  - 修改 `main-route-selection`、`route-management-actions`、`route-query-results-layout` 與 `route-place-selection` delta spec。
- 外部系統：
  - 隱私政策與官網使用外部瀏覽器或等效瀏覽能力打開 URL。
  - 問題反饋使用系統郵件 Intent。
  - 分享應用使用系統分享 Intent。
- 相容性與錯誤處理：
  - 無可處理瀏覽、分享或郵件 Intent 時使用短 Toast 降級。
  - 本期不新增網路 API、資料庫 schema 或外部資料解析邏輯。
  - 臨時查詢編輯不自動保存、不更新常用路線使用統計，不改變 Citybus 查詢參數或路線結果解析。
- 驗證：
  - 需要 JVM／UI contract 測試覆蓋設定頁分組、入口文案、暫不支援 Toast、分享與反饋 Intent 資料，以及臨時查詢上下文條的保存／編輯入口和預填再查流程。
  - Android 實作完成後需執行 `./gradlew build`，並在模擬器或實機檢查首頁普通狀態、首次引導狀態、臨時查詢編輯流程、設定頁、關於頁和外部入口失敗提示。
