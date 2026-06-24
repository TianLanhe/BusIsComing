# route-detail-bottom-sheet Specification

## Purpose
TBD - created by archiving change add-route-detail-bottom-sheet. Update Purpose after archive.
## Requirements
### Requirement: 路线卡片可打开详情底部弹层
系统 SHALL 允许用户点击主界面路线结果卡片，并在当前页面打开路线详情底部弹层。

#### Scenario: 点击有详情元数据的路线卡片
- **WHEN** 用户点击一张包含可解析 P2P 详情元数据的路线结果卡片
- **THEN** 系统 SHALL 在当前主界面上方打开路线详情底部弹层
- **AND** 底部弹层 SHALL 展示该路线的路线名、价格、总耗时和步行距离摘要
- **AND** 底部弹层 SHALL NOT 展示起点步行段、终点步行段、换乘步行距离或换乘步行时间

#### Scenario: 弹层打开时保留结果列表上下文
- **WHEN** 路线详情底部弹层打开
- **THEN** 主界面路线结果列表 SHALL 保持在弹层后方
- **AND** 用户关闭弹层后 SHALL 返回原查询结果列表和当前排序状态

#### Scenario: 点击缺少详情元数据的路线卡片
- **WHEN** 用户点击的路线结果缺少可解析 P2P 详情元数据
- **THEN** 系统 SHALL 打开详情底部弹层并展示“路线详情暂不可用”
- **AND** 系统 SHALL NOT 发起 Citybus 路线详情请求

### Requirement: 按需查询 Citybus P2P 路线详情
系统 SHALL 在用户点击路线卡片后，使用该路线的 P2P 详情元数据按需请求 Citybus 路线详情。

#### Scenario: 构造详情请求
- **WHEN** 系统获得路线详情查询元数据 `rawInfo`、`ginfo`、`lid` 和 `lang`
- **THEN** 系统 SHALL 请求 `https://mobile.citybus.com.hk/nwp3/getp2pstopinroute.php`
- **AND** 请求 SHALL 携带 `info=<rawInfo>`、`ginfo=<ginfo>`、`lid=<lid>` 和 `l=<lang>`

#### Scenario: 点击后展示加载状态
- **WHEN** 用户点击路线卡片且详情请求尚未完成
- **THEN** 底部弹层 SHALL 展示路线详情加载状态
- **AND** 加载状态 SHALL NOT 清空主界面已有路线结果

#### Scenario: 详情接口不使用公共 API 兜底
- **WHEN** Citybus P2P 详情请求失败、超时、返回空内容或解析失败
- **THEN** 系统 SHALL 展示详情失败状态
- **AND** 系统 SHALL NOT 调用 DATA.GOV.HK route-stop 或 stop 接口重建路线详情

### Requirement: 解析路线详情站点结构
系统 SHALL 将 Citybus P2P 详情 HTML 解析为结构化路线详情，包含每段巴士的上车站、下车站和途经站。

#### Scenario: 解析单段路线详情
- **WHEN** Citybus P2P 详情 HTML 包含一段巴士路线的站点列表
- **THEN** 系统 SHALL 解析该段路线的 route variant、上车站、下车站和途经站
- **AND** 每个站点 SHALL 尽可能包含站点名称、站点编号、站序、纬度和经度

#### Scenario: 站点展示名只使用逗号前第一段
- **WHEN** 系统解析到的站点名称包含逗号
- **THEN** 系统 SHALL 将第一个逗号前的文本作为该站点的展示名
- **AND** 系统 SHALL 在详情 UI 中展示该展示名
- **AND** 系统 MAY 在结构化模型中保留完整原始站点名称

#### Scenario: 路线段展示方向
- **WHEN** 系统解析到某段巴士的可靠方向信息
- **THEN** 详情 UI SHALL 在该路线段标题区域展示 `往 XX方向`
- **AND** `XX` SHALL 来自接口返回的方向信息

