package com.tvguard.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

// Dich vu giu nen (foreground) tren TV:
//  - giu cho tien trinh app song, tu bat/ep bind lai dich vu Tro nang neu bi tat hoac chua ket noi
//  - lang nghe su kien MAN HINH BAT (TV vua bat len tu che do cho) -> khoi phuc + mo app thu nho
//  - dat bao thuc canh gac 60 giay/lan: neu HyperOS giet tien trinh luc TV tat thi bao thuc danh thuc lai
public class KeepService extends Service {
    static final String ACTION_WATCHDOG = "com.tvguard.app.WATCHDOG";
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean watching;
    private boolean receiverOn;

    private final Runnable watch = new Runnable() {
        @Override public void run() {
            if ("tv".equals(Cfg.role(KeepService.this))) {
                Cfg.ensureService(KeepService.this);
                // vong lap Firebase khong co nhip tim > 60 giay (mang treo sau khi TV bat lai...) -> tao vong lap moi
                TvService s = TvService.inst;
                long now = SystemClock.elapsedRealtime();
                if (s != null && TvService.connected && now - TvService.beat > 60000) {
                    Cfg.log(KeepService.this, "Vòng lặp Firebase bị treo " + (now - TvService.beat) / 1000 + "s -> khởi động lại");
                    s.restartLoop();
                }
            }
            h.postDelayed(this, 5000);
        }
    };

    // TV vua bat len: man hinh sang / het che do cho / mo khoa
    private final BroadcastReceiver wake = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String a = i == null ? "?" : String.valueOf(i.getAction());
            Cfg.log(c, "TV bật lại (" + a.replace("android.intent.action.", "") + "), dịch vụ " + (TvService.connected ? "đang kết nối" : "CHƯA kết nối"));
            Cfg.recover(c, a);
            TvService s = TvService.inst;
            if (s != null && TvService.connected) s.restartLoop();   // ket noi mang moi sau khi thoat che do cho
            Cfg.autoLaunch(c, false);
        }
    };

    static void start(Context c) {
        try {
            if (!"tv".equals(Cfg.role(c))) return;
            c.startForegroundService(new Intent(c, KeepService.class));
        } catch (Exception e) { }
    }

    // Bao thuc lap lai 60 giay: song sot qua viec tien trinh bi giet (khong song sot qua khoi dong lai may -> WakeJob/BootReceiver lo)
    static void scheduleWatchdog(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            Intent in = new Intent(c, BootReceiver.class).setAction(ACTION_WATCHDOG);
            PendingIntent pi = PendingIntent.getBroadcast(c, 77, in, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 60000L, pi);
        } catch (Exception e) { }
    }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel("keep", "TV Guard", NotificationManager.IMPORTANCE_MIN));
            Notification n = new Notification.Builder(this, "keep")
                    .setContentTitle("TV Guard đang chạy")
                    .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                    .build();
            startForeground(1, n);
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!receiverOn) {
            try {
                IntentFilter f = new IntentFilter();
                f.addAction(Intent.ACTION_SCREEN_ON);
                f.addAction(Intent.ACTION_USER_PRESENT);
                f.addAction(Intent.ACTION_DREAMING_STOPPED);
                f.addAction(Intent.ACTION_POWER_CONNECTED);
                registerReceiver(wake, f);
                receiverOn = true;
            } catch (Exception e) { }
        }
        if (!watching) {
            watching = true;
            h.post(watch);
        }
        scheduleWatchdog(this);
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onDestroy() {
        h.removeCallbacks(watch);
        watching = false;
        if (receiverOn) {
            try { unregisterReceiver(wake); } catch (Exception e) { }
            receiverOn = false;
        }
        super.onDestroy();
    }
}
