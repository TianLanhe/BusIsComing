## REMOVED Requirements

### Requirement: 允许重复路线配置

**Reason**: 早期 MVP 規格允許名稱、起點及終點完全相同的重複路線，但目前 `RouteConfigRepository.hasDuplicate()`、新增／編輯流程及較新的 `route-management-actions` 有效規格均已禁止三要素完全重複。本 change 的匯入合併亦依相同規則跳過完全重複項，因此移除此過時要求以消除規格矛盾。

**Migration**: 不需要資料庫或使用者資料遷移，也不改變目前手動新增、編輯或複製路線的實際行為。既有資料若因舊版本含有完全重複記錄，仍可由現有管理功能處理；新匯入流程只會在匯入候選中保留第一條，合併時跳過與現有資料完全重複的路線。
