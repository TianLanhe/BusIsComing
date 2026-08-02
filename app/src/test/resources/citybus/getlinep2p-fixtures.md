# `getlinep2p.php` 回歸樣本

以下 fixture 於 2026-08-03 使用不帶 Cookie、session 或瀏覽器 header 的最小請求取得：

```bash
curl 'https://mobile.citybus.com.hk/nwp3/getlinep2p.php?rdv=780-CEF-1&start=6&dest=17'
curl 'https://mobile.citybus.com.hk/nwp3/getlinep2p.php?rdv=104-KET-1&start=19&dest=33'
curl 'https://mobile.citybus.com.hk/nwp3/getlinep2p.php?rdv=82X-ISR-1&start=6&dest=9'
curl 'https://mobile.citybus.com.hk/nwp3/getlinep2p.php?rdv=102-MEF-1&start=12&dest=15'
```

`82X-ISR-1` 與 `102-MEF-1` 組成測試中的兩段轉乘樣本。fixture 只用於 parser／repository 回歸測試；App 生產路徑仍須使用真實 HTTPS 請求。
