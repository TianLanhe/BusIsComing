## 上下文

当前 App 主题继承自 `Theme.MaterialComponents.DayNight.DarkActionBar`，因此系统会在每个 Activity 顶部显示 ActionBar，并展示应用标题 `BusIsComing`。页面 XML 内部已经有自定义标题和操作按钮，例如主界面的“巴士查询”和“管理路线”，所以系统标题栏会造成重复标题，并在截图中遮挡页面顶部内容。

## 目标 / 非目标

**目标：**

- 移除系统 ActionBar 标题栏。
- 保留页面内部已有的标题、按钮和内容布局。
- 同时修改日间主题和夜间主题，确保表现一致。
- 保持现有状态栏颜色和 Material 组件样式。

**非目标：**

- 不重构页面布局。
- 不修改路线配置、巴士查询、Mock 数据和排序逻辑。
- 不新增自定义 Toolbar。
- 不调整状态栏沉浸式或 edge-to-edge 行为。

## 技术决策

### 决策 1：将主题父类改为 NoActionBar

把 `Theme.BusIsComing` 的 parent 从 `Theme.MaterialComponents.DayNight.DarkActionBar` 改为 `Theme.MaterialComponents.DayNight.NoActionBar`。

理由：

- 这是移除系统标题栏的最小改动。
- 不需要修改每个 Activity 的布局结构。
- 页面内已经有自定义标题和操作入口，可以承担页面 chrome。

备选方案：

- 在每个 Activity 中调用 `supportActionBar?.hide()`：会把全局布局规则分散到代码里，不如主题层统一。
- 新增自定义 Toolbar：当前页面已经有自定义标题区域，新增 Toolbar 会再次产生重复标题。

### 决策 2：保留 Activity 中的 `title` 设置

可以暂时保留 `title` 设置，因为 NoActionBar 主题下它不会再显示为顶部系统标题。后续如果确认完全无用，可以再清理。

理由：

- 这避免扩大改动范围。
- 对运行时 UI 没有负面影响。

## 风险 / 权衡

- [风险] 没有系统 ActionBar 后，依赖 ActionBar 返回按钮的页面可能失去可见返回入口。→ 缓解：实现时检查路线管理页和路线编辑页，如果仍需要返回入口，应补充页面内返回按钮或让系统返回键承担导航。
- [风险] 内容可能贴近状态栏。→ 缓解：当前不做沉浸式配置，普通 NoActionBar 主题下内容区域仍应位于状态栏下方；构建后通过模拟器/真机确认。
- [风险] 页面标题样式完全依赖 XML 内容。→ 缓解：现有页面已经有清晰标题，后续统一页面标题样式即可。
