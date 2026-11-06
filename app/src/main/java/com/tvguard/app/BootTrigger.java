package com.tvguard.app;

import android.app.Activity;
import android.os.Bundle;

// Muc khoi dong TRONG SUOT cho launcher khac (Projectivy...) goi o muc "mo ung dung khi khoi dong".
// Khong ve gi len man hinh: goi TV Guard day roi tu dong. Ten hien trong danh sach app: "TV Guard (nen)".
public class BootTrigger extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            Cfg.log(this, "BootTrigger chạy (được launcher gọi), Trợ năng đang kết nối=" + TvService.connected);
            if ("tv".equals(Cfg.role(this))) Cfg.recover(this, "trigger");
        } catch (Throwable t) { }
        finish();   // bat buoc truoc onResume voi Theme.NoDisplay
        overridePendingTransition(0, 0);
    }
}
