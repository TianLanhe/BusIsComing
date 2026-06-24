## 1. 工程准备

- [x] 1.1 在 Gradle 版本目录和 app 模块构建文件中加入 Kotlin Android 插件配置
- [x] 1.2 配置 app 模块以现有 Java 11 目标编译 Kotlin 源码
- [x] 1.3 创建 `MainActivity` 作为启动 Activity，并在 `AndroidManifest.xml` 中注册
- [x] 1.4 在 `AndroidManifest.xml` 中加入 `android.permission.INTERNET`
- [x] 1.5 创建数据模型、本地存储、仓库和 UI 页面所需的基础 Kotlin 包结构

## 2. 数据模型和本地持久化

- [x] 2.1 新增 `RouteConfig` 和 `BusRouteOption` 数据模型
- [x] 2.2 新增价格和预计等候时间排序状态相关类型
- [x] 2.3 实现 `RouteConfigDbHelper`，创建 `route_configs` SQLite 表
- [x] 2.4 实现 `RouteConfigRepository.getAll()` 和 `getById(id)`
- [x] 2.5 实现 `RouteConfigRepository.insert(...)`、`update(config)` 和 `delete(id)`
- [x] 2.6 验证路线配置新增、查询、更新、删除、不存在 id 和本地持久化行为

## 3. 巴士查询仓库

- [x] 3.1 定义 `BusRouteRepository.searchRoutes(origin, destination)`
- [x] 3.2 实现 `MockBusRouteRepository`，支持 `渔湾村渔进楼 -> 兴华二村丰兴楼` 的返回结果
- [x] 3.3 实现 `MockBusRouteRepository`，支持 `兴华二村丰兴楼 -> 渔湾村渔进楼` 的返回结果
- [x] 3.4 对未匹配的起点/终点组合返回空列表
- [x] 3.5 增加或执行仓库级验证，覆盖 Mock 结果数量、价格、等待时间和空结果

## 4. 路线编辑流程

- [x] 4.1 创建 `activity_route_edit.xml`，包含路线名称、起点地址、终点地址、保存操作和返回导航
- [x] 4.2 实现 `RouteEditActivity` 新增模式，包含 trim 和必填字段校验
- [x] 4.3 通过 `RouteConfigRepository` 保存有效的新路线配置
- [x] 4.4 实现 `RouteEditActivity` 编辑模式，加载已有记录并回填表单
- [x] 4.5 通过 `RouteConfigRepository` 更新已有路线配置
- [x] 4.6 处理不存在的编辑目标 id，确保页面不崩溃
- [x] 4.7 验证空字段、纯空格字段、有效新增、有效编辑和不存在 id 编辑行为

## 5. 路线管理流程

- [x] 5.1 创建 `activity_route_manage.xml`，包含路线列表、空状态和新增操作
- [x] 5.2 创建 `item_route_config.xml`，展示路线名称、起点到终点文本、编辑操作和删除操作
- [x] 5.3 实现 `RouteConfigAdapter`，用于展示已保存路线配置
- [x] 5.4 实现 `RouteManageActivity` 的列表加载和空状态切换
- [x] 5.5 串联路线管理页到 `RouteEditActivity` 的新增和编辑入口
- [x] 5.6 实现删除确认弹窗和确认删除逻辑
- [x] 5.7 验证列表展示、空状态、新增导航、编辑导航、取消删除、确认删除和删除最后一条数据行为

## 6. 主界面查询流程

- [x] 6.1 创建 `activity_main.xml`，包含路线选择器、查询操作、路线管理入口、结果表头、结果列表和空状态
- [x] 6.2 创建 `item_bus_route.xml`，展示路线名称、港币价格和预计等候时间单元格
- [x] 6.3 实现 `BusRouteAdapter`，用于展示表格化查询结果行
- [x] 6.4 主界面恢复时加载路线配置，并以 `路线名称：起点地址 -> 终点地址` 格式展示选择器条目
- [x] 6.5 当没有路线配置时展示无路线空状态，并禁用或隐藏不可用的查询控件
- [x] 6.6 从主界面串联路线管理入口和新增路线入口
- [x] 6.7 用户点击查询时，用所选路线的起点和终点调用 `BusRouteRepository`
- [x] 6.8 对非空查询结果展示 `HK$金额` 价格和 `约 N 分钟` 等候时间
- [x] 6.9 查询返回空结果时清空旧结果行，并展示 `暂无可用巴士路线`
- [x] 6.10 验证主界面空状态、CRUD 后选择器刷新、正向 Mock 查询、未匹配查询和展示格式

## 7. 结果排序

- [x] 7.1 实现价格表头点击处理，第一次点击按价格升序排序
- [x] 7.2 再次点击价格表头时切换为价格降序排序
- [x] 7.3 实现预计等候时间表头点击处理，第一次点击按等待时间升序排序
- [x] 7.4 再次点击预计等候时间表头时切换为等待时间降序排序
- [x] 7.5 更新激活表头文本或标记，展示升序和降序方向
- [x] 7.6 确保排序只改变顺序，不增加、删除或重复结果行
- [x] 7.7 验证价格升序、价格降序、等待时间升序、等待时间降序、方向标记和相同值行为

## 8. 打磨和验证

- [x] 8.1 统一所有页面的间距、字体层级、按钮样式和 Material 风格反馈
- [x] 8.2 确保长路线名称和长地址能够换行或省略，不与相邻控件重叠
- [x] 8.3 执行 `./gradlew build`，修复构建或阻塞性 lint 问题
- [x] 8.4 执行可用的单元测试和仪器测试
- [ ] 8.5 手动验证完整流程：首次空状态、新增路线、查询、排序、编辑路线、删除路线、回到空状态
- [x] 8.6 如果实现过程中有任务延期或范围变化，更新实现进度说明

## 实现备注

- 当前 AGP 9.2.1 工程在未显式应用 `org.jetbrains.kotlin.android` 插件时已经生成并执行 `compileDebugKotlin` / `compileReleaseKotlin` 任务；显式应用 Kotlin Android 插件会与已有 `kotlin` 扩展冲突，因此保留现有 AGP Kotlin 编译能力。
- `./gradlew build` 已成功执行，包含本地单元测试、lint、debug/release 构建。
- 未连接 Android 设备或模拟器，因此 8.5 的完整真机/模拟器手动流程仍需后续人工执行。
