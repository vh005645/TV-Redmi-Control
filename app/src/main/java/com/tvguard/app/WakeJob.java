package com.tvguard.app;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

// Job dinh ky (giu lai sau khi khoi dong lai may, do he thong JobScheduler quan ly): neu TV khong gui
// broadcast BOOT_COMPLETED cho app (HyperOS chan tu khoi dong), job nay van danh thuc app trong vai phut.
public class WakeJob extends JobService {
    private static final int JOB_ID = 4711;

    static void schedule(Context c) {
        try {
            JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js == null || js.getPendingJob(JOB_ID) != null) return;
            JobInfo ji = new JobInfo.Builder(JOB_ID, new ComponentName(c, WakeJob.class))
                    .setPersisted(true)
                    .setPeriodic(15 * 60 * 1000L)
                    .build();
            js.schedule(ji);
        } catch (Exception e) { }
    }

    @Override
    public boolean onStartJob(JobParameters p) {
        try {
            if ("tv".equals(Cfg.role(this))) {
                Cfg.log(this, "Job nền chạy (dịch vụ " + (TvService.connected ? "đang chạy" : "CHƯA chạy") + ")");
                Cfg.enableService(this);
                KeepService.start(this);
                Cfg.autoLaunch(this, false);
            }
        } catch (Exception e) { }
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters p) { return false; }
}
