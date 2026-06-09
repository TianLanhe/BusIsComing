## Why

当前 App 在补全首程候车时间时要求 Citybus ETA 响应中的 `seq` 必须等于 `ppsearch_p3.php` 的首程上车站序。真实数据中已出现 `route-stop` 可用、ETA 也有非空时间，但 ETA 响应 `seq` 与上车站序不一致的情况，导致有效候车时间被错误显示为不可用。

## What Changes

- 调整首程 ETA 过滤规则：优先使用 `route + stop + dir + seq` 严格匹配；严格匹配没有非空 ETA 时，降级使用 `route + stop + dir` 匹配。
- 保持 route-stop 推导 stop_id 的逻辑不变，仍通过 `boardingSeq` 查找上车站点。
- 保持 ETA 不可用状态、渐进式补全、排序和 UI 文案不变。
- 增加覆盖 `118` 类真实数据差异的测试，确保有效 ETA 不再被 `seq` 差异错误过滤。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `citybus-first-leg-eta`: 修改 ETA 记录过滤要求，允许在严格 `seq` 匹配无非空 ETA 时降级为 `route + stop + dir` 匹配。

## Impact

- 影响 `CitybusFirstLegEtaService` 中 ETA 响应过滤和最近 ETA 选择逻辑。
- 影响 `citybus-first-leg-eta` 规格中“查询并过滤 ETA”和“ETA 不可用”行为。
- 需要更新或新增单元测试，覆盖严格匹配、降级匹配、方向不匹配、站点不匹配、无非空 ETA 等情况。
- 不新增依赖，不修改 Citybus 路线查询 API，不修改 UI 布局。
