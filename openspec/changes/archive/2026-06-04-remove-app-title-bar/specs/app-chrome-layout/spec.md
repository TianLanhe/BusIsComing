## ADDED Requirements

### Requirement: 移除系统标题栏

系统 SHALL 不显示由应用主题自动生成的顶部 ActionBar 标题栏。

#### Scenario: 主界面不显示系统标题栏
- **WHEN** 用户打开主界面
- **THEN** 页面顶部不显示系统 ActionBar 中的 `BusIsComing` 标题

#### Scenario: 页面内容不被标题栏遮挡
- **WHEN** 用户打开主界面、路线管理页或路线编辑页
- **THEN** 页面内部标题和主要操作控件完整可见，不被系统标题栏覆盖

### Requirement: 保留页面内部标题和操作入口

系统 SHALL 保留页面 XML 内部已有的页面标题和操作入口，用于替代系统标题栏提供页面语义。

#### Scenario: 主界面保留自定义标题
- **WHEN** 用户打开主界面
- **THEN** 页面内部仍显示“巴士查询”标题和“管理路线”入口

#### Scenario: 路线管理页保留自定义标题
- **WHEN** 用户打开路线管理页
- **THEN** 页面内部仍显示“路线管理”标题和新增路线入口

### Requirement: 日夜主题表现一致

系统 SHALL 在日间主题和夜间主题下都移除系统标题栏。

#### Scenario: 日间主题无系统标题栏
- **WHEN** App 使用日间主题启动
- **THEN** 系统不显示 ActionBar 标题栏

#### Scenario: 夜间主题无系统标题栏
- **WHEN** App 使用夜间主题启动
- **THEN** 系统不显示 ActionBar 标题栏
