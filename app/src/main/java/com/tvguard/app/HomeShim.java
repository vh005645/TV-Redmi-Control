package com.tvguard.app;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import java.util.List;

// Man hinh "Home" trung gian, trong suot. Dat lam launcher mac dinh thi he thong TU KHOI DONG no moi lan bat TV
// va moi lan bam nut Home (khong bi chan nhu tu khoi dong thuong). Viec no lam:
//   1) chay phan khoi phuc cua TV Guard (bat Tro nang, bo canh, ...)
//   2) mo ngay launcher that (App Drawer), neu loi thi mo launcher khac (khong bao gio de TV khong co man hinh chinh)
public class HomeShim extends Activity {
    // Launcher that se duoc mo tiep theo (App Drawer cua ban). Sua neu doi launcher.
    static final String REAL_HOME = "tungbui.drawer/.MainActivity";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            if ("tv".equals(Cfg.role(this))) Cfg.recover(this, "home");
        } catch (Throwable t) { }
        try {
            if (!launchRealHome()) Cfg.log(this, "HomeShim: khong mo duoc launcher that nao");
        } catch (Throwable t) { }
        finish();
        overridePendingTransition(0, 0);
    }

    private boolean launchRealHome() {
        // 1) launcher da cau hinh
        try {
            ComponentName cn = ComponentName.unflattenFromString(REAL_HOME);
            if (cn != null && !getPackageName().equals(cn.getPackageName())) {
                Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(cn)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return true;
            }
        } catch (Throwable t) {
            Cfg.log(this, "HomeShim: launcher cau hinh loi (" + t.getClass().getSimpleName() + "), thu launcher khac");
        }
        // 2) bat ky launcher nao khac TV Guard
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            List<ResolveInfo> l = getPackageManager().queryIntentActivities(home, PackageManager.MATCH_ALL);
            for (ResolveInfo ri : l) {
                if (ri.activityInfo == null || getPackageName().equals(ri.activityInfo.packageName)) continue;
                try {
                    Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                            .setComponent(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                    Cfg.log(this, "HomeShim: da mo launcher du phong " + ri.activityInfo.packageName);
                    return true;
                } catch (Throwable t) { }
            }
        } catch (Throwable t) { }
        return false;
    }
}
