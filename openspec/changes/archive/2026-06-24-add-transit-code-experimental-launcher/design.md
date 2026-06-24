
- 一屏可明確看到所有候選，方便真機逐條測試。
- 平台分組比純調試表格更可理解。
- 不需要二級導航，降低測試操作成本。

替代方案：

- 先選平台再選入口：更像正式產品，但實驗期多一層操作，不利於快速對比。
- 純調試表格：工程判讀清楚，但不符合 App 內普通 UI 風格。

### Decision 3: 候選入口固定在 App 內，不走遠端配置

第一版在 App 內固定 7 條候選入口：

- 微信 `jumpWxa`
- 微信 `dl/business` 無 path
- 微信 `dl/business` 帶 `pages/index/index`
- 支付寶 `appId`
- 支付寶 `saId`
- 支付寶 H5 render
- 支付寶 `ds.alipay.com` 包裝

原因：

- 目前目標是小範圍實驗，不需要引入遠端配置或同步機制。
- 固定清單可被單元測試精確覆蓋。
- 避免外部配置錯誤導致無法審查 URI 來源。

替代方案：

- 遠端配置入口：後續可快速更新 URI，但第一版會引入不必要的後端、快取和安全審查成本。
- 只保留一條「最可能成功」入口：用戶體驗簡單，但無法回答哪條候選實際可用。

### Decision 4: 不自動 fallback

每次點擊只嘗試當前入口。即使失敗，也不自動嘗試同平台下一條候選。

原因：

- 實驗目標是辨識每條入口的真機結果，自動 fallback 會混淆成功來源。
- 外部 App 跳轉可能彈出系統確認框或中轉頁，自動連續嘗試會造成不可預期體驗。

替代方案：

- 依可信度自動 fallback：正式產品可能有價值，但不適合本次驗證階段。

### Decision 5: 輕量啟動器封裝外部 Intent

新增或使用輕量模型表示候選入口，例如：

```text
TransitCodeLaunchTarget
  - provider: WECHAT / ALIPAY
  - title: String
  - description: String
  - uri: Uri
```

啟動器集中處理：

- `Intent.ACTION_VIEW`
- `startActivity`
- `ActivityNotFoundException`
- `SecurityException`
- 其他非預期異常

UI 層根據 provider 與結果展示 toast。

原因：

- URI/URL 和錯誤處理集中可測，避免散落在 `MainActivity`。
- Activity 只負責展示、點擊和 toast，保持分層清晰。
- 不需要 repository、SQLite、HTTP 或 Citybus parser 參與。

替代方案：

- 直接在 `MainActivity` 內逐條寫 Intent：初期更快，但測試和維護成本高。
- 新增完整 repository/service：過度設計，這不是資料存取或背景能力。

### Decision 6: 增加 package visibility

在既有 `<queries>` 中合併：

```xml
<package android:name="com.tencent.mm" />
<package android:name="com.eg.android.AlipayGphone" />
```

原因：

- Android 11+ 對 package visibility 有限制。
- 目前或後續若做安裝檢測、`resolveActivity` 或 package 可見性判斷，需要明確聲明。
- 即使第一版只捕獲 `startActivity` 異常，保留查詢聲明也有助於後續演進。

替代方案：

- 不增加 `<queries>`：對單純 `startActivity` 可能仍可工作，但會限制安裝檢測和錯誤分類。

## Risks / Trade-offs

- 微信 `jumpWxa` 非官方，可能在部分微信版本無效 → 將入口標為實驗性，失敗時提示用戶嘗試其他入口或手動打開微信。
- 微信 `dl/business` 可能要求目標小程序已開啟明文 Scheme，且 `path` 必須存在 → 同時提供無 path 與 `pages/index/index` 兩條候選，並在文檔中明確 `pages/index/index` 不是已確認的騰訊乘車碼 path。
- 支付寶 `saId`、render H5、ds 包裝入口可信度不同 → 每條入口獨立展示與測試，不在第一版宣稱哪條為正式入口。
- 入口數量較多，普通用戶可能覺得工程化 → 標題使用「實驗性乘車碼入口」，並按平台分組降低理解成本。
- 外部 App 成功打開後 App 無法知道是否到達乘車碼頁 → 驗收標準不要求 App 自動判斷到達頁面，需人工真機記錄。
- 主頁頂部可能變擠 → 使用短文案「乘車碼」，並遵循既有間距、觸控目標和字體縮放驗證。

## Migration Plan

1. 新增 UI 入口與底部彈層，不修改既有查詢流程。
2. 新增啟動器與候選入口清單，補 URI/URL 構造和失敗處理測試。
3. 合併 Android Manifest package visibility。
4. 執行自動化測試與 `./gradlew build`。
5. 在可安裝微信/支付寶的真機上逐條記錄 7 條入口結果。

Rollback 策略：

- 若實驗入口不可用或影響主頁布局，可移除主頁「乘車碼」按鈕和彈層入口；不涉及資料遷移或 schema 回滾。

## Open Questions

- 無阻塞實作的開放問題。
- 真機驗證後可再決定是否保留全部 7 條入口、收斂為單一正式入口，或改為接入微信 OpenSDK / 更精確的支付寶 page/query。
