## 1. ETA 匹配逻辑

- [x] 1.1 梳理 `CitybusFirstLegEtaService.parseNearestEtaMillis` 当前过滤流程，确认严格匹配字段和非空 ETA 解析路径。
- [x] 1.2 将 ETA 记录解析拆成可复用的候选记录集合，保留 `route`、`stop`、`dir`、`seq`、`etaMillis` 信息。
- [x] 1.3 实现严格匹配优先：当存在 `route + stop + dir + seq` 均匹配且 ETA 可解析的记录时，取最近 ETA。
- [x] 1.4 实现降级匹配：严格匹配无结果时，仅使用 `route + stop + dir` 均匹配且 ETA 可解析的记录，取最近 ETA。
- [x] 1.5 保持 route-stop 推导 stop_id、缓存 TTL、网络请求 URL 和异常处理行为不变。

## 2. 单元测试

- [x] 2.1 更新现有严格匹配测试，验证严格匹配记录存在时仍优先选择严格匹配的最近 ETA。
- [x] 2.2 新增 `118` 类场景测试：route-stop 通过 `boardingSeq=5` 推导 `stop=001312`，ETA 响应返回同 route/stop/dir 但 `seq=3` 的非空 ETA 时，系统返回可用候车时间。
- [x] 2.3 新增降级保护测试：`seq` 不一致且 `route`、`stop` 或 `dir` 任一字段不匹配时，系统不使用该记录。
- [x] 2.4 新增不可用测试：严格和降级匹配都没有非空可解析 ETA 时，系统返回 `WaitTimeState.Unavailable`。
- [x] 2.5 确认缓存、请求失败、时间向上取整等既有测试仍通过，无需改变外部行为。

## 3. 验证

- [x] 3.1 运行 `./gradlew test` 验证单元测试。
- [x] 3.2 运行 `./gradlew build` 验证完整 Android 构建。
- [x] 3.3 如模拟器可用，安装并查询 `Eatdinner`，确认 `118` 正向路线不再因 `seq` 不一致显示 `候车暂无`。（当前真实 ETA 返回 `九巴時段` 且 `eta` 为空，模拟器仍显示 `候车暂无`；该剩余问题不属于本次 `seq` 降级修复范围。）
- [x] 3.4 检查 `git diff`，确认本次相关改动只涉及 ETA 匹配逻辑、相关测试和 OpenSpec 任务勾选。
