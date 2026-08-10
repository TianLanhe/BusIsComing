## Context

目前候選實作已把分享入口接到集中管理的 Google Play 商品 URL 與本地化官網下載頁，亦已換上 Lucide `Route`、將三個 MaterialButton 設為零 padding／`24dp` 圖標，並把下車 marker 由空心改為實心。然而候選「關於我們」與分享文案仍以一段列舉大量功能，三個按鈕亦重複同一組布局屬性；主 spec 的未提交修改也須移入本 change delta，避免歸檔前提前改寫生效合同。

變更跨設定資源、分享協調、路線詳情 XML／style、drawable、Canvas marker 與兩個既有 specs，但不新增資料源、狀態機或外部依賴。三語內容遵循本地化指南；路線詳情視覺遵循 UI 指南與既有相機、漸進載入及無障礙邊界。

## Goals / Non-Goals

**Goals:**

- 以精簡三語文案準確區分「香港巴士通勤」產品定位與 `Citybus` 目前能力。
- 分享一句核心價值，依 App 語言同時提供 Google Play 與官方網站下載入口。
- 以共用 style 固定三個 `48dp` 圓形控件內的 `24dp` 圖標中心，避免屬性漂移。
- 使用 Route 語義圖標及不透明下車 marker 提高地圖角色辨識。
- 保留分享失敗、相機所有權、marker 身份、TalkBack 與其他路線詳情行為。

**Non-Goals:**

- 不新增巴士營運商、第三方商店、圖片分享、短連結或網絡探測。
- 不改用 FAB／ImageButton，不調整控件位置、陰影、間距或點擊行為。
- 不改其他 marker、Citybus／ETA／CSDI／Google Maps 資料流或相機 bounds。
- 不處理手機朝向及方向箭頭精度。

## Decisions

### 1. 關於我們保留單一資源入口但使用兩段內容

`about_description` 繼續由關於頁既有 TextView 顯示，以空行分隔兩段，無需新增 layout 區塊。第一段只說產品定位、Citybus 路線、實時到站及出發時機；第二段補充常用行程、地圖詳情與通知欄監察。這比單段完整功能清單更適合小屏幕，也不把商店頁或 README 的責任搬入 App。

被否決方案是保留車費、車程、步行、自動刷新等逐項列舉，因為文案密度高且每次能力演進都需修改關於頁；亦不採泛稱「香港巴士路線」，避免暗示已支援其他營運商。

### 2. 分享重用更新／評分 URL 權威

`AppSupportActions` 繼續從 `AppUpdateLinks.PLAY_HTTPS_URL` 取得 Play 商品頁，並以 `LanguageSnapshot.effectiveLanguage` 取得官網 `#download` 頁；格式化參數順序固定為 Play 在前、網站在後。分享模板只保留一句核心價值與兩個帶標籤 URL，不新增追蹤參數、版本、裝置資料或 URL 可用性請求。

被否決方案包括只分享網站、使用裸首頁、在每個 locale 硬編碼完整 URL，以及保留功能清單式長文案。現有 ActivityNotFound 失敗 Toast 足以恢復，不新增 fallback 商店。

### 3. MaterialButton 共用 style 負責幾何居中

建立專案級路線詳情地圖控件 style，以 `Widget.MaterialComponents.Button.Icon` 為 parent，共同提供 `48dp` 外框、`24dp` 圓角、四向零 inset／padding、center gravity、`24dp` iconSize、零 iconPadding、surface 與 icon tint。layout 只保留 id、位置、margin、圖標、content description 及個別 elevation。

這保留現有 ripple、MaterialButton 觸控與主題行為；不採 FAB／ImageButton，也不為某個 vector 增加 translation 或不對稱 padding。測試除資源合同外須以 inflate 後 bounds 或裝置視覺確認幾何中心，避免只驗證 XML 文字。

### 4. 全覽路線使用 Lucide Route 並保留授權

`ic_route_overview` 使用 Lucide `Route` 的端點及相連路徑、`24 × 24` viewport、`2px` round stroke。content description 仍是操作語義「全覽路線」，不朗讀 icon library 名稱。

`Waypoints` 容易表示多點規劃，`Map` 只表達底圖，`Maximize` 偏向全屏，掃描框則明顯指向掃碼；因此均不採用。若候選 vector 與授權檔已相符，apply 不重畫相同資源，只以測試固定語義。

### 5. 下車 marker 使用完全不透明的路線色表面

marker factory 在下車分支先以目前乘車段色填滿圓形，再畫 `route_map_marker_outline` 對比外框與同色系白色 `log-out` glyph。路線色及 outline 資源需保持 alpha `255`，中央不得透出底圖。上車與下車共享圓形表面體系，以 bus／log-out glyph、時間線、站名及 TalkBack 區分角色。

不採白色表面，避免淺色底圖融入；不採方形或菱形，避免破壞現有站點圖形體系。stable id、z-index、anchor、轉乘合併、標籤避讓及相機 bounds 均不變。

### 6. 未歸檔合同只存在於 delta spec

把工作樹中對 `app-settings-support`、`route-detail-google-map` 主 spec 的候選改動還原到 HEAD 生效合同；本 change 的 delta spec 成為新行為唯一待歸檔來源。apply 完成時不同步主 spec，待後續 archive 才由 OpenSpec 工作流合併。

## Risks / Trade-offs

- [精簡文案未列出全部能力] → 關於頁與分享只承擔定位及核心價值；完整功能由主 UI、README 與商店頁呈現。
- [共用 style 抽取導致外觀漂移] → 只搬移三個節點現有共同屬性，保留個別位置／elevation，並以資源合同及實際 inflate 後控件屬性驗證。
- [上車與下車同為實心圓] → 保留高對比 bus／log-out glyph、站名、時間線與 TalkBack 多重角色資訊。
- [Route 被誤解為路線規劃] → content description 與點擊行為仍明確是全覽；不增加編輯、導航或多點操作。
- [分享目標不把純文字 URL 渲染為可點擊] → 合同只保證完整 HTTPS 文本及系統分享 Intent，不宣稱所有第三方接收 App 的渲染行為。

## Migration Plan

1. 先以 failing tests 固定新三語文案、两個 URL、共用 style、Route vector 與不透明 marker。
2. 更新最小資源、style 與協調代碼；已符合設計的候選圖標／marker 不重寫。
3. 將主 spec 候選修改還原，保留本 change delta 並完成 strict validation。
4. 運行定向單元／instrumentation 測試與完整 build，不建立截圖產物；失敗時可回退本 change 提交，不涉及資料遷移或使用者資料。

## Open Questions

無。五項產品、文案與視覺決策均已由使用者逐項確認。
