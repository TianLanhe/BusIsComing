## ADDED Requirements

### Requirement: 不直接請求電池最佳化豁免
系統 SHALL NOT 在通知欄監控啟動流程中直接請求忽略電池最佳化，並 SHALL 在未取得電池最佳化豁免時仍允許用戶啟動監控。

#### Scenario: 啟動監控不跳轉電池豁免頁
- **WHEN** 用戶點擊 `開始監控`
- **AND** 通知權限已授權
- **AND** App 尚未被系統豁免電池最佳化
- **THEN** 系統 SHALL NOT 開啟 `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 或等效的直接豁免請求頁
- **AND** 系統 SHALL 繼續依既有監控流程啟動前台服務或處理 exact alarm special access

#### Scenario: 不宣告電池豁免權限
- **WHEN** App 生成可上架的 Android manifest
- **THEN** manifest SHALL NOT 宣告 `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- **AND** manifest SHALL 保留通知欄監控所需的前台服務、通知、wake lock 與 exact alarm 相關宣告

#### Scenario: 電池限制下保持可理解降級
- **WHEN** Android 省電、Doze 或廠商省電策略導致監控刷新延遲
- **THEN** 系統 SHALL 保留最後一次成功資料或資料延遲狀態
- **AND** 系統 SHALL 在下一個可用調度窗口繼續嘗試刷新，除非停止條件已達成
- **AND** 系統 SHALL NOT 因未取得電池最佳化豁免而中止本次監控

## REMOVED Requirements

### Requirement: 提供電池最佳化豁免入口
**Reason**: Google Play 上架前需要降低高敏感系統豁免請求的審核與用戶信任成本；短時公交 ETA 監控不再直接要求用戶將 App 加入忽略電池最佳化白名單。

**Migration**: 監控繼續依賴前台服務、ongoing notification、wake lock timeout、exact alarm special access 與 alarm fallback 提供短時刷新能力；未取得電池最佳化豁免時仍以「每分鐘嘗試更新」和資料延遲狀態處理系統限制。
