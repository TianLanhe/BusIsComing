# AGENTS.md

## 文件用途

这个文件是 BusIsComming 项目的项目级长期记忆，用于告诉后续进入本仓库的 AI coding agent：

- 这个项目是什么。
- 当前 App 的背景和目标是什么。
- 开发、验证、OpenSpec 和提交时需要遵守哪些项目约定。

它应该随仓库一起提交到 git，因此属于项目级规则，而不是单次会话记忆或用户本机私有记忆。

## 项目目录说明

当前目录是 Android Studio 项目根目录：

```text
BusIsComming/
├── app/                 Android App 模块
├── docs/                产品设计、规格说明和实现计划
├── gradle/              Gradle version catalog 和 wrapper 配置
├── openspec/            OpenSpec 变更提案、规格和任务
├── build.gradle.kts     根项目构建配置
├── settings.gradle.kts  Gradle settings
└── AGENTS.md            项目级 agent 说明
```

## App 背景和目标

BusIsComming 是一个用于查询巴士到站信息的 Android App。

第一版 MVP 的目标：

- 用户可以本地管理常用路线配置。
- 每条路线配置包含路线名称、起点地址和终点地址。
- 用户可以在主界面选择已保存路线。
- 用户点击查询后，App 展示从起点到终点的巴士路线结果。
- 查询结果包含路线、港币价格和预计车辆到站等候时间。
- 查询结果支持按价格和预计等候时间排序。

当前阶段使用本地 Mock 数据，不接真实 HTTP API。后续拿到真实 API 和调用方式后，再替换查询仓库实现。

## 当前技术栈

- 语言：Kotlin
- UI：XML + AppCompat + Material Components
- 本地存储：SQLiteOpenHelper
- 列表和表格：RecyclerView
- 架构风格：轻量 Repository 分层
- 构建：Gradle Kotlin DSL

当前工程使用 Android Gradle Plugin 9.2.1。该工程在未显式应用 `org.jetbrains.kotlin.android` 插件时已经生成并执行 Kotlin 编译任务。不要盲目重复应用 Kotlin Android 插件；如果需要调整 Kotlin 配置，先运行构建确认不会与现有 `kotlin` 扩展冲突。

## 重要文档

优先阅读以下文档理解项目：

- `docs/overview-design.md`：概要设计
- `docs/specification.md`：规格说明
- `docs/implementation-plan.md`：实现计划
- `docs/ui-style-guide.md`：UI 风格、视觉层级和交互动效指南
- `openspec/changes/`：OpenSpec 变更提案、设计、规格和任务

## OpenSpec 约定

- OpenSpec 生成的人类可读文件使用中文。
- 为了让 OpenSpec 解析通过，spec 文件中必要结构关键词可以保留英文，例如：
  - `## ADDED Requirements`
  - `### Requirement:`
  - `#### Scenario:`
- 每次 `/opsx-propose` 需要生成完整 artifacts：
  - `proposal.md`
  - `design.md`
  - `specs/**/*.md`
  - `tasks.md`
- 每次 `/opsx-apply` 或 `/opsx:apply` 应按对应 change 的 `tasks.md` 执行，完成后更新任务勾选状态。

## opsx-apply 后提交规则

迁移自本地记忆：

```text
Preference: After every opsx-apply completes changes, run git commit to submit the changes automatically after verification. Ask only if commit message or scope is ambiguous.
```

项目级规则：

- 每次 `/opsx-apply` 或 `/opsx:apply` 完成实现和验证后，自动执行 `git commit` 提交本次改动。
- 如果用户没有指定 commit message，基于变更内容生成简洁提交信息。
- 如果提交范围不清晰、工作区存在明显无关改动，先向用户确认。
- 提交前优先运行必要验证；Android 实现改动通常运行：

```bash
./gradlew build
```

## 验证约定

代码实现完成后优先执行：

```bash
./gradlew build
```

这会覆盖：

- Kotlin 编译
- unit tests
- lint
- debug/release assemble

如果需要真机或模拟器验证，但当前没有设备连接，应明确说明未完成手动视觉验证。

检查设备：

```bash
adb devices
```

## 开发注意事项

- 不要把 Mock 巴士数据写死在 Activity 中，应通过 `BusRouteRepository` 提供。
- 本地路线配置通过 `RouteConfigRepository` 访问，不要让 UI 直接操作 SQLite。
- 起点和终点目前只是纯文本地址，不接地图、不做地址联想。
- 价格固定为港币，展示格式为 `HK$金额`。
- 预计等候时间表示车辆到站时间，展示格式为 `约 N 分钟`。
- 删除路线配置需要二次确认。
- 主界面无路线配置时需要展示空状态和新增入口。
- 查询无结果时显示 `暂无可用巴士路线`。
- 新增或改造页面时默认遵循 `docs/ui-style-guide.md`，采用安静实用的现代通勤工具风格。
- 查询结果默认优先使用路线结果卡片；如果继续使用表格，需要在 OpenSpec 设计中说明原因。
- 起点和终点交换默认使用图标按钮，不使用整行文字按钮。

## Git 提交约定

- 保持提交粒度清晰。
- 提交信息使用简洁英文 conventional commit 风格，例如：
  - `feat: implement bus query MVP`
  - `fix: remove app title bar`
  - `docs: add project agent notes`
- 不要提交构建产物。
- 提交前检查：

```bash
git status --short
git diff --cached --stat
```
