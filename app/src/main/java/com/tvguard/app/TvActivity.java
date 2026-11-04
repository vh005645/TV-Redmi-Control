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
    private TextView info, urlTv, codeTv;
    private EditText urlEt, codeEt;
    private Button saveBtn, codeSaveBtn, iconBtn;
    private ScrollView sv;
    private boolean alive;
    private volatile boolean checking;

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
        long end = Cfg.sp(this).getLong("endAt", 0);
        if (Cfg.sp(this).getBoolean("locked", false)) b.append("Hẹn giờ: đang KHÓA\n");
        else if (end > System.currentTimeMillis())
            b.append("Hẹn giờ: sẽ khóa/tắt TV lúc ").append(new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date(end))).append("\n");
        else b.append("Hẹn giờ: không giới hạn thời gian xem TV\n");
        b.append("Tắt nguồn TV (ADB nội bộ): ").append(TvService.powerMsg.isEmpty() ? "chưa kiểm tra - bấm nút \"Kiểm tra quyền tắt nguồn\"" : TvService.powerMsg).append("\n");
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
        b.append("\nNhật ký khởi động (mới nhất ở cuối):\n").append(Cfg.bootLog(this)).append("\n");
        b.append("\nMở lại app khi icon bị ẩn:\nadb shell am start -n ").append(getPackageName()).append("/.MainActivity\n");
        return b.toString();
    }

    private void refreshUrl() {
        urlTv.setText("URL Firebase: " + (Cfg.urlOk(this) ? Cfg.dbUrl(this) : "(chưa có)"));
    }

    private void refreshCode() {
        codeTv.setText("Mã TV: " + Cfg.tvCode(this));
    }

    private void refreshIconBtn() {
        iconBtn.setText(Cfg.iconHidden(this) ? "Hiện lại biểu tượng app" : "Ẩn biểu tượng app khỏi màn hình chính");
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        Cfg.hideFromRecents(this, true);
        Cfg.log(this, "Mở app" + (getIntent() != null && getIntent().getBooleanExtra("minimize", false) ? " (tự động)" : " (thủ công)")
                + ": trợ năng " + (Cfg.serviceEnabled(this) ? "đang bật" : "ĐANG TẮT")
                + ", dịch vụ " + (TvService.connected ? "đã kết nối" : "CHƯA kết nối"));
        WakeJob.schedule(this);
        KeepService.start(this);
        sv = new ScrollView(this);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(64, 48, 64, 48);
        sv.addView(l);

        TextView t = new TextView(this);
        t.setText("TV Guard - chế độ TV");
        t.setTextSize(22);
        l.addView(t);

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

        codeTv = new TextView(this);
        codeTv.setTextSize(15);
        codeTv.setPadding(0, 24, 0, 8);
        l.addView(codeTv);
        refreshCode();

        Button chgCode = new Button(this);
        chgCode.setText("Thay đổi mã TV");
        chgCode.setAllCaps(false);
        l.addView(chgCode);

        codeEt = new EditText(this);
        codeEt.setSingleLine(true);
        codeEt.setTextSize(16);
        codeEt.setHint("Mã TV mới (a-z, 0-9; để trống = dùng mã mặc định)");
        codeEt.setText(Cfg.savedTvCode(this));
        codeEt.setVisibility(View.GONE);
        l.addView(codeEt);

        codeSaveBtn = new Button(this);
        codeSaveBtn.setText("Lưu mã TV");
        codeSaveBtn.setAllCaps(false);
        codeSaveBtn.setVisibility(View.GONE);
        codeSaveBtn.setOnClickListener(v -> {
            if (!Cfg.setTvCode(this, codeEt.getText().toString())) {
                info.setText("Mã TV không hợp lệ: chỉ gồm chữ a-z và số 0-9, dài 4-40 ký tự\n\n" + build());
                return;
            }
            codeEt.setText(Cfg.savedTvCode(this));
            codeEt.setVisibility(View.GONE);
            codeSaveBtn.setVisibility(View.GONE);
            refreshCode();
            info.setText(build());
        });
        l.addView(codeSaveBtn);

        chgCode.setOnClickListener(v -> {
            boolean show = codeEt.getVisibility() != View.VISIBLE;
            codeEt.setVisibility(show ? View.VISIBLE : View.GONE);
            codeSaveBtn.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) codeEt.requestFocus();
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

        Button pw = new Button(this);
        pw.setText("Kiểm tra quyền tắt nguồn (ADB nội bộ)");
        pw.setAllCaps(false);
        pw.setOnClickListener(v -> checkPower());
        l.addView(pw);

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
        handleLaunch(getIntent());
    }

    // Kiem tra ket noi ADB noi bo (hien hop thoai xin quyen tren TV lan dau - bam "Luon cho phep")
    private void checkPower() {
        if (checking) return;
        checking = true;
        TvService.powerMsg = "Đang kết nối ADB... nếu TV hiện hộp thoại, hãy chọn \"Luôn cho phép\" rồi bấm OK";
        info.setText(build());
        new Thread(() -> {
            try {
                AdbLocal.exec(getFilesDir(), 5555, "echo tvguard_ok");
                TvService.powerMsg = "SẴN SÀNG - app có quyền tắt nguồn TV [" + AdbLocal.lastKeyInfo + "]";
            } catch (Exception e) {
                TvService.powerMsg = "Chưa được: " + AdbLocal.explain(e) + " [" + AdbLocal.lastKeyInfo + "]";
            } finally {
                checking = false;
            }
            runOnUiThread(() -> info.setText(build()));
        }, "tvguard-adbcheck").start();
    }

    // Duoc mo luc bat TV (extra "minimize"): giu giao dien vo hinh roi tu thu nho ve nen
    private void handleLaunch(Intent it) {
        if (it == null || !it.getBooleanExtra("minimize", false)) return;
        it.removeExtra("minimize");
        sv.setAlpha(0f);
        h.postDelayed(() -> {
            moveTaskToBack(true);
            sv.setAlpha(1f);
        }, 1200);
    }

    @Override
    protected void onNewIntent(Intent it) {
        super.onNewIntent(it);
        setIntent(it);
        handleLaunch(it);
    }

    // Phim Back chi thu nho app, khong thoat han (thoat han co the lam mat dich vu tren MIUI)
    @Override public void onBackPressed() { moveTaskToBack(true); }

    @Override protected void onResume() { super.onResume(); alive = true; h.post(refresh); }
    @Override protected void onPause() { super.onPause(); alive = false; }
}
