# 乘車碼快捷方式

## 目前入口

乘車碼是輔助快捷能力，不是路線查詢結果。常用頁和搜尋頁不提供固定乘車碼按鈕；正式入口是：

- Manifest 發佈的靜態 App shortcut。
- 設定頁請求固定到主畫面的 pinned shortcut。
- 通知欄監控中的乘車碼 action。
- `MainActivity` 接收明確 `OPEN_TRANSIT_CODE` action 的兼容入口。

桌面 shortcut 先進入無顯示 UI、無歷史、排除 recent tasks 的 `TransitCodeShortcutActivity`。它驗證 action、執行候選鏈、必要時顯示本地化失敗 Toast，隨後立即結束；不打開 BusIsComing 主畫面或改變當前路線結果。

## 支付應用候選鏈

| 安裝狀態 | 順序 |
| --- | --- |
| 只安裝 AlipayHK | AlipayHK scheme → AlipayHK HTTPS |
| 只安裝支付寶 | 支付寶 scheme → 支付寶 HTTPS |
| 兩者均安裝 | AlipayHK scheme → AlipayHK HTTPS → 支付寶 scheme → 支付寶 HTTPS |
| 兩者均未安裝 | AlipayHK HTTPS |

目前候選：

| Provider | 方法 | URI |
| --- | --- | --- |
| AlipayHK | scheme | `alipayhk://platformapi/startApp?appId=85200098` |
| AlipayHK | HTTPS | `https://render.alipay.hk/p/s/hkwallet/landing/easygo` |
| 支付寶 | scheme | `alipays://platformapi/startapp?appId=200011235` |
| 支付寶 | HTTPS | `https://render.alipay.com/p/s/i?appId=200011235` |

每個 target 由 `ACTION_VIEW` 啟動。`ActivityNotFoundException`、`SecurityException` 或其他啟動異常只令本 target 失敗，繼續下一個候選；全部失敗才顯示失敗 Toast。package 偵測異常按未安裝處理，但仍保留 HTTPS 候選。

Manifest package visibility 只聲明 AlipayHK、支付寶和小米權限頁所需 package，不使用 `QUERY_ALL_PACKAGES`。

## 桌面快捷方式

靜態及 pinned shortcut 共用 id `transit_code`、圖示、三語 label 和 `OPEN_TRANSIT_CODE` action。設定頁狀態區分已固定、未固定及未知；固定請求可能返回：

- 已固定
- 需要先處理小米權限
- 已交給 launcher 處理
- launcher 不支援
- 請求失敗

Android launcher 最終可拒絕、延遲或不回報 pin 結果，因此 App 不能只憑 `requestPinShortcut()` 返回 true 宣稱 shortcut 已出現在桌面。成功 callback 由 `TransitCodeShortcutPinnedReceiver` 記錄並提示；返回設定頁時重新查詢實際 pinned shortcuts。

## Xiaomi／Redmi／POCO

小米家族設備在首次請求前使用 permission gate：

1. 若可信狀態已授權、先前 gate 已通過或本次明確 bypass，直接請求 pin。
2. 否則先打開 `com.miui.securitycenter` 的應用權限頁。
3. MIUI 頁不可用時回退 Android App details。
4. 返回設定頁後消費 pending 狀態並重試／重新檢查。
5. pin 請求未完成時清除 gate，讓使用者可再次處理權限。

不得把 manufacturer 不是 Xiaomi／Redmi／POCO 的設備帶入 MIUI 權限流程，也不得因無法判斷權限就聲稱已固定。

## 失敗、日誌與私隱

- 外部 App 或瀏覽器不可用時，BusIsComing 保持可用且不改變查詢／監控資料。
- 日誌可記錄安裝狀態、target 名稱、URI、啟動結果及 exception 類型，不記錄使用者行程、位置或支付資料。
- BusIsComing 不接收支付結果、不持有乘車碼內容，也不聲稱第三方頁面一定成功到達指定服務。
- 外部 label、品牌和 URI 不翻譯；App 自有提示與 `contentDescription` 提供三語資源。

## 已移除的實驗

微信 OpenSDK、微信 package visibility、`.wxapi.WXEntryActivity`、實驗 bottom sheet、AppID／userName 及 callback diagnostic 已從生產 App 移除。其歷史保留在 Git 與 OpenSpec archive，不是目前 manifest、依賴、測試或入口配置依據，不應恢復到長期文件。

## 驗證

- unit／contract：安裝狀態對候選順序、每個 target 失敗後繼續、全部失敗提示、action 校驗、shortcut 狀態及 Xiaomi policy。
- instrumentation：桌面 relay Activity 不顯示主 UI、不進 recent、成功／失敗後 finish；設定頁 pin／返回重查；通知 action 不破壞 monitor session。
- 裝置：選擇具有目標 launcher／廠商行為的合適設備；只使用本任務自行啟動的模擬器或實機，不能以其他 launcher 的結果替代小米權限驗證。
