# `getlinep2p.php` 回歸樣本

以下 fixture 於 2026-08-03 使用不帶 Cookie、session 或瀏覽器 header 的最小請求取得：

```bash
curl 'https://mobile.citybus.com.hk/nwp3/getlinep2p.php?rdv=780-CEF-1&start=6&dest=17'
curl 'https://mobile.citybus.com.hk/nwp3/getlinep2p.php?rdv=104-KET-1&start=19&dest=33'
curl 'https://mobile.citybus.com.hk/nwp3/getlinep2p.php?rdv=82X-ISR-1&start=6&dest=9'
curl 'https://mobile.citybus.com.hk/nwp3/getlinep2p.php?rdv=102-MEF-1&start=12&dest=15'
curl 'https://mobile.citybus.com.hk/nwp3/getlinep2p.php?rdv=N118-TOS-1&start=5&dest=9'
```

`82X-ISR-1` 與 `102-MEF-1` 組成測試中的兩段轉乘樣本。fixture 只用於 parser／repository 回歸測試；App 生產路徑仍須使用真實 HTTPS 請求。

`N118-TOS-1` 於 2026-08-04 重新以最小請求取得，用於固定 Citybus 舊底圖坐標與 Google Maps WGS84 的對齊回歸。parser 仍保存 fixture 原值；repository 對每個路線點套用 `latitude + 0.0001935197`、`longitude - 0.0000697374` 後才進行端點驗證及成功快取。該 provider-specific 校正不適用於站點、查詢端點、設備位置或 Google 資料。
