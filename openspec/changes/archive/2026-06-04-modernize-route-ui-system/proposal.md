## Why

当前主页面和路线管理相关页面仍偏表格化、配置页化，核心信息字号偏小，查询结果在手机上阅读和排序都不够直观。用户已经确认后续默认采用“安静实用的现代通勤工具”风格，本次变更需要把这套风格落实到主查询、路线结果、路线管理和路线编辑交互中。

## What Changes

- 将主页面查询结果从表格改为路线结果卡片，突出路线、候车时间、价格、总耗时和步行距离。
- 调整主页面结果字号和卡片尺寸，优先保证文字明显、舒适，一屏大约展示 5 到 6 条完整结果。
- 将排序从依赖表头点击调整为更容易发现的排序控件，并保留按路线、价格、总耗时、候车时间和步行距离排序。
- 使用状态卡或现代化状态区域展示查询中、无结果、失败和 ETA 渐进补全状态。
- 将路线编辑页的起点/终点交换从整行文字按钮改为输入框右侧中线附近的弯曲双向箭头图标按钮，并提供轻量动画反馈。
- 路线管理页跟随主页面风格改造，使用路线卡片、清晰的新增入口和卡片内编辑、克隆、删除操作。
- 明确本轮仍沿用 XML + AppCompat + Material Components，不为本次视觉调整引入 Compose。
- 在设计中引用 `docs/ui-style-guide.md`，作为后续 OpenSpec 页面改造的默认视觉和交互基线。

## Capabilities

### New Capabilities
- `app-ui-style-system`: 定义 BusIsComing 路线相关页面默认采用的现代通勤工具风格、结果卡片、状态反馈、触控和动效基线。

### Modified Capabilities
- `route-query-results-layout`: 将主页面查询结果区域从自适应表格升级为卡片列表，并调整字号、密度、状态呈现和首屏可读目标。
- `bus-route-result-table`: 移除四列表格展示和表头排序作为主交互的要求，改为卡片展示与显式排序控件。
- `bus-route-walking-distance-table`: 移除五列表格和横向滚动要求，保留步行距离、价格和排序能力并迁移到卡片展示。
- `route-place-selection`: 将起点/终点交换控件从普通按钮要求升级为弯曲双向箭头图标按钮，并补充触控区域和动画反馈要求。
- `route-management-actions`: 将路线管理页和路线编辑页的视觉层级升级为与主页面一致的现代卡片式风格。

## Impact

- 影响主界面布局和结果列表：`activity_main.xml`、主页面 Activity、结果 RecyclerView adapter 和结果 item 布局。
- 影响路线管理界面：路线管理 Activity、路线列表 item 布局、新增入口和卡片操作样式。
- 影响路线编辑界面：`activity_route_edit.xml`、交换起点终点交互和相关无障碍/动画反馈。
- 影响资源：颜色、drawable、图标、尺寸、style 或 animator 资源可能需要补充。
- 不引入新的业务 API，不改变路线查询、地点搜索、保存校验或 Citybus 数据解析的核心数据契约。
- 不引入 Compose；实现应继续使用 XML、AppCompat 和 Material Components。
