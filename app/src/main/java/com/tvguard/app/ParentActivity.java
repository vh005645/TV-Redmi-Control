package com.tvguard.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;

public class ParentActivity extends Activity {
    static final int PICK_LOCK = 1, PICK_SEND = 2;
    private final Handler h = new Handler(Looper.getMainLooper());
    private EditText codeEt, minEt, secEt, urlEt;
    private TextView statusTv, msgTv;
    private ImageView shotIv;
    private RadioGroup modeRg;
    private boolean alive;
    private long lastTs = -1, lastChange = 0;

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private Button btn(String t, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private TextView label(String t) {
        TextView x = new TextView(this);
        x.setText(t);
        x.setTextSize(16);
        x.setPadding(0, dp(12), 0, dp(4));
        return x;
    }

    private LinearLayout row(View... vs) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        for (View v : vs) r.addView(v, new LinearLayout.LayoutParams(0, -2, 1f));
        return r;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(24), dp(16), dp(24));
        sv.addView(root);

        TextView title = label("Điều khiển TV");
        title.setTextSize(22);
        root.addView(title);
        root.addView(label("URL Firebase Realtime Database"));
        urlEt = new EditText(this);
        urlEt.setHint("https://xxxx-default-rtdb.asia-southeast1.firebasedatabase.app");
        urlEt.setSingleLine(true);
        urlEt.setTextSize(14);
        urlEt.setText(Cfg.urlOk(this) ? Cfg.dbUrl(this) : "");
        root.addView(urlEt);

        codeEt = new EditText(this);
        codeEt.setHint("Mã TV (xem trên màn hình TV)");
        codeEt.setSingleLine(true);
        codeEt.setText(Cfg.parentCode(this));
        root.addView(codeEt);
        root.addView(btn("Lưu URL & mã", v -> {
            String bs = baseForSend();
            msg(bs == null ? noBase() : "Đã lưu URL và mã");
        }));

        statusTv = label("TV: chưa có dữ liệu");
        root.addView(statusTv);

        root.addView(label("Hẹn giờ xem TV"));
        minEt = new EditText(this);
        minEt.setInputType(InputType.TYPE_CLASS_NUMBER);
        minEt.setHint("Số phút");
        minEt.setText("60");
        root.addView(minEt);

        root.addView(label("Khi hết giờ:"));
        modeRg = new RadioGroup(this);
        String[] names = {"Màn hình đen", "Khung ảnh (ảnh đã chọn)", "Tắt màn hình (nếu TV hỗ trợ)"};
        for (int i = 0; i < names.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(names[i]);
            rb.setId(100 + i);
            modeRg.addView(rb);
        }
        modeRg.check(100);
        root.addView(modeRg);

        root.addView(row(btn("Đặt giờ", v -> timer("settimer")), btn("Cộng thêm", v -> timer("addtime"))));
        root.addView(row(btn("Khóa ngay", v -> cmd("lock", "mode", selectedMode())),
                btn("Mở khóa", v -> cmd("unlock"))));
        root.addView(btn("Chọn ảnh làm khung khóa", v -> pick(PICK_LOCK)));

        root.addView(label("Chụp màn hình TV"));
        root.addView(btn("Chụp ngay", v -> shot()));
        shotIv = new ImageView(this);
        shotIv.setAdjustViewBounds(true);
        root.addView(shotIv, new LinearLayout.LayoutParams(-1, -2));

        root.addView(label("Gửi ảnh lên màn hình TV"));
        secEt = new EditText(this);
        secEt.setInputType(InputType.TYPE_CLASS_NUMBER);
        secEt.setHint("Hiển thị bao nhiêu giây (0 = đến khi ẩn)");
        secEt.setText("30");
        root.addView(secEt);
        root.addView(row(btn("Chọn ảnh & gửi", v -> pick(PICK_SEND)), btn("Ẩn ảnh trên TV", v -> cmd("hideimg"))));

        msgTv = label("");
        root.addView(msgTv);
        root.addView(btn("Đổi chế độ", v -> {
            Cfg.setRole(this, "");
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }));

