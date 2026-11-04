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
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class ParentActivity extends Activity {
    static final int PICK_LOCK = 1, PICK_SEND = 2;
    private final Handler h = new Handler(Looper.getMainLooper());
    private EditText codeEt, minEt, secEt, urlEt;
    private TextView urlTv, codeTv;
    private Button urlSaveBtn, codeSaveBtn;
    private TextView statusTv, msgTv;
    private ImageView shotIv;
    private RadioGroup modeRg;
    private boolean alive;
    private long lastTs = -1, lastChange = 0;
    private volatile long lastEnd = 0;   // gio TV se khoa (epoch ms) theo trang thai TV gui ve

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private Button btn(String t, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        if (l != null) b.setOnClickListener(l);
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
        Cfg.hideFromRecents(this, false);
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(24), dp(16), dp(24));
        sv.addView(root);

        TextView title = label("Điều khiển TV");
        title.setTextSize(22);
        root.addView(title);
        urlTv = label("");
        urlTv.setTextSize(13);
        root.addView(urlTv);
        refreshUrl();
        final Button chg = btn("Thay đổi URL", null);
        root.addView(chg);
        urlEt = new EditText(this);
        urlEt.setHint("URL mới (để trống = dùng URL mặc định)");
        urlEt.setSingleLine(true);
        urlEt.setTextSize(14);
        urlEt.setText(Cfg.savedDbUrl(this));
        urlEt.setVisibility(View.GONE);
        root.addView(urlEt);
        urlSaveBtn = btn("Lưu URL", v -> {
            Cfg.setDbUrl(this, urlEt.getText().toString());
            urlEt.setText(Cfg.savedDbUrl(this));
            urlEt.setVisibility(View.GONE);
            urlSaveBtn.setVisibility(View.GONE);
            refreshUrl();
            msg("Đã lưu URL");
        });
        urlSaveBtn.setVisibility(View.GONE);
        root.addView(urlSaveBtn);
        chg.setOnClickListener(v -> {
            boolean show = urlEt.getVisibility() != View.VISIBLE;
            urlEt.setVisibility(show ? View.VISIBLE : View.GONE);
            urlSaveBtn.setVisibility(show ? View.VISIBLE : View.GONE);
        });

        codeTv = label("");
        codeTv.setTextSize(13);
        root.addView(codeTv);
        refreshCode();
        final Button chgCode = btn("Thay đổi mã TV", null);
        root.addView(chgCode);
        codeEt = new EditText(this);
        codeEt.setHint("Mã TV mới (a-z, 0-9; để trống = dùng mã mặc định)");
        codeEt.setSingleLine(true);
        codeEt.setText(Cfg.parentCode(this));
        codeEt.setVisibility(View.GONE);
        root.addView(codeEt);
        codeSaveBtn = btn("Lưu mã TV", v -> {
            String c = codeEt.getText().toString().trim().toLowerCase();
            if (!c.isEmpty() && !Cfg.validCode(c)) { msg("Mã TV không hợp lệ: chỉ gồm a-z, 0-9, dài 4-40 ký tự"); return; }
            Cfg.setParentCode(this, c);
            codeEt.setText(Cfg.parentCode(this));
            codeEt.setVisibility(View.GONE);
            codeSaveBtn.setVisibility(View.GONE);
            refreshCode();
            lastTs = -1;
            msg("Đã lưu mã TV");
        });
        codeSaveBtn.setVisibility(View.GONE);
        root.addView(codeSaveBtn);
        chgCode.setOnClickListener(v -> {
            boolean show = codeEt.getVisibility() != View.VISIBLE;
            codeEt.setVisibility(show ? View.VISIBLE : View.GONE);
            codeSaveBtn.setVisibility(show ? View.VISIBLE : View.GONE);
        });

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
        String[] names = {"Màn hình đen", "Khung ảnh (ảnh đã chọn)", "Tắt nguồn TV (shutdown, như bấm phím Nguồn)"};
        for (int i = 0; i < names.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(names[i]);
            rb.setId(100 + i);
            modeRg.addView(rb);
        }
        modeRg.check(100);
        root.addView(modeRg);

        root.addView(row(btn("Đặt giờ", v -> timer("settimer")), btn("Cộng thêm", v -> timer("addtime"))));
        root.addView(btn("Hủy hẹn giờ (không giới hạn thời gian)", v -> cmd("canceltimer")));
        root.addView(row(btn("Khóa ngay", v -> cmd("lock", "mode", selectedMode())),
                btn("Mở khóa", v -> cmd("unlock"))));
        root.addView(btn("Chọn ảnh làm khung khóa", v -> pick(PICK_LOCK)));
        root.addView(label("Khi đang KHÓA, điều khiển TV bị vô hiệu hóa (chỉ còn nút Nguồn). Chế độ \"Tắt nguồn TV\" cần cấp quyền 1 lần trên TV (nút \"Kiểm tra quyền tắt nguồn\")."));

        root.addView(label("Bảo vệ TV"));
        root.addView(row(btn("Mở Cài đặt TV 10 phút", v -> cmd("allowsettings", "minutes", 10)),
                btn("Chặn Cài đặt TV", v -> cmd("allowsettings", "minutes", 0))));
        root.addView(row(btn("Ẩn icon app TV", v -> cmd("hideicon")),
                btn("Hiện icon app TV", v -> cmd("showicon"))));

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
        String c = Cfg.parentCode(this);
        if (c.isEmpty()) return null;
        if (!Cfg.urlOk(this)) return null;
        return Cfg.dbUrl(this) + "/tv/" + c;
    }

    private String baseForSend() {
        return base();
    }

    private void refreshCode() {
        codeTv.setText("Mã TV: " + Cfg.parentCode(this));
    }

    private void refreshUrl() {
        urlTv.setText("URL Firebase: " + (Cfg.urlOk(this) ? Cfg.dbUrl(this) : "(chưa có)"));
    }

    private String noBase() {
        if (!Cfg.urlOk(this)) return "Chưa có URL Firebase (bấm Thay đổi URL)";
        return "Chưa nhập mã TV";
    }

    private void msg(final String s) { runOnUiThread(() -> msgTv.setText(s)); }

    private String selectedMode() {
        int id = modeRg.getCheckedRadioButtonId();
        return id == 101 ? "image" : id == 102 ? "shutdown" : "black";
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    // "10:50" (them ngay/thang neu khong phai hom nay)
    private String fmtTime(long ms) {
        Calendar a = Calendar.getInstance(), b = Calendar.getInstance();
        b.setTimeInMillis(ms);
        boolean same = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
        String t = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ms));
        return same ? t : t + " ngày " + new SimpleDateFormat("dd/MM", Locale.getDefault()).format(new Date(ms));
    }

    private String actionText(String mode) {
        if ("shutdown".equals(mode) || "sleep".equals(mode)) return "tắt nguồn TV";
        if ("image".equals(mode)) return "khóa TV (khung ảnh)";
        return "khóa TV (màn hình đen)";
    }

    private void timer(String type) {
        final int min = parseInt(minEt.getText().toString(), 0);
        if (min <= 0) { msg("Nhập số phút lớn hơn 0"); return; }
        final String mode = selectedMode();
        long now = System.currentTimeMillis();
        boolean add = "addtime".equals(type);
        long start = add && lastEnd > now ? lastEnd : now;
        long when = start + min * 60000L;
        String ok = (add ? "Đã cộng thêm " + min + " phút: " : "Đã đặt giờ " + min + " phút: ")
                + fmtTime(when) + " sẽ " + actionText(mode);
        send(type, ok, "minutes", min, "mode", mode);
    }

    private void cmd(final String type, final Object... kv) {
        String ok = "Đã gửi lệnh: " + type;
        if ("canceltimer".equals(type)) ok = "Đã hủy hẹn giờ - không giới hạn thời gian xem TV";
        send(type, ok, kv);
    }

    private void send(final String type, final String okMsg, final Object... kv) {
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
                msg(okMsg);
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
        String mode = s.optString("mode", "black");
        lastEnd = s.optLong("endAt", 0);
        if (s.optBoolean("locked")) {
            b.append("\nĐang KHÓA (hết giờ / khóa ngay)");
            if ("shutdown".equals(mode) || "sleep".equals(mode)) {
                b.append("\nChế độ: tắt nguồn TV");
                String pw = s.optString("pw", "");
                if (!pw.isEmpty()) b.append("\nTắt nguồn: ").append(pw);
            }
        } else if (s.optBoolean("timer")) {
            long r = s.optLong("remain");
            if (lastEnd > 0) b.append("\nĐã hẹn: ").append(fmtTime(lastEnd)).append(" sẽ ").append(actionText(mode));
            b.append(String.format("\nCòn lại: %d phút %02d giây", r / 60, r % 60));
        } else b.append("\nKhông giới hạn thời gian xem TV");
        if (s.optBoolean("img")) b.append("\nĐang hiển thị ảnh của bạn");
        if (s.optBoolean("allow")) b.append("\nCài đặt TV đang được MỞ (tạm thời)");
        if (s.optBoolean("hidden")) b.append("\nIcon app trên TV: đang ẩn");
        String k = s.optString("key", "");
        if (!k.isEmpty()) b.append("\nPhím cuối bị chặn khi khóa: ").append(k);
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
