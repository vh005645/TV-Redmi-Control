package com.tvguard.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

// Dich vu giu nen (foreground) tren TV: giu cho tien trinh app song, va tu bat lai
// dich vu Tro nang neu bi tat (can da cap quyen WRITE_SECURE_SETTINGS qua ADB)
public class KeepService extends Service {
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean watching;

    private final Runnable watch = new Runnable() {
        @Override public void run() {
            if ("tv".equals(Cfg.role(KeepService.this)) && !Cfg.serviceEnabled(KeepService.this)) {
                Cfg.enableService(KeepService.this);
            }
            h.postDelayed(this, 5000);
        }
    };

    static void start(Context c) {
        try {
            if (!"tv".equals(Cfg.role(c))) return;
            c.startForegroundService(new Intent(c, KeepService.class));
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
        if (!watching) {
            watching = true;
            h.post(watch);
        }
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onDestroy() {
        h.removeCallbacks(watch);
        watching = false;
        super.onDestroy();
    }
}
