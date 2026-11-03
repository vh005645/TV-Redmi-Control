package com.tvguard.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class TvActivity extends Activity {
    private static final String WSS = "android.permission.WRITE_SECURE_SETTINGS";
    private final Handler h = new Handler(Looper.getMainLooper());
    private TextView info;
    private EditText urlEt;
    private boolean alive;

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!alive) return;
            if (!enabled()) autoEnable();
            info.setText(build());
            h.postDelayed(this, 2000);
        }
    };

    private String component() {
        return getPackageName() + "/" + TvService.class.getName();
    }

    private boolean enabled() {
        String s = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(getPackageName() + "/");
    }

    private boolean canWriteSecure() {
        return checkSelfPermission(WSS) == PackageManager.PERMISSION_GRANTED;
    }

    // Neu da cap quyen WRITE_SECURE_SETTINGS qua ADB (1 lan) thi app tu bat dich vu Tro nang
    private boolean autoEnable() {
        if (!canWriteSecure()) return false;
        try {
            String comp = component();
            String cur = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (cur == null) cur = "";
            if (!cur.contains(comp)) {
                String nv = cur.isEmpty() ? comp : cur + ":" + comp;
                Settings.Secure.putString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, nv);
            }
            Settings.Secure.putInt(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String build() {
        StringBuilder b = new StringBuilder();
        if (!Cfg.urlOk(this)) {
            b.append("!! CHƯA CÓ URL FIREBASE !!\n")
                    .append("Nhập URL vào ô bên dưới rồi bấm Lưu URL (hoặc sửa db_url trong strings.xml rồi build lại).\n\n");
        }
        b.append("Dịch vụ trợ năng: ").append(enabled() ? "ĐÃ BẬT" : "CHƯA BẬT").append("\n");
        b.append("Kết nối: ").append(TvService.connected ? TvService.lastMsg : "dịch vụ chưa chạy").append("\n");
        if (!enabled()) {
            b.append("\nCách bật nhanh nhất - chạy ADB trên máy tính (1 lần, sau đó mở lại app là tự bật):\n");
            b.append("adb shell pm grant ").append(getPackageName()).append(" ").append(WSS).append("\n");
            b.append("\nHoặc bật thẳng bằng ADB:\n");
            b.append("adb shell settings put secure enabled_accessibility_services ").append(component()).append("\n");
            b.append("adb shell settings put secure accessibility_enabled 1\n");
        }
        return b.toString();
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        ScrollView sv = new ScrollView(this);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(64, 48, 64, 48);
        sv.addView(l);

        TextView t = new TextView(this);
        t.setText("TV Guard - chế độ TV\nNhập mã này vào app chủ trên điện thoại:");
        t.setTextSize(22);
        l.addView(t);

        TextView code = new TextView(this);
        code.setText(Cfg.tvCode(this));
        code.setTextSize(48);
        code.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        l.addView(code);

        info = new TextView(this);
        info.setTextSize(18);
        l.addView(info);

        TextView ul = new TextView(this);
        ul.setText("\nURL Firebase (để trống nếu đã sửa trong strings.xml):");
        ul.setTextSize(16);
        l.addView(ul);

        urlEt = new EditText(this);
        urlEt.setSingleLine(true);
        urlEt.setTextSize(16);
        urlEt.setHint("https://xxxx-default-rtdb.asia-southeast1.firebasedatabase.app");
        urlEt.setText(Cfg.savedDbUrl(this));
        l.addView(urlEt);

        Button save = new Button(this);
        save.setText("Lưu URL");
        save.setAllCaps(false);
        save.setOnClickListener(v -> {
            Cfg.setDbUrl(this, urlEt.getText().toString());
            urlEt.setText(Cfg.savedDbUrl(this));
            info.setText(build());
        });
        l.addView(save);

        Button acc = new Button(this);
        acc.setText("Mở cài đặt Trợ năng");
        acc.setAllCaps(false);
        acc.setOnClickListener(v -> {
            if (autoEnable()) { info.setText(build()); return; }
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Exception e) {
                info.setText("TV không có màn hình Trợ năng, hãy dùng ADB (xem hướng dẫn bên dưới)\n\n" + build());
            }
        });
        l.addView(acc);

        Button sw = new Button(this);
        sw.setText("Đổi chế độ");
        sw.setAllCaps(false);
        sw.setOnClickListener(v -> {
            Cfg.setRole(this, "");
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        l.addView(sw);

        setContentView(sv);
        acc.requestFocus();
    }

    @Override protected void onResume() { super.onResume(); alive = true; h.post(refresh); }
    @Override protected void onPause() { super.onPause(); alive = false; }
}
