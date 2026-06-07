# BusIsComing

BusIsComing 是一個用於查詢香港巴士路線與到站資訊的 Android App。它以「常用路線快速查詢」為核心，讓用戶可以保存起點與終點，並在主頁快速查詢可用巴士方案、港幣票價、預計候車時間、步行距離與路線詳情。

## 功能特色

- 管理常用路線：新增、編輯、複製與刪除常用起終點配置。
- 地點搜尋：使用 Citybus 地點搜尋資料選擇有效起點與終點。
- 主頁快捷查詢：按使用習慣展示常用路線快捷卡，支持完整常用路線列表。
- 臨時查詢：可臨時輸入起點與終點查詢，並可保存為常用路線。
- 路線結果：展示巴士路線、HK$ 票價、預計候車時間、步行距離與路線耗時。
- 結果排序：支持按路線、價格、耗時、候車時間和步行距離排序。
- 路線詳情：支持查看路線分段、上落車站點與途經站點。

## 技術棧

- 語言：Kotlin
- UI：XML + AppCompat + Material Components
- 本地存儲：SQLiteOpenHelper
- 列表：RecyclerView
- HTML 解析：jsoup
- 架構風格：輕量 Repository 分層
- 構建：Gradle Kotlin DSL，Android Gradle Plugin 9.2.1

## 專案結構

```text
BusIsComing/
├── app/                 Android App 模組
├── docs/                產品設計、UI 風格和資料推導文檔
├── gradle/              Gradle wrapper 與 version catalog
├── openspec/            OpenSpec 變更提案、規格和任務
├── AGENTS.md            專案級 AI coding agent 約定
├── build.gradle.kts     根專案構建配置
└── settings.gradle.kts  Gradle settings
```

## 開發環境

- Android Studio：建議使用支持 Android Gradle Plugin 9.x 的版本
- JDK：Java 11 兼容工具鏈
- Android SDK：compileSdk 36.1，minSdk 25，targetSdk 36

首次克隆後可以直接使用 Gradle wrapper：

```bash
./gradlew build
```

## 常用命令

構建、測試與 lint：

```bash
./gradlew build
```

僅運行本地單元測試：

```bash
./gradlew test
```

安裝 debug 包到已連接設備或模擬器：

```bash
./gradlew :app:installDebug
```

查看已連接 Android 設備：

```bash
adb devices
```

## OpenSpec 工作流

本專案使用 OpenSpec 管理需求、設計和實作任務。主要內容位於：

- `openspec/specs/`：已歸檔並生效的能力規格
- `openspec/changes/`：進行中的變更提案、設計和任務
- `openspec/changes/archive/`：已完成並歸檔的變更

常用校驗命令：

```bash
openspec validate <change-name> --strict
```

新增或修改 App 文案、測試期望、文檔和 OpenSpec 人類可讀內容時，中文默認使用繁體中文；外部 API 樣例、Citybus 原始 fixture 或第三方來源文字保持原文。

## 資料與網絡

App 目前通過 Citybus 相關資料接口與頁面結果解析查詢路線和到站資訊。外部服務的可用性、回傳格式或網絡狀態可能影響查詢結果。專案中的 Citybus fixture 和測試資源只用於解析與回歸測試。

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
