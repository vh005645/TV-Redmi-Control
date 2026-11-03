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
        String r = Cfg.role(this);
        if ("tv".equals(r)) { startActivity(new Intent(this, TvActivity.class)); finish(); return; }
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
