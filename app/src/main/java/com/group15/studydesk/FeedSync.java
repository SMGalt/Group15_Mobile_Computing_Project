package com.group15.studydesk;

import android.app.job.*;
import android.content.*;

public class FeedSync extends JobService {
  public static void schedule(Context c, boolean exists) {
    JobScheduler scheduler = c.getSystemService(JobScheduler.class);
    if (!exists) {
      scheduler.cancel(1518);
      return;
    }
    if (scheduler.getPendingJob(1518) == null)
      scheduler.schedule(
          new JobInfo.Builder(1518, new ComponentName(c, FeedSync.class))
              .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
              .setPeriodic(6 * 60 * 60 * 1000L)
              .setPersisted(true)
              .build());
  }

  public boolean onStartJob(JobParameters p) {
    CloudSync.NETWORK.execute(
        () -> {
          try {
            CalendarFeed.refreshDue(this, new Repository(this));
            new Repository(this).changed();
          } finally {
            jobFinished(p, false);
          }
        });
    return true;
  }

  public boolean onStopJob(JobParameters p) {
    return true;
  }
}
