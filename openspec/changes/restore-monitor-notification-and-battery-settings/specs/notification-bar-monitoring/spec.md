## ADDED Requirements

### Requirement: 啟動面板展示監控通知準備狀態
系統 SHALL 在通知欄監控啟動面板以目前 App 語言展示通知與鎖屏的準備狀態，並讓用戶在開始前處理已知設定問題。

#### Scenario: 面板展示已就緒狀態
- **WHEN** App 通知總開關及普通監控渠道沒有公開 API 可確定的 blocking 問題
- **THEN** 啟動面板 SHALL 展示通知監控可啟動的狀態
- **AND** 系統 SHALL NOT 將此狀態描述為保證所有設備都會在鎖屏顯示

#### Scenario: 面板展示需要設定狀態
- **WHEN** channel 健康檢查發現 blocking 或 warning
- **THEN** 啟動面板 SHALL 以目前 App 語言展示需要處理或確認的狀態
- **AND** 面板 SHALL 提供至少 48dp 觸控目標的設定操作
- **AND** 設定操作 SHALL 具有目前 App 語言的無障礙內容描述

#### Scenario: blocking 狀態攔截開始監控
- **WHEN** 用戶在 App 通知總開關關閉或普通監控渠道停用時點擊開始監控
- **THEN** 系統 SHALL 保存本次路線、步行分鐘及語音選擇
- **AND** 系統 SHALL 開啟可用的最具體通知設定頁
- **AND** 系統 SHALL NOT 在問題修復前啟動前台監控服務

#### Scenario: warning 或未知狀態仍可開始
- **WHEN** 緊急提醒渠道異常、鎖屏明確隱藏或平台無法確認最終鎖屏呈現
- **AND** 普通監控渠道仍可發出可見常駐通知
- **THEN** 系統 SHALL 允許用戶開始基本監控
- **AND** 面板 SHALL 保留對應設定入口或說明

#### Scenario: 三語與自適應版面
- **WHEN** 啟動面板以香港繁體、簡體或英文顯示監控準備狀態
- **THEN** 所有狀態、操作、失敗與手動引導文案 SHALL 使用目前 App 語言
- **AND** 文字 SHALL 在 360dp 及 font scale 1.0、1.3、2.0 下可換行且不裁切核心操作
