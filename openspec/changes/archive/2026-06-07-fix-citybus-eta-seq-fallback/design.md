## Context

`citybus-first-leg-eta` 当前通过 `ppsearch_p3.php` 的 `showroutep2p(...)` 解析首程 bus leg，再用 `route-stop/{company}/{route}/{direction}` 根据 `boardingSeq` 推导上车 `stop_id`，最后调用 `eta/{company}/{stop_id}/{route}` 获取候车时间。

现有实现把 ETA 记录过滤为 `route`、`stop`、`dir`、`seq` 全部匹配首程信息。真实查询中已观察到：`route-stop/CTB/118/outbound` 中 `seq=5` 对应 `stop=001312`，但 `eta/CTB/001312/118` 的非空 ETA 记录返回 `seq=3`。由于 stop 和 direction 已经匹配，继续强制 `seq=5` 会把有效 ETA 错误过滤掉。

## Goals / Non-Goals

**Goals:**

- 在 `route + stop + dir` 已匹配的前提下，避免因 ETA 响应 `seq` 与 `boardingSeq` 不一致而丢弃有效 ETA。
- 保留严格匹配优先策略，使原有可完全匹配的数据仍按最精确路径处理。
- 保持 route-stop 推导、渐进式补全、缓存、排序和 UI 展示逻辑稳定。
- 用单元测试固定 `118` 类 `seq` 不一致场景，防止回归。

**Non-Goals:**

- 不改变 `ppsearch_p3.php` 路线查询参数。
- 不改变 `showroutep2p(...)` 首程解析规则。
- 不新增外部依赖，不调整 UI 布局或文案。
- 不改变“ETA 响应没有非空 ETA 时显示不可用”的行为。

## Decisions

### 决策 1：严格匹配优先，失败后降级匹配

`CitybusFirstLegEtaService` 解析 ETA 响应后先筛选：

```text
route == query.route
stop == stopId
dir == query.bound
seq == query.boardingSeq
eta 非空且可解析
```

如果严格匹配结果非空，继续取最近 ETA。只有严格匹配没有非空 ETA 时，才使用降级规则：

```text
route == query.route
stop == stopId
dir == query.bound
eta 非空且可解析
```

原因：`stop_id` 已由 route-stop 根据 `boardingSeq` 推导，ETA API URL 也以 `stop_id + route` 定位查询对象；当 `stop` 和 `dir` 均匹配时，`seq` 不一致不应直接覆盖掉有效 ETA。严格优先可以保留现有精确匹配行为，降级只处理已观察到的不一致数据。

替代方案：完全移除 `seq` 过滤。这个方案更简单，但会改变所有路线的匹配语义；严格优先的降级方式更保守。

### 决策 2：不把 `seq` 放入不可用判断的唯一依据

现有“ETA 不可用”应调整为：route-stop 找不到 stop、ETA 请求失败、响应解析失败，或严格/降级两轮匹配都没有可解析非空 ETA 时才不可用。

原因：`seq` 是辅助精确匹配字段，不应在 `route + stop + dir` 已能定位候车站点时成为唯一失败原因。

### 决策 3：测试覆盖严格路径和降级路径

单元测试需要同时覆盖：

- 严格匹配存在时优先使用严格匹配。
- 严格匹配缺失但降级匹配存在时返回可用候车时间。
- `route`、`stop` 或 `dir` 不匹配时仍不可用。
- ETA 为空或不可解析时仍不可用。

这样可以证明修复范围只影响 `seq` 不一致但其他定位字段一致的情况。

## Risks / Trade-offs

- [Risk] 某些特殊线路可能在同一方向中重复经过同一站点，忽略 `seq` 可能选到不同经过次数的 ETA。→ Mitigation：只在严格匹配没有非空 ETA 时降级，并继续要求 `route + stop + dir` 一致。
- [Risk] Citybus 公共 API 未来修复 `seq` 不一致后，降级逻辑可能很少触发。→ Mitigation：严格匹配优先，未来数据一致时行为等同现有精确匹配。
- [Risk] 单元测试只覆盖构造数据，真实 API 返回仍可能有其他字段异常。→ Mitigation：加入贴近真实 `118` 差异的 fixture，并保留网络失败和解析失败不可用测试。
