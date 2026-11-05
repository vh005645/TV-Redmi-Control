package com.tvguard.app;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import java.util.List;

// Launcher phu, TRONG SUOT HOAN TOAN (Theme.NoDisplay, xem ShimTheme). Dat lam launcher mac dinh thi he thong tu mo no
// moi lan bat TV va moi lan bam Home. Viec no lam trong vai tram mili giay roi tu dong:
//   1) goi TV Guard day: chay phan khoi phuc (bat Tro nang, bo canh, ...)
//   2) mo launcher that (App Drawer). KHONG dung category HOME khi chuyen tiep (HyperOS coi do la "khong co ung dung nao
//      thuc hien duoc" va hien hop thoai 没有应用可执行此操作); chi dung intent tuong minh toi dung thanh phan.
public class HomeShim extends Activity {
    // Launcher that se duoc mo tiep theo (App Drawer cua ban). Sua neu doi launcher.
    static final String REAL_HOME = "tungbui.drawer/.MainActivity";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            Intent in = getIntent();
            Cfg.log(this, "HomeShim chạy (role=" + Cfg.role(this) + ", ghi được cài đặt hệ thống=" + Cfg.canWriteSecure(this)
                    + ", Trợ năng trong danh sách=" + Cfg.serviceEnabled(this) + ", đang kết nối=" + TvService.connected + ")");
            if ("tv".equals(Cfg.role(this))) Cfg.recover(this, "home");
        } catch (Throwable t) {
            try { Cfg.log(this, "HomeShim lỗi: " + t); } catch (Throwable t2) { }
        }
        try {
            if (!launchRealHome()) Cfg.log(this, "HomeShim: không mở được launcher thật nào");
        } catch (Throwable t) { }
        finish();   // bat buoc truoc khi onResume voi Theme.NoDisplay
        overridePendingTransition(0, 0);
    }

    private boolean launchRealHome() {
        // 1) launcher da cau hinh
        ComponentName cn = ComponentName.unflattenFromString(REAL_HOME);
        if (cn != null && !getPackageName().equals(cn.getPackageName()) && tryStart(cn)) return true;
        // 2) bat ky launcher nao khac TV Guard
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            List<ResolveInfo> l = getPackageManager().queryIntentActivities(home, PackageManager.MATCH_ALL);
            for (ResolveInfo ri : l) {
                if (ri.activityInfo == null || getPackageName().equals(ri.activityInfo.packageName)) continue;
                if (tryStart(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name))) return true;
            }
        } catch (Throwable t) { }
        return false;
    }

    // Chi mo khi thanh phan TON TAI va phan giai duoc; intent tuong minh, KHONG category HOME.
    private boolean tryStart(ComponentName cn) {
        try {
            PackageManager pm = getPackageManager();
            pm.getActivityInfo(cn, 0);                       // NameNotFoundException neu khong co
            Intent i = new Intent(Intent.ACTION_MAIN).setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (i.resolveActivity(pm) == null) {
                Cfg.log(this, "HomeShim: không phân giải được " + cn.flattenToShortString());
                return false;
            }
            startActivity(i);
            Cfg.log(this, "HomeShim: đã mở " + cn.flattenToShortString());
            return true;
        } catch (Throwable t) {
            Cfg.log(this, "HomeShim: mở " + cn.flattenToShortString() + " lỗi: " + t.getClass().getSimpleName());
            return false;
        }
    }
}