#### Scenario: 路线段缺少方向
- **WHEN** 系统未解析到某段巴士的可靠方向信息
- **THEN** 详情 UI SHALL 隐藏该路线段的方向文本
- **AND** 系统 SHALL NOT 根据上车站、下车站或路线名自行推断方向

#### Scenario: 解析多段中转路线详情
- **WHEN** `rawInfo` 包含两段或更多 bus legs，且详情 HTML 包含对应站点列表
- **THEN** 系统 SHALL 按 `rawInfo` 中 bus leg 顺序生成多个路线详情分段
- **AND** 每个分段 SHALL 只包含该分段对应 route variant 的站点

#### Scenario: 使用站序判定上下车和途经站
- **WHEN** 系统解析某段路线详情站点
- **THEN** 站序等于该 leg `boardingSeq` 的站点 SHALL 标记为上车站
- **AND** 站序等于该 leg `alightingSeq` 的站点 SHALL 标记为下车站
- **AND** 站序位于上车和下车之间的站点 SHALL 标记为途经站

#### Scenario: 解析失败不影响路线列表
- **WHEN** Citybus P2P 详情 HTML 无法解析为有效结构化路线详情
- **THEN** 系统 SHALL 在弹层中展示“路线详情暂不可用”
- **AND** 主界面路线结果列表 SHALL 保持可用

### Requirement: 途经站默认折叠并可按段展开
系统 SHALL 在路线详情底部弹层中默认折叠每段巴士的途经站，并允许用户按段独立展开或收起。

#### Scenario: 默认折叠每段途经站
- **WHEN** 结构化路线详情加载成功
- **THEN** 每段巴士详情 SHALL 默认展示路线号、可选方向、上车站和下车站
- **AND** 每段途经站 SHALL 默认折叠

#### Scenario: 折叠状态展示途经站数量
- **WHEN** 某段巴士包含一个或多个途经站且处于折叠状态
- **THEN** 系统 SHALL 展示 `途经 N 个站 · 展开`
- **AND** `N` SHALL 等于该段上车站和下车站之间的途经站数量

#### Scenario: 展开单段途经站
- **WHEN** 用户点击某段的途经站展开控件
- **THEN** 系统 SHALL 展示该段的全部途经站
- **AND** 系统 SHALL 将该段控件文案更新为 `途经 N 个站 · 收起`
- **AND** 其他路线分段的折叠状态 SHALL 保持不变

#### Scenario: 收起单段途经站
- **WHEN** 用户点击已展开分段的收起控件
- **THEN** 系统 SHALL 隐藏该段的途经站
- **AND** 系统 SHALL 继续展示该段上车站和下车站

#### Scenario: 没有途经站的分段
- **WHEN** 某段巴士上车站和下车站之间没有途经站
- **THEN** 系统 SHALL 展示上车站和下车站
- **AND** 系统 SHALL NOT 展示途经站展开控件

### Requirement: 路线详情采用分段时间线视觉
系统 SHALL 在路线详情底部弹层中使用纵向时间线展示每段巴士，并限制展示信息为本次路线详情范围内的内容。

#### Scenario: 每段巴士使用分色粗竖线
- **WHEN** 结构化路线详情包含一段或多段巴士
- **THEN** 详情 UI SHALL 为每段巴士展示一条粗竖线
- **AND** 相邻路线段 SHALL 使用不同颜色区分
- **AND** 粗竖线 SHALL 仅表达路线分段，不表达车辆实时状态

#### Scenario: 上下车站作为路线段端点
- **WHEN** 某段巴士详情展示成功
- **THEN** 详情 UI SHALL 将上车站和下车站展示为该段粗竖线的端点
- **AND** 途经站展开后 SHALL 展示在该段上车站和下车站之间

#### Scenario: 多段路线的换乘连接
- **WHEN** 结构化路线详情包含两段或更多巴士
- **THEN** 详情 UI SHALL 在两段巴士之间使用灰色虚线或等效弱化连接表示换乘边界
- **AND** 详情 UI SHALL NOT 展示换乘步行距离或换乘步行时间

