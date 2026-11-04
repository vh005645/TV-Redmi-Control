package com.tvguard.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;

public class TvService extends AccessibilityService {
    static volatile boolean connected = false;
    static volatile String lastMsg = "";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean running = false;
    private WindowManager wm;
    private View lockView, imgView;
    private String shownMode = "";
    private final Runnable hideImgR = this::hideImage;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        connected = true;
        if (!running) {
            running = true;
            new Thread(this::loop, "tvguard").start();
        }
    }

    // Chan tre mo Cai dat / go cai dat de tat dich vu (tru khi phu huynh cho phep tam thoi)
    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e == null || e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pk = e.getPackageName();
        if (pk == null || !isProtected(pk.toString())) return;
        if (System.currentTimeMillis() < Cfg.sp(this).getLong("allowUntil", 0)) return;
        performGlobalAction(GLOBAL_ACTION_HOME);
    }

    private boolean isProtected(String p) {
        p = p.toLowerCase();
        return p.contains("settings") || p.contains("packageinstaller") || p.contains("permissioncontroller");
    }

    // Khi dang KHOA: vo hieu hoa moi phim cua dieu khien, chi cho nut Nguon
    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        if (!Cfg.sp(this).getBoolean("locked", false)) return false;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_TV_POWER:
            case KeyEvent.KEYCODE_STB_POWER:
            case KeyEvent.KEYCODE_SLEEP:
            case KeyEvent.KEYCODE_WAKEUP:
            case KeyEvent.KEYCODE_SOFT_SLEEP:
                return false;
            default:
                return true;
        }
    }
    @Override public void onInterrupt() { }

    @Override
    public boolean onUnbind(Intent i) {
        running = false;
        connected = false;
        ui.post(() -> { hideLock(); hideImage(); });
        return super.onUnbind(i);
    }

    // ---------- vong lap nen: hoi lenh tu Firebase moi 2 giay ----------
    private void loop() {
        int tick = 0;
        while (running) {
            SharedPreferences sp = Cfg.sp(this);
            try {
                // Hen gio chay cuc bo, khong phu thuoc mang (rut day mang van bi khoa)
                checkTimer(sp);
                if (!Cfg.urlOk(this)) {
                    lastMsg = "Chưa cấu hình URL Firebase (xem hướng dẫn)";
                } else {
                    String base = Cfg.dbUrl(this) + "/tv/" + Cfg.tvCode(this);
                    JSONObject cmd = Net.getObj(base + "/cmd.json");
                    if (!sp.contains("lastId")) {
                        sp.edit().putLong("lastId", cmd != null ? cmd.optLong("id") : 0).apply();
                    } else if (cmd != null) {
                        long id = cmd.optLong("id");
                        if (id != sp.getLong("lastId", 0)) {
                            sp.edit().putLong("lastId", id).apply();
                            handle(cmd, base);
                        }
                    }
                    checkTimer(sp);
                    if (tick % 3 == 0) pushStatus(base, sp);
                    lastMsg = "Kết nối OK";
                }
            } catch (Exception e) {
                lastMsg = "Lỗi: " + Net.explain(e);
            }
            ui.post(this::applyLock);
            tick++;
            try { Thread.sleep(2000); } catch (InterruptedException ie) { break; }
        }
    }

    private void checkTimer(SharedPreferences sp) {
        long end = sp.getLong("endAt", 0);
        if (end > 0 && System.currentTimeMillis() >= end && !sp.getBoolean("locked", false)) {
            sp.edit().putBoolean("locked", true).putLong("endAt", 0).apply();
        }
    }

    private void pushStatus(String base, SharedPreferences sp) throws Exception {
        long now = System.currentTimeMillis();
        long end = sp.getLong("endAt", 0);
        JSONObject o = new JSONObject();
        o.put("ts", now);
        o.put("locked", sp.getBoolean("locked", false));
        o.put("timer", end > 0);
        o.put("remain", end > now ? (end - now) / 1000 : 0);
        o.put("mode", sp.getString("mode", "black"));
        o.put("img", imgView != null);
        o.put("allow", sp.getLong("allowUntil", 0) > now);
        o.put("hidden", Cfg.iconHidden(this));
        Net.put(base + "/status.json", o.toString());
    }

    private void handle(JSONObject c, String base) throws Exception {
        SharedPreferences sp = Cfg.sp(this);
        String type = c.optString("type");
        String mode = c.optString("mode", sp.getString("mode", "black"));
        long now = System.currentTimeMillis();
        switch (type) {
            case "settimer": {
                long min = c.optLong("minutes", 60);
                sp.edit().putLong("endAt", now + min * 60000L).putBoolean("locked", false).putString("mode", mode).apply();
                break;
            }
            case "addtime": {
                long min = c.optLong("minutes", 15);
                long b = Math.max(now, sp.getLong("endAt", 0));
                sp.edit().putLong("endAt", b + min * 60000L).putBoolean("locked", false).putString("mode", mode).apply();
                break;
            }
            case "lock":
                sp.edit().putBoolean("locked", true).putLong("endAt", 0).putString("mode", mode).apply();
                break;
            case "unlock":
                sp.edit().putBoolean("locked", false).putLong("endAt", 0).apply();
                break;
            case "shot":
                takeShot(base, c.optLong("id"));
                break;
            case "showimg": {
                JSONObject o = Net.getObj(base + "/img.json");
                if (o != null) {
                    final Bitmap b = ImgUtil.fromB64(o.getString("data"));
                    final int sec = c.optInt("seconds", 30);
                    ui.post(() -> showImage(b, sec));
                }
                break;
            }
            case "hideimg":
                ui.post(this::hideImage);
                break;
            case "allowsettings": {
                long min = c.optLong("minutes", 10);
                sp.edit().putLong("allowUntil", min > 0 ? now + min * 60000L : 0).apply();
                break;
            }
            case "hideicon":
                Cfg.setIconHidden(this, true);
                break;
            case "showicon":
                Cfg.setIconHidden(this, false);
                break;
            case "lockimg": {
                JSONObject o = Net.getObj(base + "/lockimg.json");
                if (o != null) {
                    byte[] d = Base64.decode(o.getString("data"), Base64.DEFAULT);
                    FileOutputStream f = new FileOutputStream(new File(getFilesDir(), "lock.jpg"));
                    f.write(d);
                    f.close();
                    ui.post(() -> { if (lockView != null) hideLock(); });
                }
                break;
            }
            default:
                break;
        }
    }

    // ---------- chup man hinh (Android 11+) ----------
    private void takeShot(final String base, final long id) {
        takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
            @Override
            public void onSuccess(ScreenshotResult r) {
                Bitmap hw = Bitmap.wrapHardwareBuffer(r.getHardwareBuffer(), r.getColorSpace());
                Bitmap sw = hw == null ? null : hw.copy(Bitmap.Config.ARGB_8888, false);
                r.getHardwareBuffer().close();
                upload(base, id, sw, 0);
            }

            @Override
            public void onFailure(int errorCode) {
                upload(base, id, null, errorCode);
            }
        });
    }

    private void upload(final String base, final long id, final Bitmap b, final int err) {
        new Thread(() -> {
            try {
                JSONObject o = new JSONObject();
                o.put("id", id);
                if (b != null) o.put("data", ImgUtil.toB64(b, 1280, 60));
                else o.put("err", err);
                Net.put(base + "/shot.json", o.toString());
                Net.put(base + "/shotid.json", String.valueOf(id));
            } catch (Exception e) {
                lastMsg = "Lỗi gửi ảnh: " + e.getMessage();
            }
        }).start();
    }

    // ---------- khoa man hinh ----------
    private void applyLock() {
        SharedPreferences sp = Cfg.sp(this);
        boolean locked = sp.getBoolean("locked", false);
        String mode = sp.getString("mode", "black");
        if (locked) {
            if (lockView == null || !mode.equals(shownMode)) {
                boolean first = lockView == null;
                showLock(mode);
                if (first && "sleep".equals(mode)) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
            }
            mute(true);
        } else if (lockView != null) {
            hideLock();
            mute(false);
        }
    }

    private void mute(boolean m) {
        try {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    m ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE, 0);
        } catch (Exception e) { }
    }

    private WindowManager.LayoutParams overlayParams() {
        return new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
    }

    private void showLock(String mode) {
        hideLock();
        FrameLayout f = new FrameLayout(this);
        f.setBackgroundColor(Color.BLACK);
        boolean has = false;
        if ("image".equals(mode)) {
            File ff = new File(getFilesDir(), "lock.jpg");
            if (ff.exists()) {
                Bitmap b = BitmapFactory.decodeFile(ff.getPath());
                if (b != null) {
                    ImageView iv = new ImageView(this);
                    iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    iv.setImageBitmap(b);
                    f.addView(iv, new FrameLayout.LayoutParams(-1, -1));
                    has = true;
                }
            }
        }
        if (!has && !"sleep".equals(mode)) {
            TextView t = new TextView(this);
            t.setText("Hết giờ xem TV\nCon hãy nghỉ ngơi nhé!");
            t.setTextColor(Color.WHITE);
            t.setTextSize(44);
            t.setGravity(Gravity.CENTER);
            f.addView(t, new FrameLayout.LayoutParams(-1, -1));
        }
        try {
            wm.addView(f, overlayParams());
            lockView = f;
            shownMode = mode;
        } catch (Exception e) {
            lastMsg = "Overlay lỗi: " + e.getMessage();
        }
    }

    private void hideLock() {
        if (lockView != null) {
            try { wm.removeView(lockView); } catch (Exception e) { }
            lockView = null;
            shownMode = "";
        }
    }

    // ---------- anh de len man hinh ----------
    private void showImage(Bitmap b, int sec) {
        hideImage();
        if (b == null) return;
        FrameLayout f = new FrameLayout(this);
        f.setBackgroundColor(Color.BLACK);
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setImageBitmap(b);
        f.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        try {
            wm.addView(f, overlayParams());
            imgView = f;
        } catch (Exception e) {
            lastMsg = "Overlay lỗi: " + e.getMessage();
        }
        if (sec > 0) ui.postDelayed(hideImgR, sec * 1000L);
    }

    private void hideImage() {
        ui.removeCallbacks(hideImgR);
        if (imgView != null) {
            try { wm.removeView(imgView); } catch (Exception e) { }
            imgView = null;
        }
    }
}
