## Context

当前 App 已经通过 `CitybusBusRouteRepository` 并发请求 Citybus `ppsearch_p3.php` 的 `m1=T/F/W` 三种路线集合，并由 `CitybusRouteParser` 解析 `aria-label` 中的路线、价格、总耗时和步行距离。`BusRouteOption.arrivalMinutes` 目前仍由 parser 直接赋值为 `durationMinutes`，只是“预计汽车到站时间”的阶段性替代。

`docs/citybus-eta-from-ppsearch.md` 已记录从 `showroutep2p(...)` 推导首程上车站 `stop_id` 的路径：从 route variant 得到公开路线号，通过 `route-stop` API 按 `company + route + direction + boardingSeq` 找到站点，再调用 ETA API 获取实时抵站时间。本次变更把这条路径接入 App，并将主界面结果列改为真实首程候车时间。

## Goals / Non-Goals

**Goals:**

- 为聚合去重后的每条展示路线计算首程车候车时间。
- 多段路线只计算第一段 bus leg 的 ETA，不计算转乘段。
- 主界面表格列名改为 `候车时间\n(分钟)`，行内显示数字分钟、`...` 或 `-`。
- 默认继续按总耗时升序展示。
- 优先补全总耗时最低的前 5 条路线 ETA，最多等待 5 秒后展示完整列表。
- 剩余路线 ETA 在后台继续补全，并逐行更新表格。
- `route-stop` 结果按 `(company, route, direction)` 进程内缓存 1 天。
- 控制 ETA 并发数量，并去重相同首程请求，避免无界网络并发。

**Non-Goals:**

- 不实现转乘段 ETA、全程每段 ETA 或路线详情页。
- 不引入协程、OkHttp、Retrofit 或新的后台任务框架。
- 不落库缓存 ETA 或 `route-stop` 数据。
- 不改变 `ppsearch_p3.php` 的 `m1=T/F/W`、`ws=1.3` 和现有路线聚合去重规则。
- 不改变默认排序字段；默认仍为总耗时升序。

## Decisions

### 决策 1：在候选路线模型中保留首程 ETA 查询信息

`CitybusRouteParser` 需要在解析每个候选 `table` 时，同时读取当前已经使用的 `aria-label` 和该候选元素上的 `showroutep2p(...)` 调用。`aria-label` 继续负责路线、价格、总耗时和步行距离；`showroutep2p` 的第一个字符串参数负责提供首程 bus leg。

首程 leg 字段解析为：

- `company`：例如 `CTB`
- `routeVariant`：例如 `8X-THR-1`
- `route`：取第一个 `-` 前的公开路线号，例如 `8X`
- `boardingSeq`：上车站序
- `bound`：`O` 或 `I`
- `directionPath`：`O -> outbound`，`I -> inbound`

如果候选路线缺少可解析的首程 leg，路线本身仍可展示，但候车时间最终显示为 `-`。这样 Citybus HTML 中个别候选结构异常不会导致整条路线被过滤。

备选方案是在 ETA 补全阶段重新扫描完整 HTML 查找路线名匹配，但同一路线号可能在不同 `m1` 中出现不同总耗时和步行距离，靠展示文本回连不稳定。

### 决策 2：把一次用户查询拆成路线列表阶段和 ETA 补全阶段

现有 `BusRouteRepository.searchRoutes()` 一次性返回 `List<BusRouteOption>`，不适合“先展示前 5 条 ETA，再后台逐行补全”。本次变更应引入一个渐进式查询结果通道，例如回调接口或查询 handle：

```text
onInitialRoutes(routes)
onRouteWaitTimeUpdated(routeKey, waitTimeState)
onFailure(error)
```

Activity 仍只负责 UI 状态、排序和渲染；Citybus 的 `m1`、`showroutep2p`、`route-stop` 和 ETA 细节留在仓库层或仓库内部服务。实现可以继续使用 `ExecutorService + Handler`，避免引入新依赖。

备选方案是让 `searchRoutes()` 等所有 ETA 都完成后一次性返回。它实现简单，但会让最慢的 ETA 请求拖慢整个页面，违背首屏性能目标。

### 决策 3：前 5 条 ETA 作为首屏门槛，最长等待 5 秒

路线聚合去重后先按总耗时升序排序，然后选前 5 条作为首屏优先 ETA。系统并发补全这 5 条；当它们全部完成或失败，或者等待达到 5 秒，就展示完整路线列表。

展示完整列表时：

