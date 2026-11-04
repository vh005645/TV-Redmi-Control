package com.tvguard.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        // Cho phep dat URL qua ADB: adb shell am start -n com.tvguard.app/.MainActivity --es db_url "https://..."
        String du = getIntent() == null ? null : getIntent().getStringExtra("db_url");
        if (du != null && !du.trim().isEmpty()) Cfg.setDbUrl(this, du);
        // Cuu ho qua ADB khi TV dang khoa / mat mang:
        //   --ez unlock true      mo khoa ngay
        //   --ei allow_min 10     cho phep mo Cai dat 10 phut (0 = chan lai)
        Intent it = getIntent();
        if (it != null) {
            if (it.getBooleanExtra("unlock", false))
                Cfg.sp(this).edit().putBoolean("locked", false).putLong("endAt", 0).apply();
            if (it.hasExtra("allow_min")) {
                int m = it.getIntExtra("allow_min", 0);
                Cfg.sp(this).edit().putLong("allowUntil", m > 0 ? System.currentTimeMillis() + m * 60000L : 0).apply();
            }
        }
        String r = Cfg.role(this);
        if ("tv".equals(r)) { Cfg.hideFromRecents(this, true); startActivity(new Intent(this, TvActivity.class)); finish(); return; }
        if ("parent".equals(r)) { startActivity(new Intent(this, ParentActivity.class)); finish(); return; }

        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.setPadding(48, 48, 48, 48);

        TextView t = new TextView(this);
        t.setText("TV Guard\nChọn chế độ cho thiết bị này:");
        t.setTextSize(24);
        t.setGravity(Gravity.CENTER);
        l.addView(t);

        Button tv = new Button(this);
        tv.setText("Đây là TV (cần kiểm soát)");
        tv.setAllCaps(false);
        tv.setOnClickListener(v -> {
            Cfg.setRole(this, "tv");
            Cfg.hideFromRecents(this, true);
            startActivity(new Intent(this, TvActivity.class));
            finish();
        });
        l.addView(tv);

        Button p = new Button(this);
        p.setText("Đây là điện thoại phụ huynh (app chủ)");
        p.setAllCaps(false);
        p.setOnClickListener(v -> {
            Cfg.setRole(this, "parent");
            startActivity(new Intent(this, ParentActivity.class));
            finish();
        });
        l.addView(p);

        setContentView(l);
        tv.requestFocus();
    }
}
