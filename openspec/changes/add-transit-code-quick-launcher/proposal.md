## Why

乘車碼跳轉實驗已確認 AlipayHK 可正常到達乘車碼頁，支付寶 `appId` scheme 與 render HTTPS 也可用；微信 OpenSDK 跳轉則返回 `has_no_permission`，不適合作為正式入口。現在需要把主頁上的實驗入口收斂為普通用戶可直接使用的「乘車碼」快捷按鈕，並在 AlipayHK 與支付寶之間自動兜底。

## What Changes

- 主頁頂部移除 `巴士查詢` 文案，改為左側 `乘車碼`、右側 `管理路線` 兩個同級主要按鈕。
- 點擊 `乘車碼` 後不再展示實驗底部彈層，而是直接執行正式乘車碼拉起流程。
- 新增正式乘車碼拉起能力，根據本機是否安裝 AlipayHK 與支付寶自動組裝候選鏈：
  - 只安裝 AlipayHK：AlipayHK scheme -> AlipayHK HTTPS。
  - 只安裝支付寶：支付寶 scheme -> 支付寶 HTTPS。
  - 兩者都安裝：AlipayHK scheme -> AlipayHK HTTPS -> 支付寶 scheme -> 支付寶 HTTPS。
  - 兩者都未安裝：直接 AlipayHK HTTPS。
- 兜底只針對本地啟動失敗，例如無可處理 Activity、啟動被拒或安全異常；一旦 Android 成功接受 `startActivity`，App 即停止嘗試後續候選。
- 所有候選本地啟動均失敗時顯示短提示：`未能開啟乘車碼，請確認已安裝 AlipayHK 或支付寶。`
- 保留既有實驗面板、微信 SDK 接入和診斷代碼，暫不從主頁正式入口暴露；微信不納入正式主備方案。
- 不新增支付偏好、用戶選擇器、資料庫欄位或 Citybus 查詢流程變更。

## Capabilities

### New Capabilities
- `transit-code-quick-launcher`: 正式「乘車碼」單按鈕入口，負責 AlipayHK／支付寶安裝狀態判斷、候選鏈順序、本地失敗兜底與失敗提示。

### Modified Capabilities
- `main-route-selection`: 主頁頂部入口契約調整為左側 `乘車碼`、右側 `管理路線`，並移除 `巴士查詢` 標題文案。

## Impact

- 受影響代碼：
  - `app/src/main/res/layout/activity_main.xml`：主頁頂部佈局與按鈕樣式。
  - `app/src/main/java/com/example/busiscoming/ui/main/MainActivity.kt`：`乘車碼` 點擊綁定改為正式拉起器。
  - `app/src/main/java/com/example/busiscoming/ui/main/` 或相近位置：新增正式乘車碼拉起器、package 檢測與啟動封裝。
  - `app/src/main/java/com/example/busiscoming/data/model/` 或相近位置：新增正式候選常量／模型，避免污染實驗候選列表。
  - `app/src/main/res/values/strings.xml`：新增正式失敗提示文案。
- 既有實驗代碼與測試需要保留，但主頁正式點擊路徑不再依賴 `TransitCodeBottomSheet`。
- Android 11+ package visibility 需確保包含 `hk.alipay.wallet` 與 `com.eg.android.AlipayGphone`；現有 manifest 已具備對應查詢聲明，實作時需回歸驗證。
- 不涉及 Citybus、DATA.GOV.HK、SQLite schema、通知欄監控或路線排序邏輯。
- 測試需覆蓋四種安裝狀態、候選順序、成功即停止、全部失敗 toast、主頁頂部文案與點擊不影響既有查詢／路線管理狀態。
- 人工驗收需在可安裝外部錢包的真機上確認至少「兩者都安裝」時優先打開 AlipayHK 並到達乘車碼頁；其他安裝組合按設備條件補充驗證。