- 前 5 条已完成 ETA 的显示数字分钟。
- 前 5 条失败或超时的显示 `-`。
- 其余尚未查询完成的显示 `...`。

这能让首屏尽量带真实 ETA，又避免外部 API 慢响应导致用户长时间只看到“查询中”。

### 决策 4：剩余路线后台补全并逐行更新

首屏展示后，剩余路线继续后台受限并发补全 ETA。每条路线完成后，仓库向 UI 发送该路线的候车时间状态更新，Adapter 只更新对应行或重新提交排序后的列表。

如果用户在后台补全期间重新查询、切换路线或退出页面，旧查询的后续更新必须被忽略。现有 `querySequence` 思路可以继续作为 UI 侧的过期保护；仓库侧也应尽量取消未开始任务。

### 决策 5：候车时间状态显式建模

`arrivalMinutes: Int` 不再足以表达 `...` 和 `-`。应把候车时间建模为显式状态，例如：

```kotlin
sealed class WaitTimeState {
    data object Loading
    data class Available(val minutes: Int)
    data object Unavailable
}
```

`BusRouteOption` 可以替换 `arrivalMinutes`，或增加新的 `waitTimeState` 并逐步删除旧语义。排序逻辑应按状态处理：

- `Available(minutes)` 按分钟升序或降序排序。
- `Loading` 和 `Unavailable` 在候车时间排序中排最后。
- 后台 ETA 回来后，如果当前排序字段是候车时间，系统自动重新排序；否则保持当前默认总耗时顺序。

### 决策 6：ETA 服务使用 route-stop 缓存和首程请求去重

`route-stop` 返回路线方向上的站序到站点编号映射，相对稳定，适合进程内缓存 1 天。缓存 key 使用 `(company, route, directionPath)`，缓存值为该方向所有 `seq -> stop_id` 映射或原始 data。

ETA 请求按首程 key 去重，例如 `(company, route, bound, boardingSeq, stopId)`。同一批结果中多个路线共享首程时，只发起一次 ETA 请求，并把结果分发给所有共享路线。

备选方案是每条展示路线都独立请求 `route-stop` 和 ETA。它实现最直观，但 `8X -> 1` 与 `8X -> 10` 这类共享首程的路线会重复请求，增加延迟和外部 API 压力。

### 决策 7：候车分钟数使用向上取整

ETA API 返回 ISO 时间后，系统使用当前系统时间计算剩余秒数：

- 剩余秒数小于等于 0 时，候车时间为 `0`。
- 剩余秒数大于 0 时，按分钟向上取整。

这样 `3 分 10 秒` 展示为 `4`，更符合“还要等多久”的用户语义。测试中应通过注入 clock 固定当前时间。

## Risks / Trade-offs

- [Risk] Citybus HTML 中 `showroutep2p(...)` 所在属性变化，导致首程 leg 解析失败。→ Mitigation：保留原路线结果，候车时间显示 `-`，并用真实 HTML fixture 覆盖解析。
- [Risk] DATA.GOV.HK ETA 或 route-stop 临时失败。→ Mitigation：ETA 失败只影响对应行，不让整个路线查询失败。
- [Risk] 渐进式更新使 UI 排序和行更新复杂度上升。→ Mitigation：用稳定 route key 更新结果；只有当前排序字段为候车时间时才因 ETA 更新触发重排。
- [Risk] 进程内缓存可能使用到一天内变更的 route-stop 数据。→ Mitigation：TTL 限制为 1 天，App 重启自然清空；如果 API 找不到 seq，则该行显示 `-`。
- [Risk] 前 5 条等待 5 秒仍可能让用户觉得慢。→ Mitigation：这是上限，不是固定等待；前 5 条提前完成会立即展示。

## Migration Plan

- 扩展 parser fixture，覆盖每个候选 table 绑定自己的 `showroutep2p(...)` 首程信息。
- 扩展路线结果模型，显式表示候车时间状态。
- 增加 Citybus 首程 ETA 解析、route-stop 查询、ETA 查询、缓存和并发控制组件。
- 调整主界面查询流程，支持初始列表回调和单行候车时间更新。
- 调整表头文案、adapter 展示和排序规则。
- 更新单元测试和模拟器验证。

## Open Questions

无。用户已确认：每条聚合去重后的展示路线都计算首程 ETA；多段路线只算首程；不可用显示 `-`；默认总耗时升序；前 5 条最多等待 5 秒后展示；剩余路线后台补全；`route-stop` 缓存 1 天；表头为 `候车时间\n(分钟)`；本需求新开 change。
