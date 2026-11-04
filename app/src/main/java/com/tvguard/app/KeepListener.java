package com.tvguard.app;

import android.service.notification.NotificationListenerService;

// Dich vu "neo": he thong tu khoi dong lai dich vu nay neu tien trinh app bi giet (khac voi Tro nang, thuong
// khong bi bo vao danh sach "da loi"). Moi lan ket noi lai thi khoi phuc Tro nang + dich vu giu nen.
public class KeepListener extends NotificationListenerService {
    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        try {
            if (!"tv".equals(Cfg.role(this))) return;
            Cfg.log(this, "Dịch vụ neo kết nối (trợ năng " + (TvService.connected ? "đang kết nối" : "CHƯA kết nối") + ")");
            Cfg.recover(this, "neo");
        } catch (Exception e) { }
    }
}
