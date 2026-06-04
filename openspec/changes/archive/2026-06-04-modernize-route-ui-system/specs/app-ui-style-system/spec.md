## ADDED Requirements

### Requirement: 路线相关页面采用统一现代通勤风格
系统 SHALL 让主查询页、路线管理页和路线编辑页采用 `docs/ui-style-guide.md` 中定义的“安静实用的现代通勤工具”风格。

#### Scenario: 主查询页使用统一风格
- **WHEN** 用户打开主查询页
- **THEN** 页面 SHALL 使用清晰标题、路线选择、主查询按钮、状态区域和结果卡片组成第一屏
- **AND** 页面 SHALL 使用项目主色、辅助色、浅色表面、克制圆角和清楚字号层级

#### Scenario: 路线管理页使用统一风格
- **WHEN** 用户打开路线管理页
- **THEN** 页面 SHALL 与主查询页保持一致的背景、标题层级、按钮层级、卡片样式和间距节奏

#### Scenario: 路线编辑页使用统一风格
- **WHEN** 用户打开新增、编辑或克隆路线页面
- **THEN** 页面 SHALL 以轻量表单方式展示路线名称、起点、终点、保存和返回操作
- **AND** 页面 SHALL 避免呈现为后台配置表单或纯文本堆叠界面

### Requirement: 页面状态反馈使用轻量现代样式
系统 SHALL 使用状态卡、状态区域或控件内进度反馈表达加载、空状态、失败和操作反馈。

#### Scenario: 查询状态可感知
- **WHEN** 系统正在查询路线、无路线结果或查询失败
- **THEN** 用户 SHALL 看到与当前状态对应的现代化状态区域，而不是只看到普通文本行

#### Scenario: 空状态提供下一步入口
- **WHEN** 主查询页或路线管理页没有可用路线配置
- **THEN** 系统 SHALL 展示空状态说明和新增路线入口

#### Scenario: 动画不阻塞操作
- **WHEN** 页面展示查询、排序、交换起终点或列表进入反馈
- **THEN** 动画 SHALL 保持在 150ms 到 300ms 的轻量范围内
- **AND** 动画 SHALL NOT 阻塞输入、滚动、返回或再次点击

### Requirement: UI 变更引用项目风格指南
涉及 BusIsComming 页面展示或交互的 OpenSpec 变更 SHALL 以 `docs/ui-style-guide.md` 作为默认视觉和交互基线。

#### Scenario: 新增或改造页面的设计说明
- **WHEN** 后续 OpenSpec change 涉及页面展示、列表、表单、状态或动效
- **THEN** 对应 `design.md` SHALL 说明是否遵循 `docs/ui-style-guide.md`
- **AND** 若偏离该指南，`design.md` SHALL 说明偏离原因
