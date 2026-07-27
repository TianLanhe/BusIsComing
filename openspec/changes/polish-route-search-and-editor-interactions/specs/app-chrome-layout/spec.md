# app-chrome-layout Delta Specification

## ADDED Requirements

### Requirement: 輸入法不推移頂層底部導航
系統 SHALL 讓主 Activity 的頂層底部導航固定在屏幕物理底部；軟鍵盤顯示時 SHALL 覆蓋底部導航，使其暫時不可見，而不得把導航抬升到鍵盤上方。

#### Scenario: 搜尋輸入框拉起鍵盤
- **WHEN** 用戶在搜尋 destination 聚焦起點或終點輸入框並拉起軟鍵盤
- **THEN** 底部導航 SHALL 保持在屏幕物理底部
- **AND** 軟鍵盤 SHALL 覆蓋底部導航，使其暫時不可見
- **AND** 底部導航 SHALL NOT 顯示或重新定位到鍵盤上方

#### Scenario: 被鍵盤覆蓋的導航不可操作
- **WHEN** 軟鍵盤覆蓋底部導航
- **THEN** 用戶 SHALL NOT 能透過鍵盤上方的殘留區域觸發底部導航
- **AND** 被覆蓋的導航項 SHALL NOT 成為目前可見內容的無障礙焦點

#### Scenario: 收起鍵盤後恢復原位
- **WHEN** 用戶收起軟鍵盤
- **THEN** 底部導航 SHALL 在原屏幕底部位置重新可見
- **AND** 導航高度、三個項目的量度及目前選中 destination SHALL 保持不變
- **AND** 頁面 SHALL NOT 因導航恢復而產生額外跳動或錯誤切換

#### Scenario: 候選列表仍避開鍵盤
- **WHEN** 搜尋地點候選列表與軟鍵盤同時顯示
- **THEN** 候選列表 SHALL 依輸入法 Insets 限制自身可用高度並保持在鍵盤上方
- **AND** 底部導航的覆蓋策略 SHALL NOT 令候選項被鍵盤遮住

#### Scenario: 次級編輯頁行為不被改變
- **WHEN** 用戶在新增、編輯或複製行程等次級 Activity 拉起軟鍵盤
- **THEN** 該頁 SHALL 沿用既有內容避讓與操作可見性行為
- **AND** 主 Activity 的底部導航覆蓋策略 SHALL NOT 全域改寫次級 Activity 的窗口行為

#### Scenario: 系統版本與導航模式一致
- **WHEN** App 在受支援的舊版與新版 Android，以及手勢導航或三按鍵導航模式下顯示軟鍵盤
- **THEN** 頂層底部導航 SHALL 保持同一個「固定於物理底部並被鍵盤覆蓋」語義
- **AND** 系統導航 Insets SHALL NOT 令底部導航浮到鍵盤上方
