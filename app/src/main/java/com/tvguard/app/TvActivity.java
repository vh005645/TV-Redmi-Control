package com.tvguard.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class TvActivity extends Activity {
    private final Handler h = new Handler(Looper.getMainLooper());
    private TextView info, urlTv;
    private EditText urlEt;
    private Button saveBtn, iconBtn;
    private boolean alive;

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!alive) return;
            if (!Cfg.serviceEnabled(TvActivity.this)) Cfg.enableService(TvActivity.this);
            info.setText(build());
            h.postDelayed(this, 2000);
        }
    };

    private String build() {
        StringBuilder b = new StringBuilder();
        if (!Cfg.urlOk(this)) b.append("!! CHƯA CÓ URL FIREBASE - bấm \"Thay đổi URL\" để nhập !!\n\n");
        boolean en = Cfg.serviceEnabled(this);
        b.append("Dịch vụ trợ năng: ").append(en ? "ĐÃ BẬT" : "CHƯA BẬT").append("\n");
        b.append("Kết nối: ").append(TvService.connected ? TvService.lastMsg : "dịch vụ chưa chạy").append("\n");
        if (!en) {
            b.append("\nBật nhanh nhất - chạy ADB trên máy tính (1 lần, sau đó app tự bật mỗi khi mở/khởi động TV):\n");
            b.append("adb shell pm grant ").append(getPackageName()).append(" ").append(Cfg.WSS).append("\n");
            b.append("\nHoặc bật thẳng bằng ADB:\n");
            b.append("adb shell settings put secure enabled_accessibility_services ").append(Cfg.serviceComponent(this)).append("\n");
            b.append("adb shell settings put secure accessibility_enabled 1\n");
        } else if (!Cfg.canWriteSecure(this)) {
            b.append("\nLưu ý: chưa cấp quyền tự bật lại dịch vụ. Chạy 1 lần trên máy tính:\n");
            b.append("adb shell pm grant ").append(getPackageName()).append(" ").append(Cfg.WSS).append("\n");
        }
        b.append("\nMở lại app khi icon bị ẩn:\nadb shell am start -n ").append(getPackageName()).append("/.MainActivity\n");
        return b.toString();
    }

    private void refreshUrl() {
        urlTv.setText("URL Firebase: " + (Cfg.urlOk(this) ? Cfg.dbUrl(this) : "(chưa có)"));
    }

    private void refreshIconBtn() {
        iconBtn.setText(Cfg.iconHidden(this) ? "Hiện lại biểu tượng app" : "Ẩn biểu tượng app khỏi màn hình chính");
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        Cfg.hideFromRecents(this, true);
        KeepService.start(this);
        ScrollView sv = new ScrollView(this);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(64, 48, 64, 48);
        sv.addView(l);

        TextView t = new TextView(this);
        t.setText("TV Guard - chế độ TV\nMã TV (cố định, app điện thoại đã tự điền sẵn):");
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

        urlTv = new TextView(this);
        urlTv.setTextSize(15);
        urlTv.setPadding(0, 24, 0, 8);
        l.addView(urlTv);
        refreshUrl();

        Button chg = new Button(this);
        chg.setText("Thay đổi URL");
        chg.setAllCaps(false);
        l.addView(chg);

        urlEt = new EditText(this);
        urlEt.setSingleLine(true);
        urlEt.setTextSize(16);
        urlEt.setHint("URL mới (để trống = dùng URL mặc định)");
        urlEt.setText(Cfg.savedDbUrl(this));
        urlEt.setVisibility(View.GONE);
        l.addView(urlEt);

        saveBtn = new Button(this);
        saveBtn.setText("Lưu URL");
        saveBtn.setAllCaps(false);
        saveBtn.setVisibility(View.GONE);
        saveBtn.setOnClickListener(v -> {
            Cfg.setDbUrl(this, urlEt.getText().toString());
            urlEt.setText(Cfg.savedDbUrl(this));
            urlEt.setVisibility(View.GONE);
            saveBtn.setVisibility(View.GONE);
            refreshUrl();
            info.setText(build());
        });
        l.addView(saveBtn);

        chg.setOnClickListener(v -> {
            boolean show = urlEt.getVisibility() != View.VISIBLE;
            urlEt.setVisibility(show ? View.VISIBLE : View.GONE);
            saveBtn.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) urlEt.requestFocus();
        });

        Button acc = new Button(this);
        acc.setText("Mở cài đặt Trợ năng");
        acc.setAllCaps(false);
        acc.setOnClickListener(v -> {
            if (Cfg.enableService(this)) { info.setText(build()); return; }
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Exception e) {
                info.setText("TV không có màn hình Trợ năng, hãy dùng ADB (xem hướng dẫn bên dưới)\n\n" + build());
            }
        });
        l.addView(acc);

        iconBtn = new Button(this);
        iconBtn.setAllCaps(false);
        iconBtn.setOnClickListener(v -> {
            Cfg.setIconHidden(this, !Cfg.iconHidden(this));
            refreshIconBtn();
        });
        l.addView(iconBtn);
        refreshIconBtn();

        // KHONG dung finish/finishAndRemoveTask: he thong coi nhu vuot xoa da nhiem va dung luon dich vu
        Button close = new Button(this);
        close.setText("Thu nhỏ app (dịch vụ vẫn chạy nền)");
        close.setAllCaps(false);
        close.setOnClickListener(v -> moveTaskToBack(true));
        l.addView(close);

        Button sw = new Button(this);
        sw.setText("Đổi chế độ");
        sw.setAllCaps(false);
        sw.setOnClickListener(v -> {
            Cfg.setRole(this, "");
            Cfg.hideFromRecents(this, false);
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        l.addView(sw);

        setContentView(sv);
        acc.requestFocus();
    }

    // Phim Back chi thu nho app, khong thoat han (thoat han co the lam mat dich vu tren MIUI)
    @Override public void onBackPressed() { moveTaskToBack(true); }

    @Override protected void onResume() { super.onResume(); alive = true; h.post(refresh); }
    @Override protected void onPause() { super.onPause(); alive = false; }
}
