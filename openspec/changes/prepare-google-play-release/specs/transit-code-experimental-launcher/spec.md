## REMOVED Requirements

### Requirement: 實驗面板作為內部診斷能力保留
**Reason**: Google Play 上架前移除不再由生產入口使用的實驗乘車碼面板，避免上架包保留微信 SDK 診斷入口與不可達測試 UI。

**Migration**: 正式主頁 `乘車碼` 入口繼續由 `transit-code-quick-launcher` 的 AlipayHK／支付寶候選鏈承接；不再提供實驗 bottom sheet。

### Requirement: 實驗面板列出微信 SDK 與 AlipayHK 候選入口
**Reason**: 微信 SDK 小程序實驗已廢棄，AlipayHK 已進入正式候選鏈，不再需要實驗面板展示候選入口。

**Migration**: 移除微信 SDK 候選、AlipayHK 實驗候選與相關 UI；正式 AlipayHK scheme/HTTPS 仍由 `TransitCodePaymentTargets` 使用。

### Requirement: 候選入口獨立嘗試跳轉
**Reason**: 實驗候選入口被移除後，不再存在逐條手動嘗試實驗入口的行為。

**Migration**: 正式乘車碼入口保留自動 fallback：只在本地啟動失敗時嘗試下一個 AlipayHK／支付寶候選。

### Requirement: 實驗啟動診斷可見
**Reason**: 實驗面板和微信 SDK 診斷能力被移除，上架包不再記錄微信 AppID、userName、registerApp、sendReq 或微信 callback 診斷。

**Migration**: 正式乘車碼流程只保留必要的 AlipayHK／支付寶本地啟動結果日誌；不得保留微信診斷 sink。

### Requirement: 跳轉失敗時保持穩定並提示
**Reason**: 實驗入口失敗提示隨實驗面板移除；正式入口已有獨立失敗提示與 fallback 行為。

**Migration**: 正式乘車碼入口在所有 AlipayHK／支付寶候選均本地啟動失敗後顯示正式失敗提示。

### Requirement: 實驗入口不保存偏好且不影響巴士查詢
**Reason**: 實驗入口被移除後，不再需要規範實驗面板對偏好與巴士查詢狀態的影響。

**Migration**: 正式乘車碼入口仍 SHALL NOT 保存支付偏好，且 SHALL NOT 改變巴士查詢狀態；該行為由 `transit-code-quick-launcher` 規範。

### Requirement: 系統聲明微信與 AlipayHK package visibility
**Reason**: 上架包不再使用微信 OpenSDK 或微信小程序實驗入口，因此不應保留 `com.tencent.mm` package visibility 或 `.wxapi.WXEntryActivity`。AlipayHK package visibility 由正式乘車碼能力保留。

**Migration**: manifest 移除 `com.tencent.mm` query 與 `.wxapi.WXEntryActivity`；保留 `hk.alipay.wallet` 和 `com.eg.android.AlipayGphone` query。
