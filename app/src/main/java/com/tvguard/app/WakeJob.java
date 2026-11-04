package com.tvguard.app;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

// Job duoc he thong JobScheduler giu lai sau khi khoi dong lai may. Neu HyperOS khong gui BOOT_COMPLETED,
// job nay van danh thuc app: (1) job dinh ky 15 phut, (2) job 1 lan "chuoi" tu dat lai sau ~1 phut moi lan chay,
// co rang buoc mang nen chay ngay khi TV vua co mang sau khi bat.
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
            if (js.getPendingJob(JOB_CHAIN) == null) scheduleChain(c, js);
        } catch (Exception e) { }
    }

    private static void scheduleChain(Context c, JobScheduler js) {
        try {
            js.schedule(new JobInfo.Builder(JOB_CHAIN, new ComponentName(c, WakeJob.class))
                    .setPersisted(true)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setMinimumLatency(60 * 1000L)
                    .setOverrideDeadline(3 * 60 * 1000L)
                    .build());
        } catch (Exception e) { }
    }

    @Override
    public boolean onStartJob(JobParameters p) {
        try {
            if ("tv".equals(Cfg.role(this))) {
                Cfg.log(this, "Job nền chạy (dịch vụ " + (TvService.connected ? "đang chạy" : "CHƯA chạy") + ")");
                Cfg.recover(this, "job");
                // Tien trinh vua duoc job danh thuc ngay sau khi bat may (<10 phut) -> mo app thu nho
                if (android.os.SystemClock.elapsedRealtime() < 10 * 60 * 1000L) Cfg.autoLaunch(this, false);
            }
            if (p.getJobId() == JOB_CHAIN) {
                JobScheduler js = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
                if (js != null) scheduleChain(this, js);   // tu dat lai de chuoi khong dut
            }
        } catch (Exception e) { }
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters p) { return false; }
}
