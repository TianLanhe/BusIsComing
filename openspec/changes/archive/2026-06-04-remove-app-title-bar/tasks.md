## 1. 主题调整

- [x] 1.1 将 `values/themes.xml` 中 `Theme.BusIsComming` 的 parent 改为 `Theme.MaterialComponents.DayNight.NoActionBar`
- [x] 1.2 将 `values-night/themes.xml` 中 `Theme.BusIsComming` 的 parent 改为 `Theme.MaterialComponents.DayNight.NoActionBar`
- [x] 1.3 保留现有颜色、状态栏和 Material 组件配置

## 2. 页面导航检查

- [x] 2.1 检查主界面顶部不再显示系统 `BusIsComming` 标题栏
- [x] 2.2 检查主界面内部“巴士查询”和“管理路线”完整可见
- [x] 2.3 检查路线管理页和路线编辑页在无 ActionBar 后仍可通过系统返回键返回

## 3. 验证

- [x] 3.1 执行 `./gradlew build`，确认构建通过
- [x] 3.2 在模拟器或真机运行 App，确认页面内容不再被顶部标题栏遮挡
- [x] 3.3 如发现返回入口不足，再补充页面内返回按钮

## 实现备注

- 已将日间和夜间主题从 `Theme.MaterialComponents.DayNight.DarkActionBar` 改为 `Theme.MaterialComponents.DayNight.NoActionBar`。
- 已执行 `./gradlew build`，构建成功。
- 已在模拟器中验证主界面、路线管理页和路线编辑页均无系统标题栏遮挡；路线编辑页已有页面内返回按钮，系统返回键也可正常逐级返回。
