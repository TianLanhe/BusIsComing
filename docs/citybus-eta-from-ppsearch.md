# 從 Citybus `ppsearch_p3.php` 結果推導 ETA 的實作指引

## 目標

本文記錄如何從 Citybus 點到點路線搜尋接口 `ppsearch_p3.php` 的 HTML 返回中解析 P2P `rawInfo`，再透過 Citybus 網頁內部 `showstops2.php` 取得與 route variant 對齊的 `stop_id`，最後呼叫 DATA.GOV.HK 城巴公開 ETA API 計算首程候車時間。

適用場景：

- 已透過 `ppsearch_p3.php` 取得點到點候選路線。
- 需要取得每條候選路線首程上車站的正確 `stop_id`。
- 需要避免公開 `route-stop` 在 Citybus P2P route variant 場景下的 station seq 錯位。

## 核心結論

`ppsearch_p3.php` 返回的是 HTML，不是 JSON。候選路線的核心資料來自 `showroutep2p(...)` 第一個字串參數，例如：

```text
showroutep2p('2|*|CTB||8X-THR-1||6||31||O|*|CTB||1-MAF-1||5||15||I|*|', ...)
```

拆解後：

```text
2
CTB || 8X-THR-1 || 6 || 31 || O
CTB || 1-MAF-1  || 5 || 15 || I
```

字段含義：

```text
第一項 2                = 本方案包含 2 段巴士行程
CTB                     = 公司代碼，城巴
8X-THR-1                = Citybus 網頁內部 route variant
6                       = 上車站在該 P2P variant 中的站序 seq
31                      = 下車站在該 P2P variant 中的站序 seq
O                       = 方向，ETA API 響應中對應 dir=O
I                       = 方向，ETA API 響應中對應 dir=I
```

公開 ETA API 仍使用公開路線號：

```text
8X-THR-1 -> 8X
1-MAF-1  -> 1
```

但 `stop_id` 不再透過 DATA.GOV.HK `route-stop/{company}/{route}/{directionPath}` 推導，而是透過與 `rawInfo` 對齊的 `showstops2.php`。

## 主推導流程

### 1. 從 `ppsearch_p3.php` 提取 `rawInfo`

從候選路線元素的 `showroutep2p(...)` 中提取第一個字串參數：

```text
2|*|CTB||8X-THR-1||6||31||O|*|CTB||1-MAF-1||5||15||I|*|
```

同一個候選路線通常也會呼叫：

```text
showroutestop('2|*|CTB||8X-THR-1||6||31||O|*|CTB||1-MAF-1||5||15||I|*|')
```

Citybus 前端會基於這個 `rawInfo` 請求 `showstops2.php`。

### 2. 解析每段 bus leg

解析規則：

```text
parts = rawInfo.split("|*|")
legCount = parts[0].toInt()
legs = parts[1..legCount]

每個 leg:
fields = leg.split("||")
company = fields[0]
routeVariant = fields[1]
boardingSeq = fields[2].toInt()
alightingSeq = fields[3].toInt()
bound = fields[4]
publicRoute = routeVariant.substringBefore("-")
```

### 3. 透過 `showstops2.php` 查詢 P2P stop map

請求：

```text
GET https://mobile.citybus.com.hk/nwp3/showstops2.php?r=<rawInfo>&l=<lang>
```

示例：

```bash
curl 'https://mobile.citybus.com.hk/nwp3/showstops2.php?r=1%7C%2A%7CCTB%7C%7C8X-THR-1%7C%7C6%7C%7C20%7C%7CO%7C%2A%7C&l=0'
```

返回片段包含 `addstoponmap(...)`：

```text
addstoponmap('001227',114.24156861053,22.264883822091,'S','6','6 - 樂軒臺, 柴灣道','8X-THR-1','O','N', ...)
addstoponmap('001364',114.19594569053,22.290176642091,'E','20','20 - 長康街, 英皇道','8X-THR-1','O','N', ...)
```

解析後可得到：

```text
routeVariant = 8X-THR-1
seq = 6  -> stop_id 001227, displayName 樂軒臺
seq = 20 -> stop_id 001364, displayName 長康街
```