        setContentView(sv);
    }

    private String base() {
        String c = codeEt.getText().toString().trim().toLowerCase();
        if (c.isEmpty()) return null;
        Cfg.setParentCode(this, c);
        if (!Cfg.urlOk(this)) return null;
        return Cfg.dbUrl(this) + "/tv/" + c;
    }

    // Luu URL dang nhap trong o (neu co) roi tra ve duong dan goc cua TV
    private String baseForSend() {
        String u = urlEt.getText().toString().trim();
        if (!u.isEmpty()) Cfg.setDbUrl(this, u);
        return base();
    }

    private String noBase() {
        if (!Cfg.urlOk(this)) return "Chưa nhập URL Firebase (ô phía trên)";
        return "Chưa nhập mã TV";
    }

    private void msg(final String s) { runOnUiThread(() -> msgTv.setText(s)); }

    private String selectedMode() {
        int id = modeRg.getCheckedRadioButtonId();
        return id == 101 ? "image" : id == 102 ? "sleep" : "black";
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private void timer(String type) {
        cmd(type, "minutes", parseInt(minEt.getText().toString(), 0), "mode", selectedMode());
    }

    private void cmd(final String type, final Object... kv) {
        final String base = baseForSend();
        if (base == null) { msg(noBase()); return; }
        final long id = System.currentTimeMillis();
        new Thread(() -> {
            try {
                JSONObject c = new JSONObject();
                c.put("id", id);
                c.put("type", type);
                for (int i = 0; i + 1 < kv.length; i += 2) c.put((String) kv[i], kv[i + 1]);
                Net.put(base + "/cmd.json", c.toString());
                msg("Đã gửi lệnh: " + type);
            } catch (Exception e) {
                msg("Lỗi: " + e.getMessage());
            }
        }).start();
    }

    private void shot() {
        final String base = baseForSend();
        if (base == null) { msg(noBase()); return; }
        msg("Đang chụp màn hình TV...");
        new Thread(() -> {
            try {
                long id = System.currentTimeMillis();
                JSONObject c = new JSONObject();
                c.put("id", id);
                c.put("type", "shot");
                Net.put(base + "/cmd.json", c.toString());
                long t0 = System.currentTimeMillis();
                while (System.currentTimeMillis() - t0 < 40000) {
                    Thread.sleep(2000);
                    String s = Net.get(base + "/shotid.json").trim();
                    if (s.equals(String.valueOf(id))) {
                        JSONObject o = Net.getObj(base + "/shot.json");
                        if (o != null && o.has("data")) {
                            final Bitmap bm = ImgUtil.fromB64(o.getString("data"));
                            runOnUiThread(() -> { shotIv.setImageBitmap(bm); msgTv.setText("Đã nhận ảnh"); });
                        } else {
                            msg("TV không chụp được (mã lỗi " + (o == null ? "?" : String.valueOf(o.optInt("err"))) + ")");
                        }
                        return;
                    }
                }
                msg("TV không phản hồi (offline?)");
            } catch (Exception e) {
                msg("Lỗi: " + e.getMessage());
            }
        }).start();
    }

    private void pick(int req) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        startActivityForResult(Intent.createChooser(i, "Chọn ảnh"), req);
    }

    @Override
    protected void onActivityResult(final int req, int res, Intent d) {
        super.onActivityResult(req, res, d);
        if (res != RESULT_OK || d == null || d.getData() == null) return;
        final Uri u = d.getData();
        final String base = baseForSend();
        final int sec = parseInt(secEt.getText().toString(), 30);
        if (base == null) { msg(noBase()); return; }
        msg("Đang tải ảnh lên...");
        new Thread(() -> {
            try {
                byte[] data = ImgUtil.readAll(getContentResolver().openInputStream(u));
                Bitmap bm = ImgUtil.decode(data, 1280);
                if (bm == null) { msg("Không đọc được ảnh"); return; }
                long id = System.currentTimeMillis();
                JSONObject o = new JSONObject();
                o.put("id", id);
                o.put("data", ImgUtil.toB64(bm, 1280, 70));
                JSONObject c = new JSONObject();
                c.put("id", id + 1);
                if (req == PICK_LOCK) {
                    Net.put(base + "/lockimg.json", o.toString());
                    c.put("type", "lockimg");
                } else {
                    Net.put(base + "/img.json", o.toString());
                    c.put("type", "showimg");
                    c.put("seconds", sec);
                }
                Net.put(base + "/cmd.json", c.toString());
                msg(req == PICK_LOCK ? "Đã gửi ảnh khung khóa (chọn 'Khung ảnh' khi hết giờ)" : "Đã gửi ảnh lên TV");
            } catch (Exception e) {
                msg("Lỗi: " + e.getMessage());
            }
        }).start();
    }

    private String describe(JSONObject s) {
        long now = System.currentTimeMillis();
        if (s == null) return "TV: chưa có dữ liệu (kiểm tra mã / bật dịch vụ trên TV)";
        long ts = s.optLong("ts");
        if (ts != lastTs) {
            lastChange = (lastTs == -1 && Math.abs(now - ts) > 300000) ? 0 : now;
            lastTs = ts;
        }
        boolean online = now - lastChange < 25000;
        StringBuilder b = new StringBuilder(online ? "TV: ONLINE" : "TV: OFFLINE");
        if (s.optBoolean("locked")) b.append("\nĐang KHÓA (hết giờ)");
        else if (s.optBoolean("timer")) {
            long r = s.optLong("remain");
            b.append(String.format("\nCòn lại: %d phút %02d giây", r / 60, r % 60));
        } else b.append("\nKhông hẹn giờ");
        if (s.optBoolean("img")) b.append("\nĐang hiển thị ảnh của bạn");
        return b.toString();
    }

    private final Runnable poller = new Runnable() {
        @Override public void run() {
            if (!alive) return;
            final String base = base();
            if (base != null) {
                new Thread(() -> {
                    String t;
                    try {
                        t = describe(Net.getObj(base + "/status.json"));
                    } catch (Exception e) {
                        t = "TV: lỗi - " + Net.explain(e);
                    }
                    final String tt = t;
                    runOnUiThread(() -> statusTv.setText(tt));
                }).start();
            } else if (!Cfg.urlOk(ParentActivity.this)) {
                statusTv.setText("TV: " + noBase());
            }
            h.postDelayed(this, 4000);
        }
    };

    @Override protected void onResume() { super.onResume(); alive = true; h.post(poller); }
    @Override protected void onPause() { super.onPause(); alive = false; }
}