#### Scenario: 不展示非本次范围的信息
- **WHEN** 路线详情展示成功
- **THEN** 详情 UI SHALL NOT 展示运营时间、到站时刻表、车辆到达时间或步行导航
- **AND** 详情 UI SHALL NOT 展示收藏、截图、分享、关注路线或下车提醒入口

### Requirement: 路线详情支持失败重试
系统 SHALL 在路线详情请求或解析失败时，在底部弹层中展示失败状态并允许用户重试。

#### Scenario: 详情请求失败
- **WHEN** Citybus P2P 详情请求失败或超时
- **THEN** 底部弹层 SHALL 展示“路线详情暂不可用”
- **AND** 底部弹层 SHALL 提供重试入口

#### Scenario: 用户点击重试
- **WHEN** 用户在详情失败状态点击重试
- **THEN** 系统 SHALL 重新发起同一条路线的 Citybus P2P 详情请求
- **AND** 底部弹层 SHALL 重新展示加载状态

#### Scenario: 关闭失败弹层
- **WHEN** 用户关闭处于失败状态的路线详情底部弹层
- **THEN** 系统 SHALL 关闭弹层
- **AND** 主界面路线结果列表 SHALL 保持可用

### Requirement: 路线详情成功结果缓存 1 天
系统 SHALL 按 `rawInfo + lang` 缓存成功解析的路线详情结果 1 天，并避免缓存失败结果。

#### Scenario: 缓存成功解析结果
- **WHEN** 系统成功解析某个 `rawInfo + lang` 对应的路线详情
- **THEN** 系统 SHALL 将结构化详情结果缓存 1 天
- **AND** 缓存 key SHALL 包含完整 `rawInfo` 和 `lang`

#### Scenario: 命中未过期缓存
- **WHEN** 用户再次打开同一 `rawInfo + lang` 的路线详情且缓存未过期
- **THEN** 系统 SHALL 使用缓存的结构化详情结果
- **AND** 系统 SHALL NOT 再次请求 Citybus P2P 详情接口

#### Scenario: 缓存过期
- **WHEN** 某个 `rawInfo + lang` 的详情缓存保存时间超过 1 天
- **THEN** 系统 SHALL 重新请求 Citybus P2P 详情接口
- **AND** 新的成功解析结果 SHALL 替换旧缓存

#### Scenario: 失败结果不缓存
- **WHEN** Citybus P2P 详情请求失败、返回空内容或解析失败
- **THEN** 系统 SHALL NOT 缓存该失败结果
- **AND** 用户后续重试或再次打开同一路线详情时 SHALL 重新请求详情接口

### Requirement: 路線詳情內容優先處理巢狀滾動
系統 SHALL 讓路線詳情內容與底部彈層協調巢狀滾動，確保長內容可完整向下及向上瀏覽。

#### Scenario: 展開大量途經站後滾動詳情
- **WHEN** 用戶展開包含大量途經站的路線分段
- **THEN** 用戶 SHALL 能夠向上滑動查看後續站點
- **AND** 用戶 SHALL 能夠再向下滑動返回詳情頂部

#### Scenario: 內容未到頂時向下滑動
- **WHEN** 路線詳情內容目前未位於滾動頂部且用戶向下滑動
- **THEN** 系統 SHALL 優先將詳情內容向頂部滾動
- **AND** 底部彈層 SHALL NOT 在內容回到頂部前先行收合或關閉

#### Scenario: 內容到頂後繼續向下拖動
- **WHEN** 路線詳情內容已位於滾動頂部且用戶繼續向下拖動
- **THEN** 系統 SHALL 沿用既有底部彈層收合或關閉行為

#### Scenario: 保持既有詳情彈層行為
- **WHEN** 用戶打開、展開、收合或關閉路線詳情
- **THEN** 系統 SHALL 保持既有初始彈層高度、長內容展開、拖動關閉及右上角關閉按鈕
- **AND** 系統 SHALL NOT 改動詳情載入、失敗、重試、時間線、途經站折疊、資料解析或快取行為

