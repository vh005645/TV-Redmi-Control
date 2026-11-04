package com.tvguard.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

// Khi TV khoi dong (hoac app vua duoc cap nhat): neu may nay la TV thi bat lai dich vu Tro nang
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (!"tv".equals(Cfg.role(c))) return;
        Cfg.enableService(c);
        KeepService.start(c);
    }
}
