## 上下文

当前 App 已经使用 `Theme.MaterialComponents.DayNight.NoActionBar` 移除了系统 ActionBar。移除后页面内容完全由 XML 根布局控制，主界面、路线管理页和路线编辑页的根布局都使用固定 `20dp` padding。部分手机或模拟器上，内容顶部会与系统状态栏重叠或距离过近，尤其是页面标题和右侧按钮区域。

这个变更需要处理系统状态栏安全区域，并顺手优化顶部标题、按钮和主要控件的垂直位置。

## 目标 / 非目标

**目标：**

- 页面内容避开系统状态栏，不与状态栏文字/图标重叠。
- 主界面顶部标题和“管理路线”按钮位置自然、完整可见。
- 路线选择器、查询按钮和结果表格保持合适间距，不挤在顶部。
- 路线管理页和路线编辑页同样处理顶部安全区域。
- 保持现有业务逻辑、数据层和查询结果行为不变。

**非目标：**

- 不重做视觉设计系统。
- 不新增复杂 Toolbar 或自定义导航框架。
- 不引入 Compose、Navigation Component 或新的大型依赖。
- 不修改 Mock 查询、SQLite、排序和路线 CRUD 逻辑。

## 技术决策

### 决策 1：使用 WindowInsets 动态应用顶部安全区

新增一个轻量 UI 工具方法，通过 AndroidX `ViewCompat.setOnApplyWindowInsetsListener` 获取 `WindowInsetsCompat.Type.systemBars()` 的 top inset，并把它叠加到页面根容器的原始 top padding 上。

理由：

- 动态适配不同设备、不同状态栏高度和不同系统版本。
- 避免硬编码例如 `44dp`、`56dp` 这类不稳定顶部间距。
- 当前项目已有 AndroidX Core KTX，使用 `ViewCompat` 不需要新增大型依赖。

备选方案：

- 直接把 XML 顶部 padding 改大：实现快，但在不同设备上仍可能过多或不足。
- 恢复 ActionBar：会重新引入重复标题，不符合上一次移除标题栏的目标。

### 决策 2：三个主要页面统一调用同一个安全区工具

主界面、路线管理页和路线编辑页都应在根容器上应用同一个 inset 工具。必要时给根容器增加稳定 id，例如 `mainRoot`、`routeManageRoot`、`routeEditContent`。

理由：

- 避免每个 Activity 自己写一份 inset 逻辑。
- 后续新增页面时可以复用同一工具。
- 统一行为更容易验证。

备选方案：

- 只修主界面：可以解决当前截图，但管理页/编辑页仍可能在同类设备上出问题。

### 决策 3：保持布局结构，微调控件间距

保留现有 LinearLayout/ScrollView 结构，只调整根容器 id、顶部 inset 和少量 margin/padding。按钮位置不做复杂重排。

理由：

- 当前页面结构已经可用，问题集中在安全区域和顶部距离。
- 小范围调整风险低，便于快速验证。

## 风险 / 权衡

- [风险] 如果同时使用系统默认 fitsSystemWindows 和手动 inset，可能产生双倍顶部间距。→ 缓解：只在明确根容器上应用一次 inset，并保留原始 padding 作为基线。
- [风险] 路线编辑页根是 ScrollView，直接给 ScrollView 加 padding 可能影响滚动体验。→ 缓解：优先对 ScrollView 内部内容容器应用 top inset，ScrollView 继续负责滚动。
- [风险] 真机/模拟器视觉验证依赖设备可用性。→ 缓解：构建验证必须完成；如果没有设备，明确保留视觉验证项未完成。

## 验证计划

- 执行 `./gradlew build`。
- 在模拟器或真机打开主界面，确认顶部标题不与状态栏重叠。
- 检查主界面“巴士查询”“管理路线”、路线选择器和查询按钮位置。
- 检查路线管理页和路线编辑页顶部标题/按钮位置。
