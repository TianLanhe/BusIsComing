# 設備、截圖與驗收

## 先定義設備畫像

從場景契約列明：API level、螢幕解析度／寬度、密度、方向、Google APIs 或 Play Store 能力、locale、theme、font scale、鍵盤、權限、通知與 lockscreen 條件。任何硬條件不符都不能當成等價替代。

## 所有權盤點

第一次設備操作只做唯讀盤點：

```bash
adb devices -l
emulator -list-avds
```

對每個已運行 emulator 用它自己的 serial 查 AVD 名稱：

```bash
adb -s "$SERIAL" emu avd name
```

把開始時的 serial 與 AVD 名稱記入 manifest。這些設備全部屬於外部占用：不得輸入、安裝、清 data、改設定、截圖、停止或重啟。合適 AVD 已運行時等待它被外部釋放，再由本任務啟動；不要另外以同名 read-only instance 繞過占用規則。

## 啟動任務設備

只選擇當時關閉且完全符合畫像的 AVD。優先方案：

1. 使用該關閉 AVD 的 read-only、no-snapshot 任務資料層。
2. 從合適的關閉 AVD 建立精確命名的 temp clone，clone 配置與 data 位於系統暫存／任務命名空間。
3. 若工具鏈不支持可靠可丟棄層，記錄所有即將改變的 device setting，並在結束時逐項恢復；不能證明恢復就不使用。

典型啟動參數包含 `-no-snapshot-load -no-snapshot-save`；支援時加 `-read-only`。不要對持久 AVD 使用 `-wipe-data` 作捷徑。

啟動前後比較 `adb devices`，將唯一新 serial 保存為 `TASK_SERIAL`。如果不是唯一，停止並重新盤點，不能依賴 `adb` default device。後續所有命令使用：

```bash
adb -s "$TASK_SERIAL" ...
```

等待 `sys.boot_completed=1`，再驗證 API、尺寸、密度、方向與所需 package/capability。package 存在不等於 Play Store 或 Google 能力可用；依契約驗證實際 capability。

## 場景環境

在可丟棄層設定並驗證：

- App locale，而不只 system locale。
- day/night mode、font scale、方向與 animation policy。
- 固定 clock/timezone 的可行邊界；不能控制的 system time 在 manifest 說明。
- runtime permission、notification permission、lockscreen visibility、keyboard 收合。
- 沒有其他 App notification、toast、overlay、錄屏提示或 debug window。

對 notification／lockscreen 場景使用獨立 notification id/channel；開始和結束都取消。不要改真實帳戶、加入憑證或登入第三方服務。

## 截圖模式

### App area

Activity、Fragment、dialog、bottom sheet 預設用 `UiAutomation.takeScreenshot()` 取得整屏，再從當前 Activity decor view 的 `WindowInsets.Type.systemBars()` 計算 bounds，安全裁切。不能按某一設備硬編碼頂部／底部像素。

### Full screen

通知抽屜、lockscreen、permission controller 或其他 system UI 保留整屏。截圖前斷言 system surface 已真正展開且目標內容可見，不能只依 shell command 成功碼。

輸出 PNG 後立即拉到 host 的 `OUTPUT`；設備內媒體副本在 cleanup 刪除。每個檔名與 manifest 一致，不用時間戳作唯一名稱。

## 技術校驗

對最終 `OUTPUT` 執行：

```bash
python3 "$SKILL_DIR/scripts/validate-screenshot-output.py" \
  "$OUTPUT" \
  --expect '01-feature-state.png'
```

校驗器檢查 PNG signature、chunk 邊界、CRC、IHDR/IDAT/IEND、最小檔案大小、尺寸、缺檔與多餘 PNG。固定設備與 crop 尺寸已知時，使用 `--expect 'name.png=WIDTHxHEIGHT'`；只有契約真的允許額外 PNG 才加 `--allow-extra`。

## 視覺與內容校驗

用圖像查看能力逐張以原始解析度檢查：

- 功能狀態、入口、焦點和所有必須元素符合契約。
- production 字體、色彩、spacing、shape、system surface 均正常。
- 沒有截斷、重疊、鍵盤遮擋、loading、Toast、debug overlay 或過渡幀。
- app-area 沒有 system bars；full-screen 沒有被意外裁切。
- 合成資料逐項核對，沒有正式地名、真實 route、PII 或時間／ETA 矛盾。

必要時同時用 UI hierarchy、resource id 或 accessibility text 證明內容，不單靠像素印象。

## 清理證據

在 `finally` 中：

1. 取消任務 notification、停止 task service、關閉 system panel，移除設備內輸出。
2. 若不是可丟棄層，恢復逐項記錄的 locale/theme/font scale/orientation/permissions/lockscreen；逐項重讀驗證。
3. 只執行 `adb -s "$TASK_SERIAL" emu kill`，輪詢直到該 serial 消失。
4. 僅刪除本任務精確建立的 temp clone/data layer；永久 AVD 不刪除。
5. 將關閉結果、殘留檢查及失敗寫入 manifest。

若 serial 已變、設備命名不確定或不能證明目標由本任務建立，停止 destructive cleanup 並報告，不可猜測刪除。
