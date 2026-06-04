# 从 Citybus `ppsearch_p3.php` 结果推导 ETA 的实现指引

## 目标

本文档记录如何从 Citybus 点到点路线搜索接口 `ppsearch_p3.php` 的返回结果中解析第一个推荐路线方案，逐步推导上车站点 `stop_id`，并最终调用 DATA.GOV.HK 城巴公开 ETA API 获取实时抵站时间。

适用场景：

- 已经通过 Citybus 点到点搜索得到 `ppsearch_p3.php` 返回的 HTML。
- 需要从第一个推荐方案中拿到巴士路线的上车站点。
- 需要调用公开 API，例如 `https://rt.data.gov.hk/v2/transport/citybus/eta/CTB/001227/8X`。

本文只阐述一条实现路径：解析 `ppsearch_p3.php` 中第一个路线方案的信息，通过 DATA.GOV.HK 公开 `route-stop` API 推导站点编号，再调用公开 ETA API 获取实时抵站时间。

## 关键结论

`ppsearch_p3.php` 返回的是 HTML，不是 JSON。它本身通常不直接暴露 `001227` 这种 6 位站点编号。

第一个推荐方案的核心数据来自 `showroutep2p(...)` 的第一个字符串参数，例如：

```text
showroutep2p('2|*|CTB||8X-THR-1||6||31||O|*|CTB||1-MAF-1||5||15||I|*|', ...)
```

拆解后：

```text
2
CTB || 8X-THR-1 || 6 || 31 || O
CTB || 1-MAF-1  || 5 || 15 || I
```

字段含义：

```text
第一项 2                = 本方案包含 2 段巴士行程
CTB                     = 公司代码，城巴
8X-THR-1                = Citybus 网页内部路线 variant
6                       = 上车站在该方向路线中的站序 seq
31                      = 下车站在该方向路线中的站序 seq
O                       = 方向，映射到公开 API 的 outbound
I                       = 方向，映射到公开 API 的 inbound
```

方向映射：

```text
O -> outbound
I -> inbound
```

路线 variant 转公开路线号：

```text
8X-THR-1 -> 8X
1-MAF-1  -> 1
```

通常取第一个 `-` 之前的部分作为公开 API 使用的 `route`。

## 推导流程

### 1. 从 `ppsearch_p3.php` 响应中提取 `showroutep2p(...)`

方案字符串示例：

```text
showroutep2p('2|*|CTB||8X-THR-1||6||31||O|*|CTB||1-MAF-1||5||15||I|*|','0','21:54|*|93')
```

实现时只需要提取第一个 `showroutep2p('...')` 的第一个字符串参数，得到首个推荐方案的 bus legs。

### 2. 解析每段 bus leg

解析规则：

```text
parts = info.split("|*|")
legCount = parts[0].toInt()
legs = parts[1..legCount]

每个 leg:
fields = leg.split("||")
company = fields[0]
routeVariant = fields[1]
boardingSeq = fields[2].toInt()
alightingSeq = fields[3].toInt()
bound = fields[4]
```

示例：

```text
CTB||8X-THR-1||6||31||O
```

解析结果：

```text
company = CTB
route = 8X
boardingSeq = 6
alightingSeq = 31
direction = outbound
```

### 3. 通过公开 `route-stop` API 推导上车站点编号

根据 `company + route + direction` 调用：

```text
GET https://rt.data.gov.hk/v2/transport/citybus/route-stop/{company}/{route}/{direction}
```

示例：

```bash
curl 'https://rt.data.gov.hk/v2/transport/citybus/route-stop/CTB/8X/outbound'
```

在返回的 `data` 中查找：

```text
seq == boardingSeq
```

实测例子：

```json
{
  "co": "CTB",
  "route": "8X",
  "dir": "O",
  "seq": 6,
  "stop": "001227"
}
```

因此：

```text
8X + outbound + seq 6 -> stop_id 001227
```

转乘段同理：

```text
CTB||1-MAF-1||5||15||I
```

调用：

```bash
curl 'https://rt.data.gov.hk/v2/transport/citybus/route-stop/CTB/1/inbound'
```

查找 `seq == 5`，得到上车站点。

