package com.tvguard.app;

import android.app.Application;

// Chay moi khi tien trinh app duoc tao (luc bat TV, he thong bind dich vu Tro nang, job nen, bao thuc...).
// Ghi nhat ky va khoi phuc: bat Tro nang, giu nen, len lich canh gac.
public class TvApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        try {
            if (!"tv".equals(Cfg.role(this))) return;
            Cfg.log(this, "Tiến trình app khởi động");
            Cfg.recover(this, "process");
        } catch (Exception e) { }
    }
}
