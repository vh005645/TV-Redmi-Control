package com.tvguard.app;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

// Job duoc he thong JobScheduler giu lai sau khi khoi dong lai may. Neu HyperOS khong gui BOOT_COMPLETED,
// job nay (dinh ky 15 phut, chay ngam, khong hien gi len man hinh) van danh thuc app.
public class WakeJob extends JobService {
    private static final int JOB_PERIODIC = 4711;
    private static final int JOB_CHAIN = 4712;

    static void schedule(Context c) {
        try {
            JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js == null) return;
            if (js.getPendingJob(JOB_PERIODIC) == null) {
                js.schedule(new JobInfo.Builder(JOB_PERIODIC, new ComponentName(c, WakeJob.class))
                        .setPersisted(true)
                        .setPeriodic(15 * 60 * 1000L)
                        .build());
            }
            js.cancel(JOB_CHAIN);      // job chuoi 1 phut cua ban cu (da bo)
        } catch (Exception e) { }
    }

    @Override
    public boolean onStartJob(JobParameters p) {
        try {
            if ("tv".equals(Cfg.role(this))) {
                if (!TvService.connected) Cfg.log(this, "Job nền chạy: dịch vụ trợ năng CHƯA kết nối");
                Cfg.recover(this, "job");
            }
        } catch (Exception e) { }
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters p) { return false; }
}
