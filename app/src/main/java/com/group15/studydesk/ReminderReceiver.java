package com.group15.studydesk;

import android.app.*;
import android.content.*;
import androidx.core.app.NotificationCompat;

public class ReminderReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context c, Intent i) {
    Repository repo = new Repository(c);
    String owner = i.getStringExtra("owner");
    if (owner != null && !owner.equals(repo.owner())) return;
    if ("mark_done".equals(i.getAction())) {
      PendingResult result = goAsync();
      Repository.IO.execute(
          () -> {
            try {
              Row r = repo.find("assignment", i.getStringExtra("row"));
              if (r != null && r.live() && !r.b("done")) {
                r.put("done", true);
                repo.save(r);
                repo.changed();
              }
              c.getSystemService(NotificationManager.class)
                  .cancel(i.getIntExtra("notificationId", 0));
            } finally {
              result.finish();
            }
          });
      return;
    }
    if (!Reminders.allowed(c)) return;
    boolean timer = "timer".equals(i.getAction());
    int id = timer ? 999 : i.getIntExtra("notificationId", 1000);
    Intent open =
        timer
            ? new Intent(c, MainActivity.class).putExtra("screen", "Timer")
            : new Intent(c, MainActivity.class).putExtra("screen", i.getStringExtra("screen"));
    PendingIntent tap =
        PendingIntent.getActivity(
            c, id, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    NotificationCompat.Builder n =
        new NotificationCompat.Builder(c, "deadlines")
            .setSmallIcon(R.drawable.ic_book)
            .setContentTitle(timer ? "Study block complete" : i.getStringExtra("title"))
            .setContentText(
                timer
                    ? "Open StudyDesk to save your session or continue."
                    : i.getStringExtra("body"))
            .setContentIntent(tap)
            .setAutoCancel(true);
    if ("assignment".equals(i.getStringExtra("kind"))) {
      Intent done =
          new Intent(c, ReminderReceiver.class)
              .setAction("mark_done")
              .putExtra("row", i.getStringExtra("row"))
              .putExtra("owner", owner)
              .putExtra("notificationId", id);
      n.addAction(
          0,
          "Mark done",
          PendingIntent.getBroadcast(
              c, id, done, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
    }
    c.getSystemService(NotificationManager.class).notify(id, n.build());
    if (timer) c.getSystemService(NotificationManager.class).cancel(998);
  }
}
