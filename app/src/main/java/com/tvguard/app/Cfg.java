package com.tvguard.app;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.provider.Settings;
import java.security.SecureRandom;

public class Cfg {
    static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("cfg", Context.MODE_PRIVATE);
    }

    // URL Firebase: uu tien gia tri nhap trong app, neu khong co thi lay strings.xml
    static String dbUrl(Context c) {
        String u = sp(c).getString("dburl", "");
        if (u == null || u.trim().isEmpty()) u = c.getString(R.string.db_url);
        return normUrl(u);
    }

    static String normUrl(String u) {
        u = u == null ? "" : u.trim();
        if (u.isEmpty()) return "";
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u;
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        if (u.endsWith(".json")) u = u.substring(0, u.length() - 5);
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    static void setDbUrl(Context c, String u) { sp(c).edit().putString("dburl", normUrl(u)).apply(); }

    static String savedDbUrl(Context c) { return sp(c).getString("dburl", ""); }

    static boolean urlOk(Context c) {
        String u = dbUrl(c);
        return !u.isEmpty() && !u.contains("TEN-PROJECT");
    }

    static String role(Context c) { return sp(c).getString("role", ""); }
    static void setRole(Context c, String r) { sp(c).edit().putString("role", r).apply(); }

    // Ma TV co dinh lay tu strings.xml (tv_code). Neu de trong thi sinh ngau nhien 1 lan va luu lai
    static String fixedCode(Context c) {
        return c.getString(R.string.tv_code).trim().toLowerCase();
    }

    // Ma TV nhap tay (de trong = dung ma co dinh)
    static String savedTvCode(Context c) { return sp(c).getString("tvcode_custom", ""); }

    static boolean validCode(String s) { return s != null && s.matches("[a-z0-9]{4,40}"); }

    // Tra ve false neu ma khong hop le (chi gom a-z, 0-9, 4-40 ky tu). Rong = quay ve ma co dinh
    static boolean setTvCode(Context c, String s) {
        s = s == null ? "" : s.trim().toLowerCase();
        if (!s.isEmpty() && !validCode(s)) return false;
        sp(c).edit().putString("tvcode_custom", s.equals(fixedCode(c)) ? "" : s).remove("lastId").apply();
        return true;
    }

    static synchronized String tvCode(Context c) {
        String cu = savedTvCode(c);
        if (!cu.isEmpty()) return cu;
        String f = fixedCode(c);
        if (!f.isEmpty()) return f;
        String s = sp(c).getString("tvcode", null);
        if (s == null) {
            String a = "abcdefghjkmnpqrstuvwxyz23456789";
            SecureRandom r = new SecureRandom();
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 10; i++) b.append(a.charAt(r.nextInt(a.length())));
            s = b.toString();
            sp(c).edit().putString("tvcode", s).apply();
        }
        return s;
    }

    // Dien thoai: mac dinh dung ma co dinh; chi luu rieng khi nguoi dung nhap ma khac
    static String parentCode(Context c) {
        String s = sp(c).getString("pcode2", "");
        return s.isEmpty() ? fixedCode(c) : s;
    }

    static void setParentCode(Context c, String s) {
        sp(c).edit().putString("pcode2", s.equals(fixedCode(c)) ? "" : s).apply();
    }

    // ---------- dich vu Tro nang ----------
    static final String WSS = "android.permission.WRITE_SECURE_SETTINGS";

    static String serviceComponent(Context c) {
        return c.getPackageName() + "/" + TvService.class.getName();
    }

    static boolean serviceEnabled(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(c.getPackageName() + "/");
    }

    static boolean canWriteSecure(Context c) {
        return c.checkSelfPermission(WSS) == PackageManager.PERMISSION_GRANTED;
    }

    // Dich vu "neo" (NotificationListener): he thong tu khoi dong lai dich vu nay khi tien trinh app bi giet,
    // moi lan no ket noi lai thi app chay phan khoi phuc (ep bind lai Tro nang, mo dich vu giu nen)
    static void enableListener(Context c) {
        try {
            String comp = new ComponentName(c, KeepListener.class).flattenToString();
            String cur = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
            if (cur == null) cur = "";
            if (!cur.contains(comp))
                Settings.Secure.putString(c.getContentResolver(), "enabled_notification_listeners", cur.isEmpty() ? comp : cur + ":" + comp);
        } catch (Exception e) { }
    }

    // Ghi vao nhat ky ly do tien trinh truoc do bi ket thuc (API 30+): biet chinh xac HyperOS giet app vi sao
    static void logExitReasons(Context c) {
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            java.util.List<android.app.ApplicationExitInfo> l = am.getHistoricalProcessExitReasons(c.getPackageName(), 0, 6);
            long seen = sp(c).getLong("exitSeen", 0), max = seen;
            for (int i = l.size() - 1; i >= 0; i--) {
                android.app.ApplicationExitInfo e = l.get(i);
                if (e.getTimestamp() <= seen) continue;
                max = Math.max(max, e.getTimestamp());
                String d = e.getDescription();
                log(c, "Tiến trình trước kết thúc lúc "
                        + new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date(e.getTimestamp()))
                        + ": " + exitName(e.getReason()) + (d == null || d.isEmpty() ? "" : " - " + d));
            }
            if (max > seen) sp(c).edit().putLong("exitSeen", max).apply();
        } catch (Exception e) { }
    }

    private static String exitName(int r) {
        switch (r) {
            case 1: return "EXIT_SELF (app tự thoát)";
            case 2: return "SIGNALED (bị giết bằng tín hiệu)";
            case 3: return "LOW_MEMORY (thiếu RAM)";
            case 4: return "CRASH (app lỗi)";
            case 5: return "CRASH_NATIVE";
            case 6: return "ANR (treo)";
            case 7: return "INITIALIZATION_FAILURE";
            case 8: return "PERMISSION_CHANGE (đổi quyền)";
            case 9: return "EXCESSIVE_RESOURCE_USAGE";
            case 10: return "USER_REQUESTED (người dùng/Force stop)";
            case 11: return "USER_STOPPED";
            case 12: return "DEPENDENCY_DIED";
            case 13: return "OTHER";
            case 14: return "FREEZER (bị đóng băng)";
            case 15: return "PACKAGE_STATE_CHANGE";
            case 16: return "PACKAGE_UPDATED";
            default: return "mã " + r;
        }
    }

    // Can da cap quyen WRITE_SECURE_SETTINGS qua ADB (1 lan)
    static boolean enableService(Context c) {
        if (!canWriteSecure(c)) return false;
        enableListener(c);
        try {
            String comp = serviceComponent(c);
            String cur = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (cur == null) cur = "";
            if (!cur.contains(comp)) {
                String nv = cur.isEmpty() ? comp : cur + ":" + comp;
                Settings.Secure.putString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, nv);
            }
            Settings.Secure.putInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- nhat ky khoi dong (hien tren man hinh app TV de biet HyperOS chan khau nao) ----------
    static synchronized void log(Context c, String ev) {
        try {
            String t = new java.text.SimpleDateFormat("dd/MM HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
            long up = SystemClock.elapsedRealtime() / 1000;
            String line = t + " [máy bật " + up + "s] " + ev;
            String cur = sp(c).getString("bootlog", "");
            String[] ls = cur.isEmpty() ? new String[0] : cur.split("\n");
            StringBuilder b = new StringBuilder();
            int from = Math.max(0, ls.length - 79);
            for (int i = from; i < ls.length; i++) b.append(ls[i]).append("\n");
            b.append(line);
            sp(c).edit().putString("bootlog", b.toString()).commit();
        } catch (Exception e) { }
    }

    static String bootLog(Context c) { return sp(c).getString("bootlog", ""); }

    // ---------- khoi phuc khi bat TV ----------
    static final long PROC_START = SystemClock.elapsedRealtime();
    private static long lastRebind = 0;
    private static final long LAUNCH_COOLDOWN = 60 * 1000L;

    // Dam bao dich vu Tro nang dang BAT va dang KET NOI. Neu da bat trong Settings nhung he thong
    // chua bind (HyperOS hay lam vay sau khi TV bat lai) thi tat/bat lai de ep he thong bind lai.
    static synchronized void ensureService(Context c) {
        if (!"tv".equals(role(c)) || !canWriteSecure(c)) return;
        try {
            if (!serviceEnabled(c)) { enableService(c); return; }
            if (TvService.connected) return;
            long now = SystemClock.elapsedRealtime();
            // cho he thong tu bind 6 giay dau sau khi tien trinh khoi dong, roi moi ep; moi lan ep cach nhau >= 15 giay
            if (now - PROC_START < 6000 || now - lastRebind < 15000) return;
            lastRebind = now;
            final String comp = serviceComponent(c);
            String cur = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (cur == null) cur = "";
            StringBuilder b = new StringBuilder();
            for (String part : cur.split(":")) {
                if (part.isEmpty() || part.equals(comp)) continue;
                if (b.length() > 0) b.append(':');
                b.append(part);
            }
            Settings.Secure.putString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, b.toString());
            final Context app = c.getApplicationContext();
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> enableService(app), 800);
            log(c, "Ép bind lại dịch vụ trợ năng (đã bật nhưng chưa kết nối)");
        } catch (Exception e) { }
    }

    // Goi o moi diem vao (boot, man hinh bat, alarm, job, mo app): dam bao moi thu dang chay
    static void recover(Context c, String why) {
        if (!"tv".equals(role(c))) return;
        WakeJob.schedule(c);
        KeepService.scheduleWatchdog(c);
        enableService(c);
        ensureService(c);
        KeepService.start(c);
        selfGrant(c);
    }

    // Mo TvActivity o che do thu nho (tu quay ve nen). Chong mo lien tuc bang cooldown 60 giay.
    static synchronized void autoLaunch(Context c, boolean force) {
        if (!"tv".equals(role(c))) return;
        if (TvActivity.visible) return;
        long now = SystemClock.elapsedRealtime();
        long last = sp(c).getLong("launchAt", -1);
        long diff = now - last;
        if (!force && last >= 0 && diff >= 0 && diff < LAUNCH_COOLDOWN) return;
        sp(c).edit().putLong("launchAt", now).apply();
        final long t0 = now;
        try {
            Intent i = new Intent(c, TvActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.putExtra("minimize", true);
            c.startActivity(i);
            log(c, "Tự mở app: đã gọi startActivity");
        } catch (Exception e) {
            log(c, "Tự mở app: LỖI " + e.getClass().getSimpleName());
        }
        // Neu 5 giay sau app van chua len (HyperOS chan mo tu nen) -> dung ADB noi bo de mo
        final Context app = c.getApplicationContext();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (TvActivity.createdAt >= t0 || !sp(app).getBoolean("adbOk", false)) return;
            new Thread(() -> {
                try {
                    AdbLocal.exec(app.getFilesDir(), 5555, "am start -n " + app.getPackageName() + "/.MainActivity --ez minimize true");
                    log(app, "Tự mở app: dùng ADB nội bộ");
                } catch (Exception e) { log(app, "Tự mở app bằng ADB lỗi"); }
            }, "tvguard-adbstart").start();
        }, 5000);
    }

    // Tu cap lai cac quyen chay nen / tu khoi dong qua ADB noi bo (chi khi da bam "Luon cho phep" 1 lan).
    // Lenh nao khong co tren ROM thi bo qua, khong anh huong lenh khac.
    static void selfGrant(Context c) {
        if (!sp(c).getBoolean("adbOk", false)) return;
        // moi 12 gio 1 lan (luu ben vung): doi quyen co the khien HyperOS khoi dong lai tien trinh
        long wall = System.currentTimeMillis();
        long lastW = sp(c).getLong("grantWall", 0);
        if (lastW != 0 && wall >= lastW && wall - lastW < 12 * 3600 * 1000L) return;
        sp(c).edit().putLong("grantWall", wall).apply();
        final Context app = c.getApplicationContext();
        final String p = app.getPackageName();
        final String cmd = "pm grant " + p + " " + WSS + ";"
                + "appops set " + p + " SYSTEM_ALERT_WINDOW allow;"
                + "cmd appops set " + p + " RUN_IN_BACKGROUND allow;"
                + "cmd appops set " + p + " RUN_ANY_IN_BACKGROUND allow;"
                + "cmd appops set " + p + " AUTO_START allow;"
                + "cmd appops set " + p + " 10008 allow;"
                + "cmd appops set " + p + " 10021 allow;"
                + "cmd deviceidle whitelist +" + p + ";"
                + "am set-standby-bucket " + p + " active;"
                + "echo done";
        new Thread(() -> {
            try {
                AdbLocal.exec(app.getFilesDir(), 5555, cmd);
                log(app, "Tự cấp quyền chạy nền (ADB nội bộ): xong");
            } catch (Exception e) {
                log(app, "Tự cấp quyền (ADB nội bộ) lỗi: " + AdbLocal.explain(e));
            }
        }, "tvguard-grant").start();
    }

    // ---------- an app ----------
    // An app khoi da nhiem (Recent apps) de tre khong vuot xoa duoc
    static void hideFromRecents(Activity a, boolean hide) {
        try {
            ActivityManager am = (ActivityManager) a.getSystemService(Context.ACTIVITY_SERVICE);
            for (ActivityManager.AppTask t : am.getAppTasks()) t.setExcludeFromRecents(hide);
        } catch (Exception e) { }
    }

    private static ComponentName launcher(Context c) {
        return new ComponentName(c.getPackageName(), c.getPackageName() + ".Launcher");
    }

    // An/hien icon app tren man hinh chinh cua TV (app + dich vu van chay)
    static void setIconHidden(Context c, boolean hide) {
        try {
            c.getPackageManager().setComponentEnabledSetting(launcher(c),
                    hide ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED : PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
        } catch (Exception e) { }
    }

    static boolean iconHidden(Context c) {
        try {
            return c.getPackageManager().getComponentEnabledSetting(launcher(c))
                    == PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        } catch (Exception e) {
            return false;
        }
    }
}
