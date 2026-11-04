package com.tvguard.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

// Nhan: khoi dong may / cap nhat app / doi gio (TV vua co mang) / bao thuc canh gac 60 giay.
// Moi lan nhan deu khoi phuc dich vu; khoi dong may va cap nhat app con tu mo app o che do thu nho.
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (!"tv".equals(Cfg.role(c))) return;
        String a = i == null ? "" : String.valueOf(i.getAction());
        boolean watchdog = KeepService.ACTION_WATCHDOG.equals(a);
        if (!watchdog) Cfg.log(c, "Nhận broadcast " + a);
        Cfg.recover(c, watchdog ? "watchdog" : a);
        if (watchdog) {
            KeepService.scheduleWatchdog(c);   // bao thuc chi chay 1 lan -> tu dat lai
            return;
        }
        boolean boot = Intent.ACTION_BOOT_COMPLETED.equals(a)
                || "android.intent.action.QUICKBOOT_POWERON".equals(a)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(a);
        boolean replaced = Intent.ACTION_MY_PACKAGE_REPLACED.equals(a);
        if (boot || replaced) Cfg.autoLaunch(c, replaced);
    }
}
