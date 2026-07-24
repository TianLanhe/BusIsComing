# 路線頁地點輸入上邊框提示與工具居中設計

## 背景

路線頁目前透過 `SearchFragment` 使用 `PlacePairEditorView` 及共用
`PlaceInputController` 提供起點、終點、目前位置、候選地點與交換操作。
當目前位置成功解析為 Google Maps 地址時，起點輸入框下方同時出現一般選擇提示
與 Google Maps attribution，形成兩行小字、推低終點欄位並浪費垂直空間。

定位按鈕與交換按鈕亦存在可見的垂直偏移。現有版面把定位工具槽固定為
`56dp` 並以 TextInputLayout 頂部對齊，把交換工具槽固定為 `120dp`，實際上卻未考慮
浮動標籤 inset、helper／error 指示區、attribution 及候選清單對欄位幾何位置的影響。
既有測試只確認按鈕尺寸，沒有比較按鈕與輸入區的中心坐標，因此無法阻止偏移回歸。

本設計只調整路線頁。行程新增、編輯與複製頁使用的輸入框、helper、error、定位及
Google Maps attribution 必須保持原狀。

## 目標

- 路線頁輸入框下方不再顯示一般 helper、error 或 Google Maps attribution 小字。
- 把每個欄位當前最相關的單一狀態放在輸入框上邊框，緊接「起點／終點」右側，
  並以中點分隔。
- 讓定位工具的中心精確對齊起點輸入區中心。
- 讓交換工具的中心精確對齊折疊態起點與終點輸入區中心的平均值。
- 候選清單開合時交換按鈕不得跳動。
- 保留地點搜尋、目前位置、Google attribution、交換、候選及查詢的既有資料語義。
- 以明確接口隔離路線頁展示，避免影響行程新增、編輯與複製。

## 非目標

- 不修改 Citybus 地點搜尋、Google reverse geocoding、候選排序、距離或座標。
- 不修改路線查詢、結果列表、保存常用行程或底部導覽。
- 不重設行程新增、編輯或複製頁的輸入 UI。
- 不把候選清單改成 popup、Dialog 或其他呈現方式。
- 不新增動畫、圖標、Compose 或第三方 UI 依賴。
- 不以固定偏移量或按裝置型號特判居中。

## 根因

### 1. 選定地點後一般提示被錯誤恢復

`PlaceInputController.setSelectedPlace()` 最終會清除訊息，而目前的
`clearMessages()` 會把 `defaultInstructionText` 重新寫回
`TextInputLayout.helperText`。因此有效地點選定後，一般「從配對清單中選擇」
提示仍會顯示。

Google Maps attribution 由另一個獨立 TextView 顯示。當選定 Google 地址時，
一般 helper 與 attribution 便同時佔用兩行。

### 2. 定位按鈕對齊固定容器而非輸入區

目前位置工具槽固定為 `56dp`，並以 `top|end` 對齊包住 TextInputLayout 的容器。
TextInputLayout 的浮動標籤會令實際 EditText 輸入區相對容器頂部產生 inset，
所以工具槽中心不等於輸入區中心。

### 3. 交換按鈕依賴固定高度假設

交換工具槽固定為 `120dp`，相當於假設兩個欄位永遠是
`56dp + 8dp + 56dp`。實際 TextInputLayout 高度與欄位間內容會改變，
因此這個固定值不能可靠代表兩個輸入區的幾何中點。

## 設計決策

### 1. 以可選訊息輸出隔離共用控制器

`PlaceInputController` 增加可選的結構化訊息輸出能力，用以表達：

- 一般選擇指引；
- 無匹配地點；
- 地點搜尋失敗；
- 欄位校驗錯誤；
- 清除訊息。

未提供新輸出接口時，控制器繼續使用目前的 `TextInputLayout.helperText` 與
`TextInputLayout.error` 行為。`RouteEditActivity` 不接入新接口，因此行程新增、
編輯與複製保持原狀。

只有 `SearchFragment` 接入路線頁專用的 `SearchFieldCaptionRenderer`。它把
控制器訊息、Google Maps attribution、目前位置失敗及欄位校驗合併為每個欄位
唯一的展示狀態。

### 2. 使用 TextInputLayout 自身的折疊 hint

路線頁不在邊框上覆蓋額外 TextView。每個 TextInputLayout 關閉 expanded hint，
讓 hint 始終位於上邊框 cutout；渲染器按狀態把 hint 組合為：

```text
欄位名稱 · 當前狀態
```

