package com.tvguard.app;

import android.app.Application;

// Chay moi khi tien trinh app duoc tao (luc bat TV, he thong bind dich vu Tro nang, job nen...).
// Ghi nhat ky va dam bao dich vu Tro nang duoc bat + job canh gac da len lich.
public class TvApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        try {
            if (!"tv".equals(Cfg.role(this))) return;
            Cfg.log(this, "Tiến trình app khởi động");
            Cfg.enableService(this);
            WakeJob.schedule(this);
        } catch (Exception e) { }
    }
}
