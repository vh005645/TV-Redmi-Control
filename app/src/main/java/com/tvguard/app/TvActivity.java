package com.tvguard.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class TvActivity extends Activity {
    private final Handler h = new Handler(Looper.getMainLooper());
    private TextView info;
    private boolean alive;

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!alive) return;
            info.setText(build());
            h.postDelayed(this, 2000);
        }
    };

    private boolean enabled() {
        String s = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(getPackageName() + "/");
    }

    private String build() {
        StringBuilder b = new StringBuilder();
        if (!Cfg.urlOk(this)) b.append("!! CHUA SUA db_url trong strings.xml !!\n\n");
        b.append("Dịch vụ trợ năng: ").append(enabled() ? "ĐÃ BẬT" : "CHƯA BẬT").append("\n");
        b.append("Kết nối: ").append(TvService.connected ? TvService.lastMsg : "dịch vụ chưa chạy").append("\n\n");
        b.append("Nếu TV không có menu Trợ năng, chạy ADB trên máy tính:\n");
        b.append("adb shell settings put secure enabled_accessibility_services ")
                .append(getPackageName()).append("/").append(TvService.class.getName()).append("\n");
        b.append("adb shell settings put secure accessibility_enabled 1\n");
        return b.toString();
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
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

        Button acc = new Button(this);
        acc.setText("Mở cài đặt Trợ năng");
        acc.setAllCaps(false);
        acc.setOnClickListener(v -> {
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
