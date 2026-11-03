package com.tvguard.app;

import android.content.Context;
import android.content.SharedPreferences;
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

    static synchronized String tvCode(Context c) {
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

    static String parentCode(Context c) { return sp(c).getString("pcode", ""); }
    static void setParentCode(Context c, String s) { sp(c).edit().putString("pcode", s).apply(); }
}
