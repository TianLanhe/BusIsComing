# route-config-management Specification

## Purpose
TBD - created by archiving change build-bus-query-mvp. Update Purpose after archive.
## Requirements

### Requirement: 创建路线配置

系统 SHALL 允许用户创建本地路线配置，字段包含路线名称、起点地址和终点地址。

#### Scenario: 创建有效路线配置
- **WHEN** 用户输入非空的路线名称、起点地址和终点地址并保存
- **THEN** 系统将路线配置保存到本地，并使其可在路线管理页和主界面路线选择器中使用

#### Scenario: 拒绝空字段路线配置
- **WHEN** 用户尝试保存任一必填字段为空的路线配置
- **THEN** 系统阻止保存，并在缺失字段上展示错误提示

#### Scenario: 拒绝纯空格字段
- **WHEN** 用户尝试保存任一必填字段只包含空白字符的路线配置
- **THEN** 系统将该字段视为空字段并阻止保存

### Requirement: 查看路线配置

系统 SHALL 提供路线管理页，用于展示所有已保存的本地路线配置。

#### Scenario: 展示已保存路线配置
- **WHEN** 用户打开路线管理页且本地存在路线配置
- **THEN** 系统展示每条路线配置的路线名称以及起点到终点地址

#### Scenario: 展示路线管理空状态
- **WHEN** 用户打开路线管理页且本地不存在路线配置
- **THEN** 系统展示空状态，而不是没有解释的空列表

### Requirement: 编辑路线配置

系統 SHALL 允許用戶編輯已有本地行程，並 SHALL 依名稱或起終點是否改變保留或清除該行程的置頂偏好。

#### Scenario: 加載已有路線進入編輯
- **WHEN** 用戶選擇編輯某條行程
- **THEN** 系統 SHALL 打開編輯頁，並回填已有行程名稱、起點地址和終點地址

#### Scenario: 保存已編輯路線配置
- **WHEN** 用戶修改為有效行程字段並保存
- **THEN** 系統 SHALL 更新已有本地記錄，並在行程管理頁和常用頁行程選擇器中展示更新後的值

#### Scenario: 處理不存在的編輯目標
- **WHEN** 編輯頁收到的行程 id 不存在
- **THEN** 系統 SHALL NOT 崩潰
- **AND** 系統 SHALL 提示用戶該行程不存在

#### Scenario: 只修改行程名稱
- **WHEN** 用戶保存編輯且行程 id、起點及終點保持不變
- **THEN** 系統 SHALL 保留該行程的全部本次及長期置頂
- **AND** 系統 SHALL 保留每條置頂原 token 及順序

#### Scenario: 修改起終點且存在長期置頂
- **WHEN** 用戶準備保存已修改起點或終點的行程
- **AND** 該行程存在 N 條長期置頂
- **THEN** 系統 SHALL 在寫入前顯示會清除 N 條長期置頂的確認
- **AND** 系統 SHALL 提供取消及確認保存操作

#### Scenario: 取消起終點修改確認
- **WHEN** 用戶在清除置頂確認中選擇取消
- **THEN** 系統 SHALL 保持停留在編輯頁
- **AND** 系統 SHALL NOT 更新行程或清除任何本次／長期置頂

#### Scenario: 確認修改起終點
- **WHEN** 用戶確認保存已修改的起點或終點
- **THEN** 系統 SHALL 在同一 SQLite transaction 更新行程並刪除該行程全部長期置頂
- **AND** 系統 SHALL 清除目前 task 中該行程的全部本次置頂

#### Scenario: 修改起終點但沒有長期置頂
- **WHEN** 用戶保存已修改起點或終點的行程
- **AND** 該行程沒有長期置頂
- **THEN** 系統 SHALL 直接保存而不顯示清除長期置頂確認
- **AND** 系統 SHALL 清除目前 task 中該行程的本次置頂

#### Scenario: 起終點更新 transaction 失敗
- **WHEN** 更新行程或清除長期置頂的任一步驟失敗
- **THEN** 系統 SHALL 回滾整個 transaction
- **AND** 原行程字段及全部長期置頂 SHALL 保持不變
- **AND** 系統 SHALL 顯示保存失敗提示

### Requirement: 删除路线配置需要确认

系統 SHALL 在刪除本地行程前要求用戶確認，並 SHALL 在確認刪除時一併移除只屬於該行程的置頂資料。

#### Scenario: 取消刪除路線
- **WHEN** 用戶開始刪除行程並在確認彈窗中取消
- **THEN** 系統 SHALL 保持該行程及其全部本次／長期置頂不變

#### Scenario: 確認刪除路線
- **WHEN** 用戶確認刪除行程
- **THEN** 系統 SHALL 從本地儲存移除該行程
- **AND** 系統 SHALL 級聯刪除該行程全部長期置頂
- **AND** 系統 SHALL 清除目前 task 中該行程的全部本次置頂
- **AND** 系統 SHALL 刷新可見行程列表

#### Scenario: 刪除一個行程不影響另一行程
- **WHEN** 用戶刪除行程 A
- **THEN** 行程 B 的本次及長期置頂 SHALL 保持不變

### Requirement: 本地持久化路线配置

系统 SHALL 将路线配置持久化保存在本地设备，使其在 App 进程重启后仍可使用。

#### Scenario: 路线配置在重启后保留
- **WHEN** 用户创建路线配置并随后重启 App
- **THEN** 系统从本地存储重新加载已保存路线配置

### Requirement: 複製行程不繼承置頂偏好
系統 SHALL 把複製所得行程視為新的置頂作用域，即使其起點及終點與來源行程相同。

#### Scenario: 複製具有置頂的行程
- **WHEN** 用戶複製一個具有本次或長期置頂的行程並保存為新行程
- **THEN** 新行程 SHALL 使用新的行程 id
- **AND** 新行程 SHALL 不包含任何本次或長期置頂
- **AND** 來源行程的置頂 SHALL 保持不變
