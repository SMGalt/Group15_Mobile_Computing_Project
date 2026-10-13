package com.group15.studydesk;

import android.content.*;

public class RestoreReceiver extends BroadcastReceiver {
  public void onReceive(Context c, Intent i) {
    if (!java.util.Arrays.asList(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED)
        .contains(i.getAction())) return;
    PendingResult result = goAsync();
    Repository.IO.execute(
        () -> {
          try {
            Repository repo = new Repository(c);
            Reminders.rebuild(c, repo.all());
            Reminders.timer(c);
            DeskWidget.refresh(c);
            CloudSync.schedule(c);
          } finally {
            result.finish();
          }
        });
  }
}