### 4. 调用 ETA API

拿到上车站点 `stop_id` 后调用：

```text
GET https://rt.data.gov.hk/v2/transport/citybus/eta/{company}/{stop_id}/{route}
```

示例：

```bash
curl 'https://rt.data.gov.hk/v2/transport/citybus/eta/CTB/001227/8X'
```

响应中建议过滤：

```text
dir == 原始 bound
seq == boardingSeq
route == route
stop == stop_id
```

示例返回字段：

```json
{
  "co": "CTB",
  "route": "8X",
  "dir": "O",
  "seq": 6,
  "stop": "001227",
  "dest_tc": "跑馬地(上)",
  "eta": "2026-06-03T20:50:19+08:00",
  "eta_seq": 1,
  "data_timestamp": "2026-06-03T20:47:10+08:00"
}
```

## 完整算法草案

输入：

```text
ppsearchHtml
```

输出：

```text
第一个推荐方案的每段行程 ETA 列表
```

步骤：

```text
1. 在 HTML 中找出第一个 showroutep2p('...') 的第一个字符串参数。
2. 对该 info 字符串按 "|*|" 拆分。
3. 读取 parts[0] 作为 legCount。
4. 对每个 leg：
   4.1 按 "||" 拆分字段。
   4.2 routeVariant 取第一个 "-" 前的部分作为 route。
   4.3 bound == "O" 时 direction = "outbound"。
   4.4 bound == "I" 时 direction = "inbound"。
   4.5 调用 route-stop API。
   4.6 在 route-stop data 中查找 seq == boardingSeq。
   4.7 得到 stop_id。
   4.8 调用 ETA API。
   4.9 过滤 ETA data 中 dir、seq、route、stop 匹配的记录。
5. 将 ETA ISO 时间转换为本地展示，例如“约 N 分钟”或具体发车时间。
```

## Kotlin 实现提示

数据结构可以先设计成：

```kotlin
data class P2pRoutePlan(
    val legs: List<P2pRouteLeg>,
    val rawInfo: String,
)

data class P2pRouteLeg(
    val company: String,
    val route: String,
    val routeVariant: String,
    val boardingSeq: Int,
    val alightingSeq: Int,
    val bound: String,
    val directionPath: String,
)
```

解析方向：

```kotlin
private fun String.toCitybusDirectionPath(): String =
    when (this) {
        "O" -> "outbound"
        "I" -> "inbound"
        else -> error("Unsupported Citybus direction: $this")
    }
```

解析公开路线号：

```kotlin
private fun String.toPublicRouteNumber(): String =
    substringBefore("-")
```

解析 `showroutep2p` 参数时建议：

- 先抽出 `showroutep2p('...')` 的第一个参数。
- 对 HTML entity 或 URL encoding 做必要解码。
- 对 `|*|`、`||` 做结构化拆分。
- 对字段数量做校验，避免脏数据导致崩溃。

## 已验证样例

点到点搜索参数：

```text
起点：漁灣邨漁進樓
终点：港鐵中環站
时间：2026-06-03 20:21 出发
```

`ppsearch_p3.php` 返回的第一个推荐方案：

```text
8X -> 1
info = 2|*|CTB||8X-THR-1||6||31||O|*|CTB||1-MAF-1||5||15||I|*|
```

第一条第一段推导：

```text
routeVariant = 8X-THR-1
route = 8X
boardingSeq = 6
bound = O
directionPath = outbound
route-stop URL = https://rt.data.gov.hk/v2/transport/citybus/route-stop/CTB/8X/outbound
seq 6 -> stop 001227
eta URL = https://rt.data.gov.hk/v2/transport/citybus/eta/CTB/001227/8X
```

## 注意事项

- `ppsearch_p3.php` 返回的是页面 HTML，解析时只取第一个可用的 `showroutep2p(...)`。
- ETA API 路径不包含方向，响应里可能需要按 `dir` 和 `seq` 再过滤。
- Citybus 网页内部路线 variant 不是公开 API 的 `route`。公开 API 使用第一个 `-` 前面的路线号。
- 实时 ETA 可能返回空数组，表示当前没有可用班次或数据暂不可用。
