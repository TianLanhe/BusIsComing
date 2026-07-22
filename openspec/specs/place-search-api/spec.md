# place-search-api Specification

## Purpose
定义 Citybus 地点搜索接口的请求、响应解析和搜索状态暴露规则，为路线管理中的起点/终点候选地点选择提供地点名称、纬度和经度。
## Requirements

### Requirement: 通过 Citybus 接口搜索地点
系統 SHALL 使用 Citybus HTTP 接口按用戶輸入關鍵詞及目前 App 語言搜索候選地點，且 SHALL NOT 為該請求設置靜態瀏覽器 header 或 cookie。

#### Scenario: 發起繁體地點搜索請求
- **WHEN** 用戶以繁體中文輸入地點搜索關鍵詞
- **THEN** 系統 SHALL 請求 `https://mobile.citybus.com.hk/nwp3/bsearch_p3.php`
- **AND** 請求 SHALL 攜帶 `l=0`、`q=<關鍵詞>`、`limit=100`、`timestamp=<目前毫秒級時間戳>`

#### Scenario: 發起簡體地點搜索請求
- **WHEN** 用戶以簡體中文輸入地點搜索關鍵詞
- **THEN** 系統 SHALL 請求 `bsearch_p3.php`
- **AND** 請求 SHALL 攜帶 `l=2`、`q=<關鍵詞>`、`limit=100`、`timestamp=<目前毫秒級時間戳>`

#### Scenario: 發起英文地點搜索請求
- **WHEN** 用戶以英文輸入地點搜索關鍵詞
- **THEN** 系統 SHALL 請求 `bsearch_p3.php`
- **AND** 請求 SHALL 攜帶 `l=1`、`q=<關鍵詞>`、`limit=100`、`timestamp=<目前毫秒級時間戳>`

#### Scenario: 保留 limit 與 timestamp
- **WHEN** 系統構造任何語言的 Citybus 地點搜索請求
- **THEN** 系統 SHALL 保留 `limit=100` 與目前毫秒級 `timestamp`
- **AND** 系統 SHALL NOT 因參數精簡而移除這兩個參數

#### Scenario: 不攜帶瀏覽器 header 或 Cookie
- **WHEN** 系統發起 Citybus 地點搜索請求
- **THEN** 系統 SHALL NOT 顯式設置 `Cookie`
- **AND** 系統 SHALL NOT 顯式設置 `User-Agent`、`Referer`、`Sec-Fetch-*`、`sec-ch-ua*`、`X-Requested-With`、`Connection` 或 `Accept-Language`
- **AND** 系統 SHALL NOT 使用靜態 session、ad、consent 或 tracking cookie

#### Scenario: Header 清理後驗證三語地點搜索一致
- **WHEN** 實作完成後執行 live 驗證
- **THEN** `bsearch_p3.php` SHALL 在繁體、簡體及英文中使用有效關鍵詞樣本驗證
- **AND** 每個樣本 SHALL 返回 HTTP 200
- **AND** 每個樣本 SHALL 包含目前語言的可解析地點候選或明確無結果格式
- **AND** 驗證 SHALL 保存不含敏感資料的可重現請求與業務簽名

#### Scenario: 發起地點搜索請求
- **WHEN** 用戶輸入地點搜索關鍵詞
- **THEN** 系統請求 `https://mobile.citybus.com.hk/nwp3/bsearch_p3.php`
- **AND** 請求 SHALL 攜帶 `l=0`、`q=<關鍵詞>`、`limit=100`、`timestamp=<目前毫秒級時間戳>`

#### Scenario: Header 清理後驗證地點搜索一致
- **WHEN** 實作完成後執行 live 驗證
- **THEN** `bsearch_p3.php` SHALL 使用 10 個有效關鍵詞樣本對比刪除 header/cookie 前後的響應
- **AND** 每個樣本 SHALL 返回 HTTP 200
- **AND** 每個樣本 SHALL 包含可解析地點候選或明確無結果格式
- **AND** 業務簽名或 body hash SHALL 一致

### Requirement: 解析 Citybus 地点搜索响应

系统 SHALL 将 Citybus 返回的纯文本响应解析为地点候选列表。

#### Scenario: 解析正常地点结果
- **WHEN** 响应包含表头和一行或多行以 `|` 分隔的地点记录
- **THEN** 系统跳过表头，并将第 2 列解析为地点名称、第 3 列解析为纬度、第 4 列解析为经度

#### Scenario: 解析无结果响应
- **WHEN** 响应表头之后为 `No Result`
- **THEN** 系统返回空候选列表

#### Scenario: 处理错误格式响应
- **WHEN** 响应既不是正常地点列表，也不是 `No Result`
- **THEN** 系统将其视为地点搜索错误

### Requirement: 暴露地点搜索状态

系统 SHALL 区分地点搜索进行中、成功、无结果和失败状态。

#### Scenario: 搜索进行中
- **WHEN** 地点搜索请求已排队或正在执行，且当前输入关键词仍有效
- **THEN** 系统向界面提供“正在匹配地点...”的进行中状态

#### Scenario: 搜索无候选
- **WHEN** 地点搜索成功但候选列表为空
- **THEN** 系统向界面提供“没有匹配地点”的状态

#### Scenario: 搜索失败
- **WHEN** 地点搜索网络请求失败或响应解析失败
- **THEN** 系统向界面提供“地点搜索失败，请稍后重试”的状态

### Requirement: 地點搜索結果受語言版本保護
系統 SHALL 只把與目前搜索文字及目前語言版本一致的 Citybus 地點結果交給 UI。

#### Scenario: 搜索期間切換語言
- **WHEN** 地點搜索請求尚未完成且用戶切換 App 語言
- **THEN** 系統 SHALL 取消或作廢舊語言搜索
- **AND** 舊語言結果 SHALL NOT 更新候選列表或 cache

#### Scenario: 三語正常結果
- **WHEN** `bsearch_p3.php` 以 `l=0`、`l=2` 或 `l=1` 返回正常地點記錄
- **THEN** 系統 SHALL 保留上游語言的地點名稱、緯度、經度及 token 語義
- **AND** 系統 SHALL NOT 將地點名稱機器翻譯為其他語言

#### Scenario: 當前語言響應無法解析
- **WHEN** 目前語言的 Citybus 地點響應格式無法解析
- **THEN** 系統 SHALL 以目前 App 語言提供搜索失敗狀態
- **AND** 系統 SHALL NOT 改用 `l=0` 靜默重試
