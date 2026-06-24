## 为什么

BusIsComing 需要先完成一个可用的 Android MVP，让用户能够保存常用的起点/终点地址，并快速查看这些路线的可选巴士方案。当前项目仍是空的 Android Studio 工程，因此这个变更用于建立 App 的第一版产品界面、本地数据模型和查询流程。

真实巴士数据后续会来自 HTTP API。当前阶段先用本地 Mock 数据完成端到端功能，便于后续把 Mock 查询替换为真实接口。

## 变更内容

- 增加 Kotlin + XML 的 Android App 入口和基础页面。
- 增加本地路线配置管理能力，支持命名的起点/终点地址组合。
- 将路线配置持久化保存在本地设备。
- 增加主界面，用户可以选择已保存路线并查询巴士方案。
- 第一版使用与起点/终点相关的本地 Mock 数据返回巴士路线。
- 查询结果用三列表格展示：路线、港币价格、预计车辆到站时间。
- 支持点击价格和预计等候时间表头进行排序。
- 支持“没有已保存路线”和“没有可用巴士路线”的空状态。
- 预先加入 `INTERNET` 权限，为后续 HTTP API 接入做准备。

## 能力

### 新增能力

- `route-config-management`：管理本地命名路线配置，包括起点地址、终点地址的新增、查看、修改、删除、校验、删除确认和本地持久化。
- `bus-route-query`：在主界面选择已保存路线，通过仓库抽象查询巴士方案，并在第一版使用 Mock 数据展示路线、价格和预计等候时间。
- `bus-route-results-sorting`：通过可点击表头按价格或预计等候时间排序，并展示当前排序方向。

### 修改能力

无。

## 影响范围

- Android Gradle 配置会增加 Kotlin 支持。
- `AndroidManifest.xml` 会增加主入口 Activity 声明和 `android.permission.INTERNET` 权限。
- 会在 `com.example.busiscoming` 下新增数据模型、本地存储、仓库和 UI 页面相关 Kotlin 代码。
- 会新增主界面、路线管理页、路线编辑页和 RecyclerView 行布局 XML。
- 本地持久化使用 SQLiteOpenHelper 实现。
- 巴士查询会通过 `BusRouteRepository` 抽象，后续可用 `HttpBusRouteRepository` 替换 Mock 实现，减少 UI 改动。
- 验证范围包括仓库行为、路线 CRUD、Mock 查询结果、排序、空状态和 Gradle 构建。
