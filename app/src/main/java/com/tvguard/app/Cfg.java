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

    // Can da cap quyen WRITE_SECURE_SETTINGS qua ADB (1 lan)
    static boolean enableService(Context c) {
        if (!canWriteSecure(c)) return false;
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

    // ---------- tu chay khi bat TV ----------
    private static boolean launched;

    // Mo TvActivity o che do thu nho (tu quay ve nen). force=true: bo qua gioi han "vua khoi dong"
    static synchronized void autoLaunch(Context c, boolean force) {
        if (!"tv".equals(role(c))) return;
        if (!force && (launched || SystemClock.elapsedRealtime() > 10 * 60 * 1000L)) return;
        launched = true;
        try {
            Intent i = new Intent(c, TvActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.putExtra("minimize", true);
            c.startActivity(i);
        } catch (Exception e) { }
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