沒有狀態時只顯示欄位名稱。中點左右各保留一個空格。路線頁不得再啟用
TextInputLayout 的 helper／error 指示區，也不得保留輸入框下方的 attribution
TextView。

普通狀態沿用次要文字語義色；校驗錯誤與搜尋失敗使用 error 語義色，並同步使用
錯誤描邊。不得透過縮小字體、水平滾動或裁切核心狀態處理長文案。

### 3. 狀態互斥與優先級

同一欄位只顯示一個最高優先級狀態：

```text
欄位校驗錯誤
> 搜尋失敗／無匹配
> 自動定位失敗
> Google Maps attribution
> 從清單選擇
> 無狀態
```

具體行為：

- 尚未選定有效地點時顯示「從清單選擇」。
- 搜尋進行中保留「從清單選擇」，以尾端 loading 表達進度。
- 搜尋無結果時顯示「找不到配對／匹配地點」。
- 搜尋請求失敗時顯示「搜尋／搜索失敗」。
- 自動定位失敗時只在起點顯示「定位失敗，請手動選擇」。
- 選定 Google 地址時顯示 Google Maps attribution。
- 選定普通 Citybus 地點時只顯示欄位名稱。
- 查詢時欄位未選定有效地點，顯示「請選擇地點」。
- 起終點相同時，只在終點顯示「不能與起點相同」。
- 使用者繼續輸入、重新選擇、重新定位或重試成功後，立即清除已失效狀態。
- 交換起終點時，選定地點與 Google attribution 跟隨交換；搜尋失敗、無匹配、
  定位失敗及校驗錯誤清除。

### 4. 三語可見短文案

路線頁使用以下短文案。TalkBack 仍使用自然完整句子，不受可見短文案長度限制。

| 狀態 | 香港繁體 | 簡體中文 | English |
| --- | --- | --- | --- |
| 欄位名稱 | 起點／終點 | 起点／终点 | From／To |
| 一般指引 | 從清單選擇 | 从列表选择 | Choose from list |
| Google attribution | 地址由 Google Maps 提供 | 地址由 Google Maps 提供 | Google Maps address |
| 無匹配 | 找不到配對地點 | 没有匹配地点 | No matches |
| 搜尋失敗 | 搜尋失敗 | 搜索失败 | Search failed |
| 自動定位失敗 | 定位失敗，請手動選擇 | 定位失败，请手动选择 | Location unavailable |
| 未選定校驗 | 請選擇地點 | 请选择地点 | Choose a place |
| 相同地點校驗 | 不能與起點相同 | 不能与起点相同 | Must differ from start |

例如：

```text
起點 · 從清單選擇
起點 · 地址由 Google Maps 提供
From · Google Maps address
To · Must differ from start
```

路線頁另提供以下等義緊湊文案。渲染策略使用實際折疊 hint 的文字測量結果：
完整文案可完整放入時必須使用完整文案；不能完整放入時使用對應緊湊文案。
不得臨時拼接、裁切或縮小文字。

| 狀態 | 香港繁體緊湊文案 | 簡體中文緊湊文案 | English compact |
| --- | --- | --- | --- |
| 一般指引 | 選擇地點 | 选择地点 | Choose place |
| Google attribution | Google Maps 地址 | Google Maps 地址 | Google Maps address |
| 無匹配 | 無配對 | 无匹配 | No match |
| 搜尋失敗 | 搜尋失敗 | 搜索失败 | Search failed |
| 自動定位失敗 | 定位失敗 | 定位失败 | Location failed |
| 未選定校驗 | 選擇地點 | 选择地点 | Choose place |
| 相同地點校驗 | 與起點相同 | 与起点相同 | Same as start |

緊湊文案仍與欄位名稱組合，例如 `起點 · Google Maps 地址` 或
`To · Same as start`。完整 TalkBack 描述在兩種可見文案下保持相同。

### 5. 定位工具以實際輸入區中心定位

定位按鈕與定位 loading 共用一個 `48 × 48dp` 工具槽。版面完成後，
`PlacePairEditorView` 取得起點 MaterialAutoCompleteTextView 在自身坐標系內的
實際矩形，令工具槽中心 Y 等於輸入區矩形中心 Y。

工具槽位於輸入框尾端保留區，輸入文字繼續保留至少 `52dp` end padding。
工具槽水平中心由固定的右端操作區決定，垂直中心則只依實際輸入區坐標決定。
定位按鈕與 loading 切換 visibility 時不得改變工具槽尺寸或位置。

