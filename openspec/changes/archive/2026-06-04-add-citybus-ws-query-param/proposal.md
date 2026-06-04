## Why

用户验证发现 Citybus `ppsearch_p3.php` 只返回两条路线的根因不是 `m1` 本身，而是当前 App 请求缺少 `ws=1.3`。缺少该参数时，即使并发请求 `m1=T/F/W`，三种模式也可能返回相同的直达路线集合，导致多程路线、免费转乘路线和步行距离优先路线被遗漏。

## What Changes

- 路线查询 HTTP URL 固定增加 query 参数 `ws=1.3`。
- 保持当前三种 `m1=T/F/W` 并发查询逻辑、共用查询时间 `t`、聚合去重、默认总耗时升序等行为不变。
- 保持不新增 `loc`、`ssid`、`sysid`，本次只新增 `ws=1.3`。
- 更新仓库 URL 构造测试，验证每个 `m1` 请求都包含 `ws=1.3`。
- 使用用户提供的 `m1=W/F/T` 样例补充解析和聚合测试：
  - `m1=W` 样例返回步行距离较短路线，包含 12 条候选。
  - `m1=F` 样例返回最便宜路线，包含免费转乘路线 `8X → 10`、`8X → 1`。
  - `m1=T` 样例返回最快路线，包含 `789 → 619`、`789 → 15` 等多程路线。
- 增加真实 HTTP 对照验证：同一起终点、同一个查询时间、同一个 `m1` 下，分别请求“不带 `ws`”和“带 `ws=1.3`”，证明新增 `ws` 后返回路线更完整。
- 重新验证三种 `m1` 返回存在差异，并验证合并去重后的结果包含来自三种模式的候选路线。
- 设计更完整的测试计划，覆盖 URL 参数、HTML 解析、聚合去重、部分失败、异常返回、排序、UI 展示和真实模拟器验证。

## Capabilities

### New Capabilities

- `citybus-ws-query-parameter`: 定义 Citybus 路线查询必须携带固定 `ws=1.3`，以及加上该参数后三种 `m1` 返回差异、聚合结果和异常场景的验证要求。

### Modified Capabilities

无。

## Impact

- 修改 `CitybusBusRouteRepository` 的 URL 构造逻辑，增加 `ws=1.3`。
- 更新 `CitybusBusRouteRepositoryTest`，覆盖三种 `m1` 请求均包含 `ws=1.3`，且仍不包含 `loc`、`ssid`、`sysid`。
- 更新 `CitybusRouteParserTest` 或新增样例测试，覆盖用户提供的 `W/F/T` 返回 HTML。
- 更新聚合测试，验证加 `ws=1.3` 后的三模式合并结果、去重规则、免费价格、步行距离和默认排序。
- 更新 OpenSpec `tasks.md` 的验证计划，要求执行单元测试、构建、带/不带 `ws` 的真实 cURL 对照和 Pixel 8 模拟器端到端验证。
