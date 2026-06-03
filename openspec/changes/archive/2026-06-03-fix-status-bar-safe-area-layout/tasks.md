## 1. 安全区工具

- [x] 1.1 新增可复用 UI 工具方法，用于把系统栏 top inset 叠加到目标 View 的原始 top padding
- [x] 1.2 确保工具方法不会重复叠加 padding，避免页面恢复或 inset 重新分发时出现双倍顶部间距
- [x] 1.3 使用 AndroidX Core 的 `ViewCompat` / `WindowInsetsCompat`，不新增大型依赖

## 2. 页面接入

- [x] 2.1 为 `activity_main.xml` 根容器增加稳定 id，并保留现有整体布局结构
- [x] 2.2 为 `activity_route_manage.xml` 根容器增加稳定 id，并保留现有整体布局结构
- [x] 2.3 为 `activity_route_edit.xml` 内部内容容器增加稳定 id，避免直接影响 ScrollView 滚动行为
- [x] 2.4 在 `MainActivity` 中对主界面根容器应用状态栏安全区
- [x] 2.5 在 `RouteManageActivity` 中对路线管理页根容器应用状态栏安全区
- [x] 2.6 在 `RouteEditActivity` 中对路线编辑页内容容器应用状态栏安全区

## 3. 布局微调

- [x] 3.1 检查并微调主界面顶部标题、“管理路线”按钮、路线选择器和查询按钮之间的 margin
- [x] 3.2 检查并微调路线管理页顶部标题和新增按钮位置
- [x] 3.3 检查并微调路线编辑页标题和首个输入框位置
- [x] 3.4 确保长按钮文字和标题不会重叠或挤压

## 4. 验证

- [x] 4.1 执行 `./gradlew build`，确认构建通过
- [x] 4.2 在模拟器或真机打开主界面，确认内容不再与状态栏重叠
- [x] 4.3 在模拟器或真机检查路线管理页和路线编辑页顶部布局
- [x] 4.4 验证新增路线、查询路线和排序功能仍然正常
- [x] 4.5 如果没有可用设备，记录未完成视觉验证的原因

## 实现备注

- 新增 `WindowInsetsExt.applyStatusBarPadding()`，基于 `WindowInsetsCompat.Type.statusBars()` 动态叠加顶部安全区。
- 已在主界面、路线管理页、路线编辑页接入状态栏安全区处理。
- 已执行 `./gradlew build`，构建成功。
- 已在模拟器 `emulator-5554` 安装并启动 App，检查主界面、路线管理页和路线编辑页顶部布局，均未与状态栏重叠。
- 已通过模拟器点击检查返回、查询和价格表头排序交互；4.5 为无设备时的兜底记录项，本次有设备可用，因此标记完成。