站名展示只取逗號前第一段，例如 `長康街, 英皇道` 展示為 `長康街`。

### 4. 使用 P2P stop map 定位首程上車站

首程 ETA 只使用第一段 bus leg：

```text
firstLeg = legs.first()
boardingStop = stopMap.find(legIndex=0, routeVariant=firstLeg.routeVariant, seq=firstLeg.boardingSeq)
stop_id = boardingStop.stopId
```

若 `showstops2.php` 失敗、返回空內容、缺少 `addstoponmap(...)`、解析失敗或找不到對應站點，該路線候車時間顯示 `-`，不回退公開 `route-stop`。

### 5. 呼叫 ETA API

拿到 `stop_id` 後呼叫 DATA.GOV.HK 城巴 ETA API：

```text
GET https://rt.data.gov.hk/v2/transport/citybus/eta/{company}/{stop_id}/{publicRoute}
```

示例：

```bash
curl 'https://rt.data.gov.hk/v2/transport/citybus/eta/CTB/001227/8X'
```

ETA 記錄匹配規則：

```text
1. 優先使用 route + stop + dir + seq 均匹配且 eta 非空可解析的記錄。
2. 若嚴格匹配缺失，降級使用 route + stop + dir 匹配且 eta 非空可解析的最近記錄。
3. 降級時仍不得使用 route、stop 或 dir 任一不一致的記錄。
```

示例返回字段：

```json
{
  "co": "CTB",
  "route": "8X",
  "dir": "O",
  "seq": 6,
  "stop": "001227",
  "eta": "2026-06-03T20:50:19+08:00",
  "eta_seq": 1
}
```

## 完整算法草案

輸入：

```text
ppsearchHtml
```

輸出：

```text
每條候選路線首程候車時間
```

步驟：

```text
1. 在候選路線 HTML 中提取 showroutep2p('...') 的第一個字串參數 rawInfo。
2. 按 "|*|" 與 "||" 解析 P2P bus legs。
3. 使用 rawInfo + lang 請求 showstops2.php。
4. 從 addstoponmap(...) 解析 P2P stop map。
5. 使用第一段 routeVariant + boardingSeq 找到首程上車站 stop_id。
6. 使用 company + stop_id + publicRoute 呼叫 ETA API。
7. 按 route + stop + dir + seq 優先匹配 ETA；必要時降級到 route + stop + dir。
8. 將 ETA ISO 時間轉為候車分鐘數。
```

## 快取與失敗策略

- `showstops2.php` 成功解析結果按 `rawInfo + lang` 在 App 進程內快取 1 天。
- 失敗、空響應、缺少站點或解析失敗不快取。
- 同一次查詢中相同 `rawInfo + lang` 只請求一次。
- ETA 即時結果不做 1 天長快取。
- `showstops2` 不可用時不使用 DATA.GOV.HK `route-stop` 作為運行時 fallback。

## 歷史方案留檔：公開 `route-stop`

舊方案曾使用：

```text
GET https://rt.data.gov.hk/v2/transport/citybus/route-stop/{company}/{publicRoute}/{directionPath}
```

再用 `seq == boardingSeq` 找 `stop_id`。這在公開路線站序與 P2P route variant 完全一致時可用，但它只按公開路線號與方向提供靜態站序，不能表示 Citybus P2P 內部 route variant。

已確認錯位案例：

```text
P2P variant: 8X-THR-1
P2P seq=20      -> 001364 長康街
公開 8X/outbound seq=20 -> 001280 新都城大廈
```

因此 `route-stop/{company}/{route}/{directionPath}` 只保留作為歷史方案、觀察和調試參考，不作為新運行時 fallback。

## 與路線詳情接口的邊界

底部路線詳情仍使用：

```text
getp2pstopinroute.php?info=<rawInfo>&ginfo=<ginfo>&lid=<lid>&l=<lang>
```

卡片站點預覽和首程 ETA stopId 使用 `showstops2.php`；底部詳情展示使用 `getp2pstopinroute.php` 當次返回。若兩個接口短時間資料不一致，記錄診斷資訊即可，不向用戶提示，也不讓卡片資料覆寫詳情內容。
