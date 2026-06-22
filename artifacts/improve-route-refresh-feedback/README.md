# `improve-route-refresh-feedback` 模擬器驗證記錄

驗證日期：2026-06-22

## 自動化驗證

- Pixel 8 API 36、1080 × 2400、字體縮放 1.0：`./gradlew connectedDebugAndroidTest`，14 項測試全部通過。
- 替代尺寸 1080 × 1920、字體縮放 1.15：地點候選與臨時查詢重點測試 3 項全部通過，測試後已恢復模擬器尺寸與字體設定。
- 刷新測試覆蓋進行中、非空成功、空結果成功、失敗保留舊結果、500ms 成功收尾及查詢按鈕鎖定。
- 詳情測試覆蓋大量途經站展開、巢狀上下滾動及回到頂部。
- 地點候選測試覆蓋最多 100 條、輸入法可見、至少 3 條可點選、列表獨立滾動、焦點切換、第一次返回只關閉候選及不自動提交。

## 截圖

- `refresh-progress.png`：固定刷新浮層、舊結果保留及查詢按鈕停用。
- `refresh-success.png`：新結果、更新時間及列表回到頂部。成功勾號由 instrumentation 同步斷言；系統截圖命令完成時已超過 500ms 收尾時間。
- `refresh-failure-preserved.png`：失敗 Toast、原結果及原更新時間保留。
- `route-detail-expanded.png`：大量途經站展開後的長內容滾動。
- `inline-place-candidates.png`：一般路線表單的內嵌候選列表與輸入法。
- `temporary-inline-candidates.png`：臨時查詢近全螢幕候選列表與輸入法。