### 6. 交換工具以折疊態欄位中心計算

交換按鈕維持 `48 × 48dp` 觸控區，置於輸入欄右側獨立 `48dp` 操作軌道。
它的目標中心 Y 為：

```text
(起點輸入區中心 Y + 折疊態終點輸入區中心 Y) / 2
```

若起點候選清單可見並把終點欄位推低，計算折疊態終點中心時必須扣除該清單
實際佔用的高度及垂直 margin。因此候選清單開合不會改變交換按鈕中心。

不得再以 `120dp`、裝置型號、語言、字體倍率或人工 margin 作中心推算。

### 7. 無障礙與大字體

- 定位與交換按鈕保留現有 content description 及至少 `48dp` 觸控區。
- 欄位的 TalkBack 描述包含欄位名稱、目前輸入值及完整狀態。
- error 色之外仍必須有文字狀態，不只靠顏色表達。
- 路線頁短文案須在 360dp、font scale 1.0／1.3／2.0 下驗證。
- 渲染策略只可在完整文案與上表指定的緊湊文案之間選擇；兩者均不得恢復下方
  helper、縮字或省略，並須保留完整 TalkBack 語義。

## 資料與狀態流程

```text
使用者輸入
  → PlaceInputController 更新搜尋狀態
  → 結構化訊息輸出
  → SearchFieldCaptionRenderer 合併欄位／歸因／定位／校驗狀態
  → 更新 TextInputLayout 折疊 hint、描邊與無障礙描述
```

候選地點、`Place`、Google attribution 歸屬及查詢資料流保持原狀。展示狀態不得
寫入 `Place.name`、保存行程或資料庫。

## 預計變更範圍

預計涉及：

- `ui/common/PlaceInputController.kt`
- `ui/common/PlacePairEditorView.kt`
- 新增路線頁欄位狀態／渲染純 Kotlin 類別
- `ui/main/SearchFragment.kt`
- `res/layout/view_place_pair_editor.xml`
- 香港繁體、簡體中文及英文 string resources
- 對應 JVM contract／policy tests 及 instrumentation tests

不得修改：

- `res/layout/activity_route_edit.xml`
- `ui/edit/RouteEditActivity.kt` 的 UI 行為
- 行程新增、編輯與複製頁專用資源或測試期望

## 驗證

### 純邏輯與契約

- 逐一覆蓋所有狀態及優先級。
- 覆蓋使用者輸入、選定、交換、重試及重建後的狀態轉換。
- 斷言有效普通地點選定後不再顯示一般指引。
- 斷言 Google attribution 跟隨實際地址交換。
- 斷言路線頁 XML 不再包含欄位下方 helper／error／attribution 區域。
- 斷言 `activity_route_edit.xml` 與 RouteEditActivity 仍使用既有展示路徑。

### 幾何 instrumentation

- 定位工具槽中心與起點輸入區中心的 Y 誤差不超過 `1dp`。
- 定位按鈕與 loading 具有相同中心。
- 交換按鈕中心與兩個折疊態輸入區中心平均值的 Y 誤差不超過 `1dp`。
- 起點候選清單開合前後，交換按鈕中心不移動。
- 定位與交換觸控區均不小於 `48 × 48dp`。

### 視覺矩陣

- 香港繁體、簡體中文、英文。
- 淺色與深色主題。
- 360dp 寬度。
- font scale 1.0、1.3、2.0。
- 空白、一般輸入、Google 地址、無匹配、搜尋失敗、定位失敗、未選定校驗、
  相同地點校驗及候選清單展開狀態。

最終執行完整 `./gradlew build`，並只使用驗證開始前未運行的模擬器；完成後關閉
本次啟動的模擬器。

## OpenSpec 影響

現行 `route-place-selection` 規格要求搜尋頁 Google Maps attribution 顯示於
對應輸入框下方，亦要求 helper／無結果／錯誤緊跟欄位。本設計改變的是搜尋頁的
展示位置，不改變 attribution 歸屬或狀態語義。

後續 OpenSpec change 應明確修改搜尋頁場景：

- 搜尋頁 attribution 改為對應欄位上邊框狀態；
- 搜尋頁 helper、無結果、搜尋失敗、定位失敗與校驗改為上邊框互斥狀態；
- 行程新增、編輯與複製頁仍保留現行下方 helper／error／attribution 規則；
- 補充按實際坐標驗證定位與交換工具中心的場景。
