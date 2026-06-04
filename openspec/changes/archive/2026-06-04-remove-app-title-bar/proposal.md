## 为什么

当前 App 使用系统 ActionBar 显示顶部标题 `BusIsComming`，在运行效果中会占据顶部空间并遮挡页面内自定义标题区域，导致主界面视觉层级混乱。需要移除系统标题栏，只保留页面内容中的自定义标题和操作入口。

## 变更内容

- 移除应用主题自带的顶部 ActionBar 标题栏。
- 保留主界面、路线管理页、路线编辑页内部已有的页面标题和操作按钮。
- 确保内容区域从可见顶部正常布局，不被系统标题栏遮挡。
- 保持现有状态栏颜色、Material 风格和页面交互不变。

## 能力

### 新增能力

- `app-chrome-layout`：定义应用外层 chrome 和页面内容标题的布局规则，确保系统标题栏不与页面内容重叠。

### 修改能力

无。

## 影响范围

- 修改 `app/src/main/res/values/themes.xml`。
- 修改 `app/src/main/res/values-night/themes.xml`。
- 可能需要移除或保留 Activity 中的 `title` 设置，具体以不再显示系统 ActionBar 为准。
- 不影响路线配置、本地存储、Mock 查询和排序逻辑。
